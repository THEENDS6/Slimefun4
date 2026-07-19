package cc.theends6.sfx.api.world;

public record SfxWorldActionResult(Status status, String message) {
    public enum Status { SUCCESS, PROTECTED, INVALID, FAILED }
    public boolean success() { return status == Status.SUCCESS; }
    public static SfxWorldActionResult succeeded() { return new SfxWorldActionResult(Status.SUCCESS, null); }
    public static SfxWorldActionResult protectedAction() { return new SfxWorldActionResult(Status.PROTECTED, null); }
    public static SfxWorldActionResult invalid(String message) { return new SfxWorldActionResult(Status.INVALID, message); }
    public static SfxWorldActionResult failed(String message) { return new SfxWorldActionResult(Status.FAILED, message); }
}
