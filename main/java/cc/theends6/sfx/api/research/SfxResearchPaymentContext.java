package cc.theends6.sfx.api.research;


public record SfxResearchPaymentContext(String researchId, int configuredLevelCost) {
    public SfxResearchPaymentContext {
        if (researchId == null || researchId.isBlank()) {
            throw new IllegalArgumentException("Research id must not be blank");
        }
        researchId = researchId.trim().toLowerCase(java.util.Locale.ROOT);
        if (configuredLevelCost < 0) {
            throw new IllegalArgumentException("Configured research level cost must not be negative");
        }
    }
}
