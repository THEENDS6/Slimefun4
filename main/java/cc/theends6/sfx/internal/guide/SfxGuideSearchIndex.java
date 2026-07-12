package cc.theends6.sfx.internal.guide;

import cc.theends6.sfx.api.item.SfxItemDefinition;
import cc.theends6.sfx.internal.item.DefaultSfxItemRegistry;
import com.github.houbb.pinyin.constant.enums.PinyinStyleEnum;
import com.github.houbb.pinyin.util.PinyinHelper;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

final class SfxGuideSearchIndex {
    private static final Comparator<Match> MATCH_ORDER = Comparator
            .comparingInt(Match::score)
            .thenComparing(match -> orderOf(match.item()))
            .thenComparing(match -> match.item().id());

    private final DefaultSfxItemRegistry registry;
    private volatile Snapshot snapshot = new Snapshot(-1L, false, false, List.of());

    SfxGuideSearchIndex(DefaultSfxItemRegistry registry) {
        this.registry = registry;
    }

    List<SfxItemDefinition> search(String input, boolean pinyinEnabled, boolean initialsEnabled) {
        String query = normalize(input);
        if (query.isEmpty()) {
            return List.of();
        }
        Snapshot current = snapshot;
        long revision = registry.revision();
        if (current.revision() != revision
                || current.pinyinEnabled() != pinyinEnabled
                || current.initialsEnabled() != initialsEnabled) {
            current = rebuild(revision, pinyinEnabled, initialsEnabled);
        }
        List<Match> matches = new ArrayList<>();
        for (Document document : current.documents()) {
            int score = score(document, query, pinyinEnabled, initialsEnabled);
            if (score >= 0) {
                matches.add(new Match(document.item(), score));
            }
        }
        matches.sort(MATCH_ORDER);
        return matches.stream().map(Match::item).toList();
    }

    private synchronized Snapshot rebuild(long revision, boolean pinyinEnabled, boolean initialsEnabled) {
        Snapshot current = snapshot;
        if (current.revision() == revision
                && current.pinyinEnabled() == pinyinEnabled
                && current.initialsEnabled() == initialsEnabled) {
            return current;
        }
        PlainTextComponentSerializer plain = PlainTextComponentSerializer.plainText();
        List<Document> documents = registry.items().stream()
                .filter(item -> !item.hidden())
                .map(item -> {
                    String name = normalize(plain.serialize(item.name()));
                    String id = normalize(item.id());
                    String pinyin = pinyinEnabled ? toPinyin(name, PinyinStyleEnum.NORMAL) : "";
                    String initials = initialsEnabled ? toPinyin(name, PinyinStyleEnum.FIRST_LETTER) : "";
                    return new Document(item, name, id, pinyin, initials);
                })
                .toList();
        snapshot = new Snapshot(revision, pinyinEnabled, initialsEnabled, documents);
        return snapshot;
    }

    private static int score(Document document, String query, boolean pinyinEnabled, boolean initialsEnabled) {
        if (document.name().equals(query) || document.id().equals(query)) {
            return 0;
        }
        if (document.name().startsWith(query)) {
            return 10;
        }
        if (document.name().contains(query) || document.id().contains(query)) {
            return 20;
        }
        if (pinyinEnabled && document.pinyin().startsWith(query)) {
            return 30;
        }
        if (initialsEnabled && document.initials().startsWith(query)) {
            return 40;
        }
        if (pinyinEnabled && document.pinyin().contains(query)) {
            return 50;
        }
        if (initialsEnabled && document.initials().contains(query)) {
            return 60;
        }
        return -1;
    }

    private static String toPinyin(String input, PinyinStyleEnum style) {
        try {
            return normalize(PinyinHelper.toPinyin(input, style, ""));
        } catch (RuntimeException ignored) {
            return "";
        }
    }

    static String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "";
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFKC).toLowerCase(Locale.ROOT);
        StringBuilder result = new StringBuilder(normalized.length());
        normalized.codePoints()
                .filter(Character::isLetterOrDigit)
                .forEach(result::appendCodePoint);
        return result.toString();
    }

    private static double orderOf(SfxItemDefinition item) {
        return item.guideOrder() == null ? item.order() : item.guideOrder();
    }

    private record Snapshot(long revision, boolean pinyinEnabled, boolean initialsEnabled, List<Document> documents) {
    }

    private record Document(SfxItemDefinition item, String name, String id, String pinyin, String initials) {
    }

    private record Match(SfxItemDefinition item, int score) {
    }
}
