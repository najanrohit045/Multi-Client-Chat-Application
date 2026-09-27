package database;

import java.sql.*;
import util.Config;

public final class DBConnection {
  private DBConnection() {}

  public static Connection getConnection() throws SQLException {
    return DriverManager.getConnection(
        Config.get("DB_URL", "jdbc:mysql://localhost:3306/chat_app_v2?connectionTimeZone=UTC"),
        Config.get("DB_USERNAME", "chat_user"),
        Config.get("DB_PASSWORD", ""));
  }
}
