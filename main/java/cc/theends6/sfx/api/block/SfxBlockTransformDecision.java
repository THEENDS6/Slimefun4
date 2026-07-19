package cc.theends6.sfx.api.block;

import org.bukkit.Material;

public record SfxBlockTransformDecision(Action action, Material replacementMaterial, String replacementTypeId) {
    public enum Action { ALLOW, CANCEL, REPLACE_WITH_CUSTOM_BLOCK }

    public static SfxBlockTransformDecision allow() { return new SfxBlockTransformDecision(Action.ALLOW, null, null); }
    public static SfxBlockTransformDecision cancel() { return new SfxBlockTransformDecision(Action.CANCEL, null, null); }
    public static SfxBlockTransformDecision replace(Material material, String typeId) {
        return new SfxBlockTransformDecision(Action.REPLACE_WITH_CUSTOM_BLOCK, material, typeId);
    }
}
