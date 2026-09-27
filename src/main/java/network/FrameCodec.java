package network;

import java.io.*;
import java.nio.charset.StandardCharsets;
import service.FileService;

/** Wire: 4-byte JSON length, UTF-8 JSON, 4-byte binary length, raw binary bytes. */
public final class FrameCodec {
  public record Frame(Packet packet, byte[] binary) {
    public Frame(Packet p) {
      this(p, new byte[0]);
    }
  }

  public static Frame read(InputStream input) throws IOException {
    DataInputStream in = new DataInputStream(input);
    int size = in.readInt();
    if (size < 2 || size > 1024 * 1024) throw new IOException("Invalid header size");
    byte[] header = in.readNBytes(size);
    if (header.length != size) throw new EOFException();
    Packet p;
    try {
      p = Packet.JSON.fromJson(new String(header, StandardCharsets.UTF_8), Packet.class);
      if (p == null || p.type() == null || p.data() == null)
        throw new IOException("Invalid packet");
    } catch (RuntimeException e) {
      throw new IOException("Invalid JSON", e);
    }
    int length = in.readInt();
    if (length < 0 || length > FileService.MAX_SIZE) throw new IOException("Invalid binary size");
    byte[] bytes = in.readNBytes(length);
    if (bytes.length != length) throw new EOFException();
    return new Frame(p, bytes);
  }

  public static void write(OutputStream output, Frame frame) throws IOException {
    byte[] header = Packet.JSON.toJson(frame.packet()).getBytes(StandardCharsets.UTF_8);
    if (header.length > 1024 * 1024 || frame.binary().length > FileService.MAX_SIZE)
      throw new IOException("Frame too large");
    DataOutputStream out = new DataOutputStream(output);
    out.writeInt(header.length);
    out.write(header);
    out.writeInt(frame.binary().length);
    out.write(frame.binary());
    out.flush();
  }
}
