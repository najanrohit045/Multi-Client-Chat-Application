package controller;

import com.google.gson.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.animation.*;
import javafx.application.Platform;
import javafx.collections.*;
import javafx.geometry.*;
import javafx.scene.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.util.Duration;
import model.*;
import network.*;
import service.FileService;
import util.Config;

public final class ChatController implements AutoCloseable {
  private final Stage stage;
  private final ExecutorService background = Executors.newFixedThreadPool(2);
  private ChatClient client;
  private User me;
  private BorderPane root;
  private Label notice = new Label();
  private ListView<Conversation> conversations;
  private final ObservableList<Conversation> all = FXCollections.observableArrayList();
  private final Map<String, Message> previews = new HashMap<>();
  private final Map<String, Message> visible = new LinkedHashMap<>();
  private final Set<String> acknowledged = new HashSet<>();
  private VBox bubbles;
  private ScrollPane scroll;
  private Label title, presence, typing;
  private TextField compose, search, filter;
  private Conversation current;
  private final PauseTransition typingStop = new PauseTransition(Duration.seconds(1.4));
  private final PauseTransition typingExpiry = new PauseTransition(Duration.seconds(3));
  private long lastTyping;
  private boolean dark = true;
  private int generation;

  private record Conversation(
      long id, boolean group, String name, boolean online, String lastSeen) {
    String key() {
      return (group ? "g" : "u") + id;
    }
  }

  public ChatController(Stage stage) {
    this.stage = stage;
  }

  public void start() {
    stage
        .focusedProperty()
        .addListener(
            (o, a, b) -> {
              if (b && me != null) visible.values().forEach(m -> ack(m, true));
            });
    stage.setTitle("Relay · Multi-Client Chat");
    stage.setMinWidth(900);
    stage.setMinHeight(650);
    login();
    stage.show();
  }

  private Button button(String text, Runnable action) {
    Button b = new Button(text);
    b.setOnAction(e -> action.run());
    return b;
  }

  private TextField field(String hint) {
    TextField f = new TextField();
    f.setPromptText(hint);
    return f;
  }

  private void scene(Parent content) {
    Scene scene = new Scene(content, 1120, 760);
    scene.getStylesheets().add(getClass().getResource("/css/chat.css").toExternalForm());
    stage.setScene(scene);
    theme();
  }

  private void theme() {
    Parent p = stage.getScene().getRoot();
    p.getStyleClass().remove("light");
    if (!dark) p.getStyleClass().add("light");
  }

  private void error(Throwable error) {
    Throwable e = error;
    while (e.getCause() != null) e = e.getCause();
    notice.setText(e.getMessage() == null ? "Operation failed. Please retry." : e.getMessage());
  }

  private <T> void async(Callable<T> work, Consumer<T> success) {
    CompletableFuture.supplyAsync(
            () -> {
              try {
                return work.call();
              } catch (Exception e) {
                throw new CompletionException(e);
              }
            },
            background)
        .whenComplete(
            (r, e) ->
                Platform.runLater(
                    () -> {
                      if (e != null) error(e);
                      else success.accept(r);
                    }));
  }

  private void request(String type, Object data, Consumer<JsonObject> success) {
    if (client == null) return;
    client
        .request(type, data)
        .whenComplete(
            (f, e) ->
                Platform.runLater(
                    () -> {
                      if (e != null) error(e);
                      else success.accept(f.packet().data());
                    }));
  }

