package io.github.zopulus.coogle;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.zip.CRC32;
public final class SignalModelAdapterTest {
    public static void main(String[] args) throws Exception {
        byte[] original = Files.readAllBytes(Path.of(args[0]));
        byte[] baseline = original.clone();
        byte[] expected = Files.readAllBytes(Path.of(args[1]));
        byte[] actual = SignalModelAdapter.adapt(original);
        CRC32 crc = new CRC32();
        crc.update(actual, 0, actual.length - 4);
        long trailer = Integer.toUnsignedLong(ByteBuffer.wrap(actual).order(ByteOrder.LITTLE_ENDIAN).getInt(actual.length - 4));
        if (crc.getValue() != trailer) throw new AssertionError("DSP CRC32 trailer mismatch");
        if (!Arrays.equals(expected, actual)) throw new AssertionError("Offline reference differs");
        if (!Arrays.equals(original, baseline)) throw new AssertionError("Source was modified");
        try { SignalModelAdapter.adapt(new byte[16]); throw new AssertionError("Unknown accepted"); }
        catch (IllegalArgumentException expectedFailure) { }
        System.out.println("Signal model adapter: reference, source preservation, rejection passed");
    }
}
