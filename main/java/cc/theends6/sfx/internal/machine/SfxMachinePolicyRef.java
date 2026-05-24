package cc.theends6.sfx.internal.machine;


public record SfxMachinePolicyRef(String type, String name) {
    public SfxMachinePolicyRef {
        type = type == null || type.isBlank() ? "custom" : type;
        name = name == null || name.isBlank() ? "unnamed" : name;
    }
    public static SfxMachinePolicyRef of(String type, String name) { return new SfxMachinePolicyRef(type, name); }
}