  private void login() {
    generation++;
    current = null;
    me = null;
    visible.clear();
    all.clear();
    previews.clear();
    acknowledged.clear();
    if (client != null) {
      client.close();
      client = null;
    }
    Label brand = new Label("◈  RELAY");
    brand.getStyleClass().add("brand");
    Label heading = new Label("A place for every conversation.");
    heading.getStyleClass().add("hero");
    Label subtitle = new Label("Private messages. Shared ideas. Connected teams.");
    subtitle.getStyleClass().add("muted");
    TextField username = field("Username");
    username.setId("loginUsername");
    PasswordField password = new PasswordField();
    password.setPromptText("Password · minimum 8 characters");
    password.setId("loginPassword");
    notice = new Label("Connect to your chat server to get started.");
    notice.setWrapText(true);
    notice.getStyleClass().add("muted");
    Button signIn = new Button("Sign in"), register = new Button("Create account");
    signIn.setId("loginButton");
    Consumer<Boolean> auth =
        registration -> {
          String name = username.getText(), pass = password.getText();
          signIn.setDisable(true);
          register.setDisable(true);
          notice.setText("Connecting…");
          async(
              () -> {
                if (client == null || client.isClosed())
                  client =
                      new ChatClient(
                          Config.get("CHAT_HOST", "127.0.0.1"),
                          Config.number("CHAT_PORT", 9090),
                          p -> Platform.runLater(() -> event(p)));
                return client;
              },
              c -> {
                c.request(
                        registration ? "REGISTER" : "LOGIN",
                        Map.of("username", name, "password", pass))
                    .whenComplete(
                        (f, e) ->
                            Platform.runLater(
                                () -> {
                                  signIn.setDisable(false);
                                  register.setDisable(false);
                                  password.clear();
                                  if (e != null) {
                                    error(e);
                                    return;
                                  }
                                  if (registration)
                                    notice.setText("Account created. Sign in to start chatting.");
                                  else {
                                    me =
                                        Packet.JSON.fromJson(
                                            f.packet().data().get("user"), User.class);
                                    dashboard();
                                  }
                                }));
              });
          // Restore actions even if opening the socket fails.
          PauseTransition restore = new PauseTransition(Duration.seconds(6));
          restore.setOnFinished(
              e -> {
                signIn.setDisable(false);
                register.setDisable(false);
              });
          restore.play();
        };
    signIn.setOnAction(e -> auth.accept(false));
    register.setOnAction(e -> auth.accept(true));
    password.setOnAction(e -> auth.accept(false));
    VBox card =
        new VBox(
            18,
            brand,
            heading,
            subtitle,
            username,
            password,
            new HBox(12, signIn, register),
            notice);
    card.getStyleClass().add("auth-card");
    card.setMaxWidth(510);
    StackPane wrap = new StackPane(card);
    wrap.getStyleClass().add("auth-root");
    scene(wrap);
  }

  private void dashboard() {
    root = new BorderPane();
    root.getStyleClass().add("dashboard");
    Label logo = new Label("◈  RELAY");
    logo.getStyleClass().add("brand");
    Label profile = new Label("@" + me.username() + "  •  " + me.role());
    profile.getStyleClass().add("muted");
    filter = field("Search people and groups");
    filter.textProperty().addListener((o, a, b) -> filterList());
    conversations = new ListView<>();
    conversations.getStyleClass().add("conversations");
    conversations.setCellFactory(
        v ->
            new ListCell<>() {
              @Override
              protected void updateItem(Conversation c, boolean empty) {
                super.updateItem(c, empty);
                if (empty || c == null) {
                  setGraphic(null);
                  return;
                }
                Label name = new Label((c.group() ? "#  " : c.online() ? "●  " : "○  ") + c.name());
                name.getStyleClass().add("conversation-name");
                Message last = previews.get(c.key());
                Label preview =
                    new Label(
                        last == null
                            ? (c.group() ? "Group conversation" : "Start a conversation")
                            : last.text());
                preview.setMaxWidth(220);
                preview.getStyleClass().add("muted");
                Label time = new Label(last == null ? "" : time(last.sentAt()));
                time.getStyleClass().add("tiny");
                setGraphic(new VBox(5, name, preview, time));
              }
            });
    conversations
        .getSelectionModel()
        .selectedItemProperty()
        .addListener(
            (o, a, b) -> {
              if (b != null) open(b);
            });
    VBox sidebar =
        new VBox(
            16,
            logo,
            profile,
            filter,
            button("+ New group", this::createGroup),
            conversations,
            new HBox(
                8,
                button("Account", this::account),
                button(
                    "◐",
                    () -> {
                      dark = !dark;
                      theme();
                    }),
                button("Log out", this::login)));
    sidebar.getStyleClass().add("sidebar");
    sidebar.setPrefWidth(300);
    VBox.setVgrow(conversations, Priority.ALWAYS);
    root.setLeft(sidebar);
    title = new Label("Your conversations");
    title.getStyleClass().add("title");
    presence = new Label("Choose a person or a group to begin");
    presence.getStyleClass().add("muted");
    search = field("Search this conversation");
    search.setOnAction(e -> load());
    Button searchButton = button("Search", this::load);
    Button clear =
        button(
            "Clear",
            () -> {
              search.clear();
              load();
            });
    HBox toolbar = new HBox(10, search, searchButton, clear, button("Members", this::members));
    HBox.setHgrow(search, Priority.ALWAYS);
    VBox header = new VBox(6, title, presence, toolbar);
    header.getStyleClass().add("chat-header");
    bubbles = new VBox(12);
    bubbles.getStyleClass().add("messages");
    scroll = new ScrollPane(bubbles);
    scroll.setFitToWidth(true);
    scroll.getStyleClass().add("message-scroll");
    compose = field("Write a message…  😊");
    compose.setId("messageInput");
    compose.setOnAction(e -> send());
    compose.textProperty().addListener((o, a, b) -> typingChanged());
    typing = new Label(" ");
    typing.getStyleClass().add("muted");
    typingExpiry.setOnFinished(e -> typing.setText(" "));
    typingStop.setOnFinished(e -> stopTyping());
    notice = new Label("Connected · messages are saved on the server");
    notice.getStyleClass().add("muted");
    notice.setWrapText(true);
    HBox input =
        new HBox(10, button("+ File", this::upload), compose, button("Send ➤", this::send));
    HBox.setHgrow(compose, Priority.ALWAYS);
    VBox footer = new VBox(7, typing, input, notice);
    footer.getStyleClass().add("composer");
    BorderPane chat = new BorderPane(scroll, header, null, footer, null);
    root.setCenter(chat);
    scene(root);
    refresh();
    pending(0);
  }

