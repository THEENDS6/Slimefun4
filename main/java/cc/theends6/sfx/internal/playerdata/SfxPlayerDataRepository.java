package cc.theends6.sfx.internal.playerdata;

import java.io.Closeable;
import java.sql.SQLException;
import java.util.UUID;

public interface SfxPlayerDataRepository extends Closeable {
    void initialize() throws SQLException;

    SfxPlayerProfile load(UUID ownerId, String lastKnownName) throws Exception;

    void save(SfxPlayerProfile profile) throws Exception;

    @Override
    void close();
}
