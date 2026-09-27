package exception;

/** A safe, user-facing validation or authorization failure. */
public class ChatException extends RuntimeException {
  public ChatException(String message) {
    super(message);
  }
}