  private void pending(long after) {
    request(
        "PENDING",
        Map.of("after", after),
        data -> {
          if (me == null) return;
          var messages = data.getAsJsonArray("messages");
          long last = after;
          for (JsonElement e : messages) {
            Message m = Packet.JSON.fromJson(e, Message.class);
            last = m.id();
            event(Packet.event("MESSAGE", Map.of("message", m)));
          }
          if (messages.size() == 40) pending(last);
        });
  }

  private void filterList() {
    String term = filter.getText().toLowerCase(Locale.ROOT);
    conversations
        .getItems()
        .setAll(
            all.stream().filter(c -> c.name().toLowerCase(Locale.ROOT).contains(term)).toList());
  }

  private void refresh() {
    if (me == null) return;
    long myId = me.id();
    request(
        "SUMMARIES",
        Map.of(),
        d -> {
          if (me == null) return;
          for (JsonElement element : d.getAsJsonArray("messages")) {
            Message m = Packet.JSON.fromJson(element, Message.class);
            previews.put(
                m.group()
                    ? "g" + m.targetId()
                    : "u" + (m.senderId() == myId ? m.targetId() : m.senderId()),
                m);
          }
          conversations.refresh();
        });
    request(
        "USERS",
        Map.of("query", ""),
        d -> {
          if (me == null || me.id() != myId) return;
          List<Conversation> list = new ArrayList<>();
          for (JsonElement e : d.getAsJsonArray("users")) {
            User u = Packet.JSON.fromJson(e, User.class);
            if (u.id() != myId)
              list.add(new Conversation(u.id(), false, u.username(), u.online(), u.lastSeen()));
          }
          request(
              "GROUPS",
              Map.of(),
              g -> {
                if (me == null) return;
                for (JsonElement e : g.getAsJsonArray("groups")) {
                  ChatGroup cg = Packet.JSON.fromJson(e, ChatGroup.class);
                  list.add(new Conversation(cg.id(), true, cg.name(), false, null));
                }
                all.setAll(list);
                filterList();
                if (current != null) {
                  var found = list.stream().filter(c -> c.key().equals(current.key())).findFirst();
                  if (found.isPresent()) {
                    current = found.get();
                    header();
                  } else {
                    current = null;
                    visible.clear();
                    render();
                    title.setText("Choose a conversation");
                  }
                }
              });
        });
  }

  private void header() {
    if (current == null) return;
    title.setText((current.group() ? "# " : "") + current.name());
    presence.setText(
        current.group()
            ? "Group conversation"
            : current.online()
                ? "Online"
                : current.lastSeen() == null ? "Offline" : "Last seen " + time(current.lastSeen()));
  }

  private Map<String, Object> context() {
    return Map.of("target", current.id(), "group", current.group());
  }

  private void open(Conversation c) {
    stopTyping();
    current = c;
    compose.clear();
    search.clear();
    typing.setText(" ");
    header();
    load();
  }

