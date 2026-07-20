package cc.theends6.sfx.api.world;

public record SfxRangeWorldActionResult(Status status, int requested, int succeeded, String message) {
    public enum Status { SUCCESS, PROTECTED, CROSS_REGION, RESOURCE_REJECTED, INVALID, PARTIAL, FAILED }

    public boolean success() { return status == Status.SUCCESS; }
}
