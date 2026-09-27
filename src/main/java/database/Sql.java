package database;

import java.sql.*;
import java.util.*;

/** Owns JDBC resources. Transaction callers pass the same connection to each DAO. */
public final class Sql {
  private Sql() {}

  public interface Mapper<T> {
    T read(ResultSet r) throws SQLException;
  }

  public interface Work<T> {
    T run(Connection c) throws SQLException;
  }

  public static <T> T transaction(Work<T> work) throws SQLException {
    try (Connection c = DBConnection.getConnection()) {
      c.setAutoCommit(false);
      try {
        T result = work.run(c);
        c.commit();
        return result;
      } catch (SQLException | RuntimeException e) {
        c.rollback();
        throw e;
      }
    }
  }

  private static PreparedStatement prepare(Connection c, String sql, Object... args)
      throws SQLException {
    PreparedStatement p = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
    try {
      for (int i = 0; i < args.length; i++) p.setObject(i + 1, args[i]);
      return p;
    } catch (SQLException e) {
      p.close();
      throw e;
    }
  }

  public static int update(Connection c, String sql, Object... args) throws SQLException {
    try (PreparedStatement p = prepare(c, sql, args)) {
      return p.executeUpdate();
    }
  }

  public static long insert(Connection c, String sql, Object... args) throws SQLException {
    try (PreparedStatement p = prepare(c, sql, args)) {
      p.executeUpdate();
      try (ResultSet r = p.getGeneratedKeys()) {
        if (r.next()) return r.getLong(1);
        throw new SQLException("No generated key");
      }
    }
  }

  public static <T> List<T> query(Connection c, String sql, Mapper<T> mapper, Object... args)
      throws SQLException {
    try (PreparedStatement p = prepare(c, sql, args);
        ResultSet r = p.executeQuery()) {
      List<T> out = new ArrayList<>();
      while (r.next()) out.add(mapper.read(r));
      return out;
    }
  }

  public static String time(ResultSet r, String name) throws SQLException {
    Timestamp t = r.getTimestamp(name);
    return t == null ? null : t.toInstant().toString();
  }
}
