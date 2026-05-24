package cc.theends6.sfx.internal.core;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class SfxAuditReport {
    public enum Severity { INFO, WARNING, ERROR }

    public record Entry(Severity severity, String message) {
        public Entry {
            Objects.requireNonNull(severity, "severity");
            message = message == null ? "" : message;
        }
    }

    public static final class Builder {
        private final String name;
        private final List<Entry> entries = new ArrayList<>();

        private Builder(String name) {
            this.name = Objects.requireNonNull(name, "name");
        }

        public Builder info(String message) {
            entries.add(new Entry(Severity.INFO, message));
            return this;
        }

        public Builder warning(String message) {
            entries.add(new Entry(Severity.WARNING, message));
            return this;
        }

        public Builder error(String message) {
            entries.add(new Entry(Severity.ERROR, message));
            return this;
        }

        public Builder add(Severity severity, String message) {
            entries.add(new Entry(severity, message));
            return this;
        }

        public SfxAuditReport build() {
            return new SfxAuditReport(name, Instant.now(), entries);
        }
    }

    private final String name;
    private final Instant createdAt;
    private final List<Entry> entries;

    private SfxAuditReport(String name, Instant createdAt, List<Entry> entries) {
        this.name = name;
        this.createdAt = createdAt;
        this.entries = List.copyOf(entries);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() {
        return name;
    }

    public Instant createdAt() {
        return createdAt;
    }

    public List<Entry> entries() {
        return Collections.unmodifiableList(entries);
    }

    public long warningCount() {
        return entries.stream().filter(entry -> entry.severity() == Severity.WARNING).count();
    }

    public long errorCount() {
        return entries.stream().filter(entry -> entry.severity() == Severity.ERROR).count();
    }
}