  private void load() {
    if (current == null) return;
    int g = ++generation;
    visible.clear();
    render();
    loadPage(g, current, search.getText(), 0);
  }

  private void loadPage(int generationId, Conversation conversation, String query, long after) {
    request(
        query.isBlank() ? "HISTORY" : "SEARCH",
        Map.of(
            "target",
            conversation.id(),
            "group",
            conversation.group(),
            "query",
            query,
            "after",
            after),
        d -> {
          if (generationId != generation
              || current == null
              || !current.key().equals(conversation.key())) return;
          var arr = d.getAsJsonArray("messages");
          long last = after;
          for (JsonElement e : arr) {
            Message m = Packet.JSON.fromJson(e, Message.class);
            visible.put(key(m), m);
            last = m.id();
            ack(m, true);
          }
          render();
          if (arr.size() == 40) loadPage(generationId, conversation, query, last);
        });
  }

  private String key(Message m) {
    return (m.group() ? "g" : "p") + m.id();
  }

  private boolean belongs(Message m) {
    return current != null
        && m.group() == current.group()
        && (m.group()
            ? m.targetId() == current.id()
            : (m.senderId() == current.id() || m.targetId() == current.id()));
  }

  private void ack(Message m, boolean seen) {
    if (me == null || m.group() || m.targetId() != me.id() || m.status().equals("SEEN")) return;
    String receipt = (seen ? "S" : "D") + m.id();
    if (!acknowledged.add(receipt)) return;
    request(seen ? "SEEN" : "DELIVERED", Map.of("messageId", m.id()), d -> {});
  }

  private void event(Packet p) {
    if (me == null) return;
    switch (p.type()) {
      case "MESSAGE" -> {
        Message m = Packet.JSON.fromJson(p.data().get("message"), Message.class);
        String chatKey =
            m.group()
                ? "g" + m.targetId()
                : "u" + (m.senderId() == me.id() ? m.targetId() : m.senderId());
        previews.put(chatKey, m);
        conversations.refresh();
        ack(m, belongs(m) && stage.isFocused());
        if (belongs(m)
            && (search.getText().isBlank()
                || m.text()
                    .toLowerCase(Locale.ROOT)
                    .contains(search.getText().toLowerCase(Locale.ROOT)))) {
          visible.put(key(m), m);
          typing.setText(" ");
          render();
        }
      }
      case "RECEIPT" -> {
        Message m = Packet.JSON.fromJson(p.data().get("message"), Message.class);
        if (visible.containsKey(key(m))) {
          visible.put(key(m), m);
          render();
        }
      }
      case "PRESENCE", "GROUPS_CHANGED" -> {
        typing.setText(" ");
        refresh();
      }
      case "TYPING", "STOP_TYPING" -> {
        boolean match =
            current != null
                && current.group() == p.flag("group")
                && (current.group()
                    ? current.id() == p.number("target")
                    : current.id() == p.number("userId"));
        if (match) {
          typing.setText(p.type().equals("TYPING") ? p.text("username") + " is typing…" : " ");
          typingExpiry.playFromStart();
        }
      }
      case "DISCONNECTED" -> {
        notice.setText("Connection lost. Sign out and sign in to reconnect.");
        compose.setDisable(true);
      }
      default -> {}
    }
  }

  private void render() {
    if (bubbles == null) return;
    bubbles.getChildren().clear();
    visible.values().stream()
        .sorted(Comparator.comparingLong(Message::id))
        .forEach(
            m -> {
              boolean mine = me != null && m.senderId() == me.id();
              Label text = new Label(m.text());
              text.setWrapText(true);
              text.setMaxWidth(520);
              Label who = new Label(mine ? "You" : m.sender());
              who.getStyleClass().add("tiny");
              Label meta =
                  new Label(time(m.sentAt()) + (mine && !m.group() ? "  ·  " + m.status() : ""));
              meta.getStyleClass().add("tiny");
              VBox bubble = new VBox(6, who, text);
              if (m.attachment() != null) {
                Attachment a = m.attachment();
                bubble
                    .getChildren()
                    .add(
                        button(
                            "↓ Download · " + Math.max(1, a.fileSize() / 1024) + " KB",
                            () -> download(a)));
              }
              bubble.getChildren().add(meta);
              bubble.getStyleClass().add(mine ? "bubble-own" : "bubble");
              HBox row = new HBox(bubble);
              row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
              bubbles.getChildren().add(row);
            });
    Platform.runLater(() -> scroll.setVvalue(1));
  }

