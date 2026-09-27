package integration;

import static org.junit.jupiter.api.Assertions.*;

import controller.ChatController;
import java.awt.image.BufferedImage;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.function.*;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;
import javax.imageio.ImageIO;
import network.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import service.*;
import support.TestDatabase;

@EnabledIfEnvironmentVariable(named = "CHAT_TEST_UI", matches = "true")
class UiSmokeTest {
  private <T> T fx(Callable<T> work) throws Exception {
    FutureTask<T> f = new FutureTask<>(work);
    Platform.runLater(f);
    return f.get(10, TimeUnit.SECONDS);
  }

  private void await(Callable<Boolean> condition) throws Exception {
    long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(15);
    while (System.nanoTime() < end) {
      if (condition.call()) return;
      Thread.sleep(50);
    }
    fail("UI condition timed out");
  }

  @Test
  void loginDashboardSendAndTheme() throws Exception {
    TestDatabase.reset();
    UserService users = new UserService();
    var alice = users.register("alice", "test-password");
    var bob = users.register("bob", "test-password");
    new ChatService()
        .send(bob.id(), alice.id(), false, "Hey Alice! Ready for the project demo? 😊");
    ChatServer server = new ChatServer();
    ExecutorService pool = Executors.newSingleThreadExecutor();
    pool.submit(
        () -> {
          try {
            server.start(0);
          } catch (Exception e) {
            throw new RuntimeException(e);
          }
        });
    await(() -> server.port() > 0);
    System.setProperty("CHAT_PORT", "" + server.port());
    CountDownLatch ready = new CountDownLatch(1);
    Platform.startup(ready::countDown);
    assertTrue(ready.await(10, TimeUnit.SECONDS));
    Platform.setImplicitExit(false);
    Stage stage = fx(() -> new Stage());
    ChatController controller =
        fx(
            () -> {
              var c = new ChatController(stage);
              c.start();
              return c;
            });
    try {
      fx(
          () -> {
            ((TextField) stage.getScene().lookup("#loginUsername")).setText("alice");
            ((PasswordField) stage.getScene().lookup("#loginPassword")).setText("test-password");
            ((Button) stage.getScene().lookup("#loginButton")).fire();
            return null;
          });
      await(() -> fx(() -> stage.getScene().lookup("#messageInput") != null));
      await(
          () ->
              fx(
                  () ->
                      ((ListView<?>) stage.getScene().lookup(".conversations")).getItems().size()
                          == 2));
      fx(
          () -> {
            ((ListView<?>) stage.getScene().lookup(".conversations")).getSelectionModel().select(0);
            return null;
          });
      await(() -> fx(() -> stage.getScene().getRoot().lookupAll(".bubble").size() == 1));
      fx(
          () -> {
            TextField input = (TextField) stage.getScene().lookup("#messageInput");
            input.setText("Absolutely. Private chat, groups and files are ready!");
            input.fireEvent(new javafx.event.ActionEvent());
            return null;
          });
      await(() -> fx(() -> stage.getScene().getRoot().lookupAll(".bubble-own").size() == 1));
      fx(
          () -> {
            stage.getScene().getRoot().applyCss();
            stage.getScene().getRoot().layout();
            WritableImage image = stage.getScene().snapshot(null);
            BufferedImage png =
                new BufferedImage(
                    (int) image.getWidth(), (int) image.getHeight(), BufferedImage.TYPE_INT_ARGB);
            for (int y = 0; y < png.getHeight(); y++)
              for (int x = 0; x < png.getWidth(); x++)
                png.setRGB(x, y, image.getPixelReader().getArgb(x, y));
            ImageIO.write(png, "png", Path.of("target/ui-smoke.png").toFile());
            return null;
          });
      fx(
          () -> {
            stage.getScene().getRoot().lookupAll(".button").stream()
                .filter(n -> n instanceof Button b && b.getText().equals("◐"))
                .map(n -> (Button) n)
                .findFirst()
                .orElseThrow()
                .fire();
            assertTrue(stage.getScene().getRoot().getStyleClass().contains("light"));
            return null;
          });
    } finally {
      fx(
          () -> {
            controller.close();
            stage.close();
            return null;
          });
      Platform.exit();
      server.close();
      pool.shutdownNow();
      pool.awaitTermination(5, TimeUnit.SECONDS);
      System.clearProperty("CHAT_PORT");
    }
  }
}
