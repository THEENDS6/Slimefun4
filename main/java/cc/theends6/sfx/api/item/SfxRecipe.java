package cc.theends6.sfx.api.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;

public final class SfxRecipe {
    private final String id;
    private final String recipeType;
    private final List<SfxRecipeSlot> matrix;
    private final Component note;
    private final int outputAmount;

    private SfxRecipe(String id, String recipeType, List<SfxRecipeSlot> matrix, Component note, int outputAmount) {
        this.id = Objects.requireNonNull(id, "id");
        this.recipeType = Objects.requireNonNull(recipeType, "recipeType");
        if (matrix.size() != 9) {
            throw new IllegalArgumentException("SFX shaped recipe matrix must have exactly 9 slots.");
        }
        this.matrix = Collections.unmodifiableList(new ArrayList<>(matrix));
        this.note = note;
        this.outputAmount = Math.max(1, outputAmount);
    }

    public static SfxRecipe shaped(String recipeType, List<SfxRecipeSlot> matrix, Component note) {
        return new SfxRecipe(recipeType, recipeType, matrix, note, 1);
    }

    public static SfxRecipe shaped(String id, String recipeType, List<SfxRecipeSlot> matrix, Component note, int outputAmount) {
        return new SfxRecipe(id, recipeType, matrix, note, outputAmount);
    }

    public String id() {
        return id;
    }

    public String recipeType() {
        return recipeType;
    }

    public List<SfxRecipeSlot> matrix() {
        return matrix;
    }

    public Component note() {
        return note;
    }

    public int outputAmount() {
        return outputAmount;
    }
}
