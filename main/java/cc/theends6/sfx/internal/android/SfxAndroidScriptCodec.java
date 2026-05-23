package cc.theends6.sfx.internal.android;

import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.zip.CRC32;

public final class SfxAndroidScriptCodec {
    public static final String PREFIX = "SFXA1:";
    private static final char[] BASE62 = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz".toCharArray();
    private static final int VERSION = 1;
    private static final int MIN_RUN = 4;
    private static final List<List<SfxAndroidInstruction>> MACROS = List.of(
            List.of(SfxAndroidInstruction.DIG_FORWARD, SfxAndroidInstruction.GO_FORWARD),
            List.of(SfxAndroidInstruction.DIG_DOWN, SfxAndroidInstruction.GO_DOWN),
            List.of(SfxAndroidInstruction.DIG_UP, SfxAndroidInstruction.GO_UP),
            List.of(SfxAndroidInstruction.FARM_DOWN, SfxAndroidInstruction.GO_FORWARD),
            List.of(SfxAndroidInstruction.FARM_FORWARD, SfxAndroidInstruction.GO_FORWARD),
            List.of(SfxAndroidInstruction.INTERFACE_ITEMS, SfxAndroidInstruction.INTERFACE_FUEL),
            List.of(SfxAndroidInstruction.TURN_LEFT, SfxAndroidInstruction.TURN_LEFT),
            List.of(SfxAndroidInstruction.WAIT, SfxAndroidInstruction.WAIT)
    );

    private SfxAndroidScriptCodec() {
    }

    public static String exportCode(SfxAndroidType type, List<SfxAndroidInstruction> body, boolean withPrefix) {
        if (type == null) {
            throw new IllegalArgumentException("Android type is required");
        }
        List<SfxAndroidInstruction> canonical = canonicalize(type, body);
        if (canonical.size() > SfxAndroidState.MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("Android script is too long");
        }
        int typeVersionChar = (VERSION << 4) | (type.codecId() & 0x0F);
        String header = "" + encodeDigit(typeVersionChar) + encodeDigit(canonical.size());
        String payload = encodeBase62(packCompressed(type, canonical));
        if (payload.isEmpty()) {
            payload = "0";
        }
        String checksumSource = header + payload;
        String checksum = checksum(checksumSource);
        String result = header + payload + checksum;
        return withPrefix ? PREFIX + result : result;
    }

