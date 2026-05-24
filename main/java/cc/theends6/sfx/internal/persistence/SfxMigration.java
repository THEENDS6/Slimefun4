package cc.theends6.sfx.internal.persistence;

public interface SfxMigration {
    int fromVersion();
    int toVersion();
    void migrate() throws Exception;
}
