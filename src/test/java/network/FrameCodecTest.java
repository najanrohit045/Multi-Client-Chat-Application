package network;

import static org.junit.jupiter.api.Assertions.*;

import java.io.*;
import java.util.*;
import org.junit.jupiter.api.Test;

class FrameCodecTest {
  @Test
  void unicodeAndBinaryRoundTrip() throws Exception {
    var out = new ByteArrayOutputStream();
    var packet = Packet.request("UPLOAD", Map.of("text", "hello: 😊\nनमस्कार"));
    byte[] bytes = {0, 1, -1, 10};
    FrameCodec.write(out, new FrameCodec.Frame(packet, bytes));
    var read = FrameCodec.read(new ByteArrayInputStream(out.toByteArray()));
    assertEquals(packet.text("text"), read.packet().text("text"));
    assertArrayEquals(bytes, read.binary());
  }

  @Test
  void oversizedHeaderRejected() throws Exception {
    var out = new ByteArrayOutputStream();
    new DataOutputStream(out).writeInt(Integer.MAX_VALUE);
    assertThrows(
        IOException.class, () -> FrameCodec.read(new ByteArrayInputStream(out.toByteArray())));
  }

  @Test
  void truncatedFrameRejected() throws Exception {
    assertThrows(
        IOException.class,
        () -> FrameCodec.read(new ByteArrayInputStream(new byte[] {0, 0, 0, 4, 1})));
  }
}