  private String time(String value) {
    try {
      return DateTimeFormatter.ofPattern("dd MMM, h:mm a")
          .format(Instant.parse(value).atZone(ZoneId.systemDefault()));
    } catch (Exception e) {
      return "";
    }
  }

  private void send() {
    if (current == null || compose.getText().isBlank()) return;
    String text = compose.getText();
    Map<String, Object> data = new HashMap<>(context());
    data.put("text", text);
    stopTyping();
    request(
        "SEND",
        data,
        d -> {
          if (compose.getText().equals(text)) compose.clear();
          notice.setText("Message sent");
        });
  }

  private void typingChanged() {
    if (current == null) return;
    typingStop.playFromStart();
    if (compose.getText().isBlank()) {
      stopTyping();
      return;
    }
    if (System.currentTimeMillis() - lastTyping > 900) {
      lastTyping = System.currentTimeMillis();
      request("TYPING", context(), d -> {});
    }
  }

  private void stopTyping() {
    typingStop.stop();
    if (current != null && client != null) request("STOP_TYPING", context(), d -> {});
  }

  private void upload() {
    if (current == null) return;
    FileChooser chooser = new FileChooser();
    chooser.setTitle("Share a file · maximum 10 MiB");
    var f = chooser.showOpenDialog(stage);
    if (f == null) return;
    Map<String, Object> data = new HashMap<>(context());
    data.put("name", f.getName());
    notice.setText("Uploading…");
    async(
        () -> {
          long size = Files.size(f.toPath());
          if (size < 1 || size > FileService.MAX_SIZE)
            throw new IllegalArgumentException("Maximum attachment size is 10 MiB.");
          return Files.readAllBytes(f.toPath());
        },
        bytes ->
            client
                .request("UPLOAD", data, bytes)
                .whenComplete(
                    (r, e) ->
                        Platform.runLater(
                            () -> {
                              if (e != null) error(e);
                              else notice.setText("File shared");
                            })));
  }

  private void download(Attachment attachment) {
    FileChooser chooser = new FileChooser();
    chooser.setInitialFileName(attachment.fileName());
    var target = chooser.showSaveDialog(stage);
    if (target == null) return;
    client
        .request("DOWNLOAD", Map.of("attachment", attachment.id()))
        .whenComplete(
            (f, e) -> {
              if (e != null) {
                Platform.runLater(() -> error(e));
                return;
              }
              Platform.runLater(
                  () ->
                      async(
                          () -> {
                            Files.write(target.toPath(), f.binary());
                            return target;
                          },
                          saved -> notice.setText("Saved " + saved.getName())));
            });
  }

  private void createGroup() {
    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.initOwner(stage);
    styleDialog(dialog);
    dialog.setTitle("Create a group");
    TextField name = field("Group name");
    ListView<Conversation> people =
        new ListView<>(
            FXCollections.observableArrayList(all.stream().filter(c -> !c.group()).toList()));
    people.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    people.setCellFactory(
        v ->
            new ListCell<>() {
              protected void updateItem(Conversation c, boolean empty) {
                super.updateItem(c, empty);
                setText(empty || c == null ? null : c.name());
              }
            });
    dialog
        .getDialogPane()
        .setContent(
            new VBox(12, name, new Label("Select members (Ctrl / Shift for multiple)"), people));
    dialog.getDialogPane().getButtonTypes().addAll(ButtonType.OK, ButtonType.CANCEL);
    dialog
        .showAndWait()
        .filter(b -> b == ButtonType.OK)
        .ifPresent(
            b ->
                request(
                    "CREATE_GROUP",
                    Map.of(
                        "name",
                        name.getText(),
                        "members",
                        people.getSelectionModel().getSelectedItems().stream()
                            .map(Conversation::id)
                            .toList()),
                    d -> refresh()));
  }