    public static DecodedScript importCode(String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Script code cannot be blank");
        }
        String code = input.trim();
        if (code.toUpperCase(Locale.ROOT).startsWith(PREFIX)) {
            code = code.substring(PREFIX.length());
        }
        if (code.length() < 5) {
            throw new IllegalArgumentException("Script code is too short");
        }
        for (int i = 0; i < code.length(); i++) {
            if (decodeDigit(code.charAt(i)) < 0) {
                throw new IllegalArgumentException("Script code contains invalid Base62 characters");
            }
        }
        String withoutChecksum = code.substring(0, code.length() - 2);
        String expected = checksum(withoutChecksum);
        String actual = code.substring(code.length() - 2);
        if (!expected.equals(actual)) {
            throw new IllegalArgumentException("Script checksum mismatch");
        }
        int versionAndType = decodeDigit(code.charAt(0));
        int version = versionAndType >> 4;
        if (version != VERSION) {
            throw new IllegalArgumentException("Unsupported Android script format version: " + version);
        }
        SfxAndroidType type = SfxAndroidType.fromCodecId(versionAndType & 0x0F);
        if (type == null) {
            throw new IllegalArgumentException("Unknown Android type in script code");
        }
        int length = decodeDigit(code.charAt(1));
        if (length < 0 || length > SfxAndroidState.MAX_BODY_LENGTH) {
            throw new IllegalArgumentException("Invalid Android script length");
        }
        String payload = code.substring(2, code.length() - 2);
        byte[] packed = decodeBase62(payload);
        List<SfxAndroidInstruction> body = unpackCompressed(type, packed, length);
        if (body.size() != length) {
            throw new IllegalArgumentException("Script payload ended early");
        }
        return new DecodedScript(type, body);
    }

    public static List<SfxAndroidInstruction> canonicalize(SfxAndroidType type, List<SfxAndroidInstruction> body) {
        List<SfxAndroidInstruction> result = new ArrayList<>();
        if (body != null) {
            for (SfxAndroidInstruction instruction : body) {
                if (instruction != null && instruction.validFor(type)) {
                    result.add(instruction);
                    if (result.size() >= SfxAndroidState.MAX_BODY_LENGTH) {
                        break;
                    }
                }
            }
        }
        if (result.isEmpty()) {
            result.add(SfxAndroidInstruction.WAIT);
        }
        return result;
    }

    public static String toReadableScript(List<SfxAndroidInstruction> body) {
        StringBuilder builder = new StringBuilder("START");
        if (body != null) {
            for (SfxAndroidInstruction instruction : body) {
                if (instruction != null) {
                    builder.append('-').append(instruction.name());
                }
            }
        }
        builder.append("-REPEAT");
        return builder.toString();
    }

    public static List<SfxAndroidInstruction> parseReadableScript(SfxAndroidType type, String input) {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("Script is empty");
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace(' ', '_');
        String[] split = normalized.split("-");
        List<SfxAndroidInstruction> body = new ArrayList<>();
        int start = 0;
        int end = split.length;
        if (end > 0 && split[0].equals("START")) {
            start = 1;
        }
        if (end > start && split[end - 1].equals("REPEAT")) {
            end--;
        }
        for (int i = start; i < end; i++) {
            if (split[i].isBlank()) {
                continue;
            }
            SfxAndroidInstruction instruction = SfxAndroidInstruction.valueOf(split[i]);
            if (!instruction.validFor(type)) {
                throw new IllegalArgumentException("Instruction " + instruction.name() + " is not valid for " + type.key());
            }
            body.add(instruction);
        }
        return canonicalize(type, body);
    }

    private static byte[] packCompressed(SfxAndroidType type, List<SfxAndroidInstruction> body) {
        List<SfxAndroidInstruction> table = SfxAndroidInstruction.validForType(type);
        int bitsPerOpcode = bitsFor(table.size() - 1);
        BitWriter writer = new BitWriter();
        int i = 0;
        while (i < body.size()) {
            MacroMatch macro = bestMacro(body, i);
            if (macro != null && macro.count >= 2) {
                writer.write(2, 2);
                writer.write(macro.id, 4);
                writer.write(macro.count, 6);
                i += macro.pattern.size() * macro.count;
                continue;
            }
            int run = runLength(body, i);
            if (run >= MIN_RUN) {
                int chunk = Math.min(63, run);
                writer.write(1, 2);
                writer.write(table.indexOf(body.get(i)), bitsPerOpcode);
                writer.write(chunk, 6);
                i += chunk;
                continue;
            }
            int literalStart = i;
            int literalCount = 0;
            while (i < body.size() && literalCount < 15) {
                MacroMatch upcomingMacro = bestMacro(body, i);
                int upcomingRun = runLength(body, i);
                if (literalCount > 0 && ((upcomingMacro != null && upcomingMacro.count >= 2) || upcomingRun >= MIN_RUN)) {
                    break;
                }
                i++;
                literalCount++;
                if ((upcomingMacro != null && upcomingMacro.count >= 2) || upcomingRun >= MIN_RUN) {
                    break;
                }
            }
            writer.write(0, 2);
            writer.write(literalCount, 4);
            for (int j = 0; j < literalCount; j++) {
                writer.write(table.indexOf(body.get(literalStart + j)), bitsPerOpcode);
            }
        }
        return writer.toByteArray();
    }

    private static List<SfxAndroidInstruction> unpackCompressed(SfxAndroidType type, byte[] bytes, int expectedLength) {
        List<SfxAndroidInstruction> table = SfxAndroidInstruction.validForType(type);
        int bitsPerOpcode = bitsFor(table.size() - 1);
        BitReader reader = new BitReader(bytes);
        List<SfxAndroidInstruction> result = new ArrayList<>();
        while (result.size() < expectedLength && reader.remaining() >= 2) {
            int tokenType = reader.read(2);
            if (tokenType == 0) {
                int count = reader.read(4);
                if (count <= 0) {
                    throw new IllegalArgumentException("Invalid literal token");
                }
                for (int i = 0; i < count && result.size() < expectedLength; i++) {
                    result.add(readOpcode(reader, table, bitsPerOpcode));
                }
            } else if (tokenType == 1) {
                SfxAndroidInstruction instruction = readOpcode(reader, table, bitsPerOpcode);
                int count = reader.read(6);
                if (count <= 0) {
                    throw new IllegalArgumentException("Invalid repeat token");
                }
                for (int i = 0; i < count && result.size() < expectedLength; i++) {
                    result.add(instruction);
                }
            } else if (tokenType == 2) {
                int macroId = reader.read(4);
                int count = reader.read(6);
                if (macroId < 0 || macroId >= MACROS.size() || count <= 0) {
                    throw new IllegalArgumentException("Invalid macro token");
                }
                List<SfxAndroidInstruction> macro = MACROS.get(macroId);
                for (int i = 0; i < count && result.size() < expectedLength; i++) {
                    for (SfxAndroidInstruction instruction : macro) {
                        if (instruction.validFor(type) && result.size() < expectedLength) {
                            result.add(instruction);
                        }
                    }
                }
            } else {
                throw new IllegalArgumentException("Reserved Android script token");
            }
        }
        return result;
    }

    private static SfxAndroidInstruction readOpcode(BitReader reader, List<SfxAndroidInstruction> table, int bits) {
        int index = reader.read(bits);
        if (index < 0 || index >= table.size()) {
            throw new IllegalArgumentException("Invalid opcode");
        }
        return table.get(index);
    }

    private static int runLength(List<SfxAndroidInstruction> body, int start) {
        SfxAndroidInstruction instruction = body.get(start);
        int count = 1;
        for (int i = start + 1; i < body.size() && body.get(i) == instruction && count < 63; i++) {
            count++;
        }
        return count;
    }

    private static MacroMatch bestMacro(List<SfxAndroidInstruction> body, int start) {
        MacroMatch best = null;
        for (int i = 0; i < MACROS.size(); i++) {
            List<SfxAndroidInstruction> macro = MACROS.get(i);
            int count = countMacro(body, start, macro);
            if (count >= 2 && (best == null || count * macro.size() > best.count * best.pattern.size())) {
                best = new MacroMatch(i, macro, Math.min(63, count));
            }
        }
        return best;
    }

    private static int countMacro(List<SfxAndroidInstruction> body, int start, List<SfxAndroidInstruction> macro) {
        int count = 0;
        int index = start;
        while (count < 63 && index + macro.size() <= body.size()) {
            boolean match = true;
            for (int i = 0; i < macro.size(); i++) {
                if (body.get(index + i) != macro.get(i)) {
                    match = false;
                    break;
                }
            }
            if (!match) {
                break;
            }
            count++;
            index += macro.size();
        }
        return count;
    }

    private static int bitsFor(int maxValue) {
        int bits = 1;
        while ((1 << bits) <= maxValue) {
            bits++;
        }
        return bits;
    }

    private static String checksum(String input) {
        CRC32 crc = new CRC32();
        crc.update(input.getBytes(StandardCharsets.UTF_8));
        long value = crc.getValue() & 0x0FFF;
        return "" + encodeDigit((int) ((value / 62) % 62)) + encodeDigit((int) (value % 62));
    }

    private static char encodeDigit(int value) {
        if (value < 0 || value >= BASE62.length) {
            throw new IllegalArgumentException("Base62 digit out of range: " + value);
        }
        return BASE62[value];
    }

    private static int decodeDigit(char c) {
        for (int i = 0; i < BASE62.length; i++) {
            if (BASE62[i] == c) {
                return i;
            }
        }
        return -1;
    }

    private static String encodeBase62(byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return "0";
        }
        byte[] preserved = new byte[bytes.length + 1];
        preserved[0] = 1;
        System.arraycopy(bytes, 0, preserved, 1, bytes.length);
        BigInteger value = new BigInteger(1, preserved);
        if (value.signum() == 0) {
            return "0";
        }
        StringBuilder builder = new StringBuilder();
        BigInteger base = BigInteger.valueOf(62);
        while (value.signum() > 0) {
            BigInteger[] divRem = value.divideAndRemainder(base);
            builder.append(encodeDigit(divRem[1].intValue()));
            value = divRem[0];
        }
        return builder.reverse().toString();
    }

    private static byte[] decodeBase62(String input) {
        if (input == null || input.isBlank() || input.equals("0")) {
            return new byte[0];
        }
        BigInteger value = BigInteger.ZERO;
        BigInteger base = BigInteger.valueOf(62);
        for (int i = 0; i < input.length(); i++) {
            int digit = decodeDigit(input.charAt(i));
            if (digit < 0) {
                throw new IllegalArgumentException("Invalid Base62 payload");
            }
            value = value.multiply(base).add(BigInteger.valueOf(digit));
        }
        byte[] raw = value.toByteArray();
        if (raw.length > 1 && raw[0] == 0) {
            raw = Arrays.copyOfRange(raw, 1, raw.length);
        }
        if (raw.length == 0 || raw[0] != 1) {
            throw new IllegalArgumentException("Invalid Base62 payload sentinel");
        }
        return Arrays.copyOfRange(raw, 1, raw.length);
    }

    public record DecodedScript(SfxAndroidType type, List<SfxAndroidInstruction> body) {
        public DecodedScript {
            body = List.copyOf(body);
        }
    }

    private record MacroMatch(int id, List<SfxAndroidInstruction> pattern, int count) {
    }

    private static final class BitWriter {
        private final ByteArrayOutputStream output = new ByteArrayOutputStream();
        private int current;
        private int bitCount;

        void write(int value, int bits) {
            for (int i = bits - 1; i >= 0; i--) {
                current = (current << 1) | ((value >> i) & 1);
                bitCount++;
                if (bitCount == 8) {
                    output.write(current & 0xFF);
                    current = 0;
                    bitCount = 0;
                }
            }
        }

        byte[] toByteArray() {
            if (bitCount > 0) {
                output.write((current << (8 - bitCount)) & 0xFF);
            }
            return output.toByteArray();
        }
    }

    private static final class BitReader {
        private final byte[] bytes;
        private int bitIndex;

        BitReader(byte[] bytes) {
            this.bytes = bytes == null ? new byte[0] : bytes;
        }

        int remaining() {
            return bytes.length * 8 - bitIndex;
        }

        int read(int bits) {
            if (bits <= 0 || remaining() < bits) {
                throw new IllegalArgumentException("Script payload is truncated");
            }
            int value = 0;
            for (int i = 0; i < bits; i++) {
                int byteIndex = bitIndex / 8;
                int bitInByte = 7 - (bitIndex % 8);
                value = (value << 1) | ((bytes[byteIndex] >> bitInByte) & 1);
                bitIndex++;
            }
            return value;
        }
    }
}
