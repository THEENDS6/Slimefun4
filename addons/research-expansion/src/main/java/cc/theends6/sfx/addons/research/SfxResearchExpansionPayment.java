package cc.theends6.sfx.addons.research;

import cc.theends6.sfx.api.localization.SfxLocalizationView;
import cc.theends6.sfx.api.research.SfxResearchPaymentComponent;
import cc.theends6.sfx.api.research.SfxResearchPaymentContext;
import cc.theends6.sfx.api.research.SfxResearchPaymentResult;
import java.util.Locale;
import java.util.Objects;
import org.bukkit.GameMode;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

final class SfxResearchExpansionPayment implements SfxResearchPaymentComponent {
    private final FileConfiguration config;
    private final SfxLocalizationView localization;
    private final Unit defaultUnit;
    private final double multiplier;
    private final long roundingUnit;
    private final Rounding rounding;

    SfxResearchExpansionPayment(FileConfiguration config, SfxLocalizationView localization) {
        this.config = Objects.requireNonNull(config, "config");
        this.localization = Objects.requireNonNull(localization, "localization");
        this.defaultUnit = Unit.parse(config.getString("defaults.mode", "points"));
        this.multiplier = config.getDouble("defaults.multiplier", 1.0D);
        if (!Double.isFinite(multiplier) || multiplier < 0.0D) {
            throw new IllegalArgumentException("defaults.multiplier must be a finite value greater than or equal to zero");
        }
        this.roundingUnit = config.getLong("defaults.rounding.unit", 100L);
        if (roundingUnit < 1L) {
            throw new IllegalArgumentException("defaults.rounding.unit must be at least 1");
        }
        this.rounding = Rounding.parse(config.getString("defaults.rounding.mode", "up"));
        validateOverrides(config.getConfigurationSection("researches"));
    }

    @Override
    public String displayCost(SfxResearchPaymentContext context) {
        Cost cost = resolve(context);
        String key = cost.unit() == Unit.POINTS
                ? "sfx-research-expansion.payment.points"
                : "sfx-research-expansion.payment.levels";
        return localization.requiredText(key).replace("{cost}", Long.toString(cost.amount()));
    }

    @Override
    public SfxResearchPaymentResult charge(Player player, SfxResearchPaymentContext context) {
        Objects.requireNonNull(player, "player");
        if (player.getGameMode() == GameMode.CREATIVE) {
            return SfxResearchPaymentResult.success();
        }
        Cost cost = resolve(context);
        if (cost.unit() == Unit.LEVELS) {
            if (cost.amount() > Integer.MAX_VALUE || player.getLevel() < cost.amount()) {
                return rejected(cost);
            }
            player.setLevel(player.getLevel() - (int) cost.amount());
            return SfxResearchPaymentResult.success();
        }

        int available = player.calculateTotalExperiencePoints();
        if (available < cost.amount()) {
            return rejected(cost);
        }
        player.setExperienceLevelAndProgress((int) (available - cost.amount()));
        return SfxResearchPaymentResult.success();
    }

    private SfxResearchPaymentResult rejected(Cost cost) {
        String key = cost.unit() == Unit.POINTS
                ? "sfx-research-expansion.payment.not-enough-points"
                : "sfx-research-expansion.payment.not-enough-levels";
        return SfxResearchPaymentResult.rejected(
                localization.requiredText(key).replace("{cost}", Long.toString(cost.amount())));
    }

