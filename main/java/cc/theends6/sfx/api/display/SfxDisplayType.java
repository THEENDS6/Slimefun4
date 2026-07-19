package cc.theends6.sfx.api.display;

public record SfxDisplayType(String id, String categoryId, double visibleDistance,
                             SfxDisplayUpdateStrategy updateStrategy, int minimumUpdateTicks) {
    public SfxDisplayType {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("Display type id must not be blank");
        if (categoryId == null || categoryId.isBlank()) throw new IllegalArgumentException("categoryId must not be blank");
        if (!Double.isFinite(visibleDistance) || visibleDistance <= 0.0D) throw new IllegalArgumentException("visibleDistance must be positive");
        if (updateStrategy == null) throw new IllegalArgumentException("updateStrategy must not be null");
        minimumUpdateTicks = Math.max(0, minimumUpdateTicks);
    }
}
