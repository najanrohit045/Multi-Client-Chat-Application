package network;

import com.google.gson.*;
import java.util.UUID;

public record Packet(String id, String type, JsonObject data) {
  public static final Gson JSON = new Gson();

  public static Packet request(String type, Object data) {
    return new Packet(UUID.randomUUID().toString(), type, object(data));
  }

  public static Packet event(String type, Object data) {
    return new Packet(null, type, object(data));
  }

  public static JsonObject object(Object data) {
    return JSON.toJsonTree(data).getAsJsonObject();
  }

  public String text(String key) {
    return data.has(key) && !data.get(key).isJsonNull() ? data.get(key).getAsString() : "";
  }

  public long number(String key) {
    return data.has(key) ? data.get(key).getAsLong() : 0;
  }

  public boolean flag(String key) {
    return data.has(key) && data.get(key).getAsBoolean();
  }
}