  private void members() {
    if (current == null || !current.group()) {
      notice.setText("Open a group to manage members.");
      return;
    }
    long groupId = current.id();
    request(
        "MEMBERS",
        Map.of("target", groupId),
        d -> {
          List<Member> list = new ArrayList<>();
          d.getAsJsonArray("members").forEach(e -> list.add(Packet.JSON.fromJson(e, Member.class)));
          Dialog<ButtonType> dialog = new Dialog<>();
          dialog.initOwner(stage);
          styleDialog(dialog);
          dialog.setTitle("Group members");
          ListView<Member> members = new ListView<>(FXCollections.observableArrayList(list));
          members.setCellFactory(
              v ->
                  new ListCell<>() {
                    protected void updateItem(Member m, boolean empty) {
                      super.updateItem(m, empty);
                      setText(empty || m == null ? null : m.username() + " · " + m.role());
                    }
                  });
          ComboBox<String> add = new ComboBox<>();
          all.stream()
              .filter(c -> !c.group() && list.stream().noneMatch(m -> m.id() == c.id()))
              .forEach(c -> add.getItems().add(c.name()));
          add.setPromptText("Choose a user");
          boolean admin =
              list.stream().anyMatch(m -> m.id() == me.id() && m.role().equals("ADMIN"));
          Button addButton =
              button(
                  "Add",
                  () -> {
                    if (add.getValue() == null) return;
                    all.stream()
                        .filter(c -> !c.group() && c.name().equals(add.getValue()))
                        .findFirst()
                        .ifPresent(
                            c ->
                                request(
                                    "ADD_MEMBER",
                                    Map.of("target", groupId, "userId", c.id()),
                                    v -> {
                                      dialog.close();
                                      members();
                                    }));
                  });
          addButton.setDisable(!admin);
          Button remove =
              button(
                  "Remove selected",
                  () -> {
                    Member m = members.getSelectionModel().getSelectedItem();
                    if (m != null)
                      request(
                          "REMOVE_MEMBER",
                          Map.of("target", groupId, "userId", m.id()),
                          v -> {
                            dialog.close();
                            members();
                          });
                  });
          remove.setDisable(!admin);
          Button leave =
              button(
                  "Leave group",
                  () ->
                      request(
                          "LEAVE_GROUP",
                          Map.of("target", groupId),
                          v -> {
                            dialog.close();
                            current = null;
                            visible.clear();
                            render();
                            refresh();
                          }));
          dialog
              .getDialogPane()
              .setContent(
                  new VBox(12, members, new HBox(8, add, addButton), new HBox(8, remove, leave)));
          dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
          dialog.show();
        });
  }

  private void styleDialog(Dialog<?> dialog) {
    dialog
        .getDialogPane()
        .getStylesheets()
        .add(getClass().getResource("/css/chat.css").toExternalForm());
    if (!dark) dialog.getDialogPane().getStyleClass().add("light");
  }

  private void account() {
    Dialog<ButtonType> dialog = new Dialog<>();
    dialog.initOwner(stage);
    styleDialog(dialog);
    dialog.setTitle("Account · " + me.username());
    PasswordField old = new PasswordField(), fresh = new PasswordField();
    old.setPromptText("Current password");
    fresh.setPromptText("New password");
    VBox box =
        new VBox(
            12,
            new Label("@" + me.username() + " · " + me.role()),
            old,
            fresh,
            button(
                "Change password",
                () ->
                    request(
                        "PASSWORD",
                        Map.of("current", old.getText(), "replacement", fresh.getText()),
                        d -> {
                          old.clear();
                          fresh.clear();
                          dialog.close();
                          notice.setText("Password updated");
                        })));
    if (me.role().equals("ADMIN")) {
      box.getChildren()
          .add(
              button(
                  "View audit events",
                  () ->
                      request(
                          "AUDIT",
                          Map.of(),
                          d -> {
                            TextArea text = new TextArea();
                            d.getAsJsonArray("lines")
                                .forEach(e -> text.appendText(e.getAsString() + "\n"));
                            text.setEditable(false);
                            Alert a = new Alert(Alert.AlertType.INFORMATION);
                            a.setTitle("Audit events");
                            styleDialog(a);
                            a.getDialogPane().setContent(text);
                            a.show();
                          })));
      box.getChildren()
          .add(
              button(
                  "Disconnect selected user",
                  () -> {
                    if (current != null && !current.group())
                      request(
                          "KICK",
                          Map.of("target", current.id()),
                          d -> notice.setText("User disconnected"));
                  }));
    }
    dialog.getDialogPane().setContent(box);
    dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);
    dialog.show();
  }

  @Override
  public void close() {
    typingStop.stop();
    typingExpiry.stop();
    if (client != null) client.close();
    background.shutdownNow();
  }
}