    private Cost resolve(SfxResearchPaymentContext context) {
        ConfigurationSection override = config.getConfigurationSection("researches." + context.researchId());
        if (override != null) {
            if (override.contains("final-points")) {
                return new Cost(nonNegative(override.getLong("final-points"), "final-points"), Unit.POINTS);
            }
            if (override.contains("final-levels")) {
                return new Cost(nonNegative(override.getLong("final-levels"), "final-levels"), Unit.LEVELS);
            }
        }

        long source;
        Unit sourceUnit;
        if (override != null && override.contains("source-points")) {
            source = nonNegative(override.getLong("source-points"), "source-points");
            sourceUnit = Unit.POINTS;
        } else {
            source = override != null && override.contains("source-level")
                    ? nonNegative(override.getLong("source-level"), "source-level")
                    : context.configuredLevelCost();
            sourceUnit = Unit.LEVELS;
        }

        Unit resultUnit = sourceUnit == Unit.POINTS ? Unit.POINTS : defaultUnit;
        long base = sourceUnit == Unit.LEVELS && resultUnit == Unit.POINTS
                ? totalExperienceForLevel(source)
                : source;
        long unit = resultUnit == Unit.POINTS ? roundingUnit : 1L;
        return new Cost(applyMultiplierAndRound(base, unit), resultUnit);
    }

    private long applyMultiplierAndRound(long base, long unit) {
        double scaled = base * multiplier;
        if (!Double.isFinite(scaled) || scaled > Long.MAX_VALUE) {
            throw new IllegalStateException("Calculated research cost exceeds the supported range");
        }
        double units = scaled / unit;
        double roundedUnits = switch (rounding) {
            case UP -> Math.ceil(units);
            case NEAREST -> Math.floor(units + 0.5D);
            case DOWN -> Math.floor(units);
        };
        if (roundedUnits > Long.MAX_VALUE / unit) {
            throw new IllegalStateException("Rounded research cost exceeds the supported range");
        }
        return (long) roundedUnits * unit;
    }

    private static long totalExperienceForLevel(long level) {
        double total;
        if (level <= 16L) {
            total = (double) level * level + 6.0D * level;
        } else if (level <= 31L) {
            total = (5.0D * level * level - 81.0D * level + 720.0D) / 2.0D;
        } else {
            total = (9.0D * level * level - 325.0D * level + 4440.0D) / 2.0D;
        }
        if (!Double.isFinite(total) || total > Long.MAX_VALUE) {
            throw new IllegalStateException("Converted research level cost exceeds the supported range");
        }
        return (long) total;
    }

    private static long nonNegative(long value, String key) {
        if (value < 0L) {
            throw new IllegalArgumentException(key + " must not be negative");
        }
        return value;
    }

    private static void validateOverrides(ConfigurationSection researches) {
        if (researches == null) {
            return;
        }
        for (String researchId : researches.getKeys(false)) {
            ConfigurationSection entry = researches.getConfigurationSection(researchId);
            if (entry == null) {
                throw new IllegalArgumentException("researches." + researchId + " must be a section");
            }
            int sourceCount = 0;
            for (String key : new String[] {"source-level", "source-points", "final-levels", "final-points"}) {
                if (entry.contains(key)) {
                    sourceCount++;
                    nonNegative(entry.getLong(key), "researches." + researchId + "." + key);
                }
            }
            if (sourceCount != 1) {
                throw new IllegalArgumentException("researches." + researchId
                        + " must declare exactly one source-level, source-points, final-levels or final-points value");
            }
        }
    }

    private enum Unit {
        LEVELS,
        POINTS;

        static Unit parse(String raw) {
            return switch (normalize(raw)) {
                case "levels", "level" -> LEVELS;
                case "points", "point", "experience-points" -> POINTS;
                default -> throw new IllegalArgumentException("Unsupported research cost mode: " + raw);
            };
        }
    }

    private enum Rounding {
        UP,
        NEAREST,
        DOWN;

        static Rounding parse(String raw) {
            return switch (normalize(raw)) {
                case "up", "ceil", "ceiling" -> UP;
                case "nearest", "round" -> NEAREST;
                case "down", "floor" -> DOWN;
                default -> throw new IllegalArgumentException("Unsupported research cost rounding mode: " + raw);
            };
        }
    }

    private static String normalize(String raw) {
        return raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    private record Cost(long amount, Unit unit) {
    }
}
