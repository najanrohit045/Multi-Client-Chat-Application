package util;

public final class Config {
  private Config() {}

  public static String get(String key, String fallback) {
    return System.getProperty(key, System.getenv().getOrDefault(key, fallback));
  }

  public static int number(String key, int fallback) {
    return Integer.parseInt(get(key, "" + fallback));
  }
}
