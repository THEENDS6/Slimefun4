package cc.theends6.sfx.internal.playerdata;

import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class SfxItemStackCodecTest {
    @Test
    void rejectsOversizedModernInventoryBeforeAllocating() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream output = new DataOutputStream(bytes)) {
            output.writeInt(0x53465849);
            output.writeInt(1);
            output.writeInt(1025);
        }

        assertThrows(IOException.class, () -> SfxItemStackCodec.decode(bytes.toByteArray()));
    }
}
