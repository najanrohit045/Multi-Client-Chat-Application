package app;

import controller.ChatController;
import javafx.application.Application;
import javafx.stage.Stage;

public class ChatApplication extends Application {
  private ChatController controller;

  @Override
  public void start(Stage stage) {
    controller = new ChatController(stage);
    controller.start();
  }

  @Override
  public void stop() {
    if (controller != null) controller.close();
  }

  public static void main(String[] args) {
    launch(args);
  }
}
