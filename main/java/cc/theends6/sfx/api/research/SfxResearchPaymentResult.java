package cc.theends6.sfx.api.research;


public record SfxResearchPaymentResult(boolean paid, String failureMessage) {
    public SfxResearchPaymentResult {
        failureMessage = failureMessage == null ? "" : failureMessage;
    }

    public static SfxResearchPaymentResult success() {
        return new SfxResearchPaymentResult(true, "");
    }

    public static SfxResearchPaymentResult rejected(String message) {
        return new SfxResearchPaymentResult(false, message);
    }
}
