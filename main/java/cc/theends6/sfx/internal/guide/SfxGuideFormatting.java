package cc.theends6.sfx.internal.guide;

import java.util.Locale;
import java.util.function.Function;
import org.bukkit.Sound;




final class SfxGuideFormatting {
    private SfxGuideFormatting() {
    }

    static String formatDuration(double seconds) {
        if (seconds < 60.0) {
            double rounded = Math.round(seconds * 10.0) / 10.0;
            if (Math.abs(rounded - Math.rint(rounded)) < 0.0001) {
                return Integer.toString((int) Math.rint(rounded)) + "s";
            }
            return String.format(Locale.ROOT, "%.1fs", rounded);
        }
        long totalSeconds = Math.max(0L, Math.round(seconds));
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long remainingSeconds = totalSeconds % 60L;
        StringBuilder builder = new StringBuilder();
        if (hours > 0L) {
            builder.append(hours).append("h");
            if (minutes > 0L) {
                builder.append(minutes).append("m");
            }
            if (remainingSeconds > 0L) {
                builder.append(remainingSeconds).append("s");
            }
            return builder.toString();
        }
        builder.append(minutes).append("m");
        if (remainingSeconds > 0L) {
            builder.append(remainingSeconds).append("s");
        }
        return builder.toString();
    }

    static Sound resolveSoundCandidate(String candidate, Function<String, Sound> resolver) {
        if (candidate == null || candidate.isBlank()) {
            return null;
        }
        if (candidate.indexOf(':') >= 0) {
            return resolver.apply(candidate);
        }
        Sound namespaced = resolver.apply("minecraft:" + candidate);
        if (namespaced != null) {
            return namespaced;
        }
        String dotted = candidate.replace('_', '.');
        if (!dotted.equals(candidate)) {
            namespaced = resolver.apply("minecraft:" + dotted);
            if (namespaced != null) {
                return namespaced;
            }
        }
        return null;
    }
}
