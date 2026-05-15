package cc.theends6.sfx.internal.topology;

public record SfxTopologyCapabilities(boolean backbone, boolean controller, boolean terminal) {
    public static final SfxTopologyCapabilities NONE = new SfxTopologyCapabilities(false, false, false);

    public boolean participates() {
        return backbone || controller || terminal;
    }
}
