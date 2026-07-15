package cc.theends6.sfx.api.item;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import org.bukkit.Material;

public final class SfxItemDefinition {
    public static final int DEFAULT_ORDER = 1_000_000;

    private final String id;
    private final Material material;
    private final Component name;
    private final String nameKey;
    private final List<Component> lore;
    private final String loreKey;
    private final String categoryId;
    private final int order;
    private final String guideCategoryId;
    private final Double guideOrder;
    private final String guideFuelProfile;
    private final int version;
    private final boolean hidden;
    private final boolean giveable;
    private final String permission;
    private final String usePermission;
    private final Float cooldownSeconds;
    private final String cooldownGroup;
    private final SfxItemKind kind;
    private final String variant;
    private final String headTextureHash;
    private final Integer colorRgb;
    private final List<String> flags;
    private final List<String> itemFlags;
    private final Map<String, Integer> enchantments;
    private final boolean unbreakable;
    private final List<SfxRecipe> recipes;

    private SfxItemDefinition(Builder builder) {
        this.id = normalizeId(builder.id);
        this.material = Objects.requireNonNull(builder.material, "material");
        this.name = Objects.requireNonNull(builder.name, "name");
        this.nameKey = Objects.requireNonNull(builder.nameKey, "nameKey");
        this.lore = Collections.unmodifiableList(new ArrayList<>(builder.lore));
        this.loreKey = builder.loreKey == null || builder.loreKey.isBlank() ? null : builder.loreKey.trim();
        this.categoryId = builder.categoryId == null ? null : SfxItemCategory.normalizeId(builder.categoryId);
        this.order = builder.order;
        this.guideCategoryId = builder.guideCategoryId == null ? null : SfxItemCategory.normalizeId(builder.guideCategoryId);
        this.guideOrder = builder.guideOrder;
        this.guideFuelProfile = builder.guideFuelProfile == null || builder.guideFuelProfile.isBlank()
                ? null
                : builder.guideFuelProfile.trim().replace('_', '-').toLowerCase();
        this.version = Math.max(1, builder.version);
        this.hidden = builder.hidden;
        this.giveable = builder.giveable;
        this.permission = normalizePermission(builder.permission);
        this.usePermission = normalizePermission(builder.usePermission);
        this.cooldownSeconds = builder.cooldownSeconds;
        this.cooldownGroup = builder.cooldownGroup == null || builder.cooldownGroup.isBlank()
                ? null : builder.cooldownGroup.trim().toLowerCase();
        this.kind = builder.kind == null ? SfxItemKind.ITEM : builder.kind;
        this.variant = Objects.requireNonNullElse(builder.variant, "default");
        this.headTextureHash = normalizeTextureHash(builder.headTextureHash);
        this.colorRgb = builder.colorRgb;
        this.flags = Collections.unmodifiableList(new ArrayList<>(builder.flags));
        this.itemFlags = Collections.unmodifiableList(new ArrayList<>(builder.itemFlags));
        this.enchantments = Collections.unmodifiableMap(new LinkedHashMap<>(builder.enchantments));
        this.unbreakable = builder.unbreakable;
        this.recipes = Collections.unmodifiableList(new ArrayList<>(builder.recipes));
    }

    public static Builder builder(String id, Material material, Component name) {
        return new Builder(id, material, name);
    }

    public static String normalizeId(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("SFX item id cannot be blank.");
        }
        String normalized = id.trim().toLowerCase();
        if (!normalized.matches("[a-z0-9_./:-]+")) {
            throw new IllegalArgumentException("Invalid SFX item id: " + id);
        }
        return normalized;
    }

    private static String normalizeTextureHash(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        String normalized = input.trim().toLowerCase();
        String texturePrefix = "https://textures.minecraft.net/texture/";
        if (normalized.startsWith(texturePrefix)) {
            normalized = normalized.substring(texturePrefix.length());
        }
        if (!normalized.matches("[0-9a-f]{32,128}")) {
            throw new IllegalArgumentException("Invalid SFX head texture hash, expected 32-128 lowercase hex characters: " + input);
        }
        return normalized;
    }

    public String id() {
        return id;
    }

    public Material material() {
        return material;
    }

    public Component name() {
        return name;
    }

    private static String normalizePermission(String permission) {
        return permission == null || permission.isBlank() ? null : permission.trim().toLowerCase();
    }

    public String nameKey() {
        return nameKey;
    }

    public List<Component> lore() {
        return lore;
    }

    public String loreKey() {
        return loreKey;
    }

    public String categoryId() {
        return categoryId;
    }

    public int version() {
        return version;
    }

    public int order() {
        return order;
    }

    public String guideCategoryId() {
        return guideCategoryId;
    }

    public Double guideOrder() {
        return guideOrder;
    }

    public String guideFuelProfile() {
        return guideFuelProfile;
    }

    public boolean hidden() {
        return hidden;
    }

    public boolean giveable() {
        return giveable;
    }

    
    public String permission() {
        return permission;
    }

    
    public String usePermission() {
        return usePermission;
    }

    
    public Float cooldownSeconds() {
        return cooldownSeconds;
    }

    
    public String cooldownGroup() {
        return cooldownGroup;
    }

    public SfxItemKind kind() {
        return kind;
    }

    public String variant() {
        return variant;
    }

    public String headTextureHash() {
        return headTextureHash;
    }

    public Integer colorRgb() {
        return colorRgb;
    }

    public List<String> flags() {
        return flags;
    }

    public List<String> itemFlags() {
        return itemFlags;
    }

    public Map<String, Integer> enchantments() {
        return enchantments;
    }

    public boolean unbreakable() {
        return unbreakable;
    }

    public List<SfxRecipe> recipes() {
        return recipes;
    }

    public static final class Builder {
        private final String id;
        private final Material material;
        private final Component name;
        private String nameKey;
        private final List<Component> lore = new ArrayList<>();
        private String loreKey;
        private String categoryId;
        private int order = DEFAULT_ORDER;
        private String guideCategoryId;
        private Double guideOrder;
        private String guideFuelProfile;
        private int version = 1;
        private boolean hidden;
        private boolean giveable = true;
        private String permission;
        private String usePermission;
        private Float cooldownSeconds;
        private String cooldownGroup;
        private SfxItemKind kind = SfxItemKind.ITEM;
        private String variant = "default";
        private String headTextureHash;
        private Integer colorRgb;
        private final List<String> flags = new ArrayList<>();
        private final List<String> itemFlags = new ArrayList<>();
        private final Map<String, Integer> enchantments = new LinkedHashMap<>();
        private boolean unbreakable;
        private final List<SfxRecipe> recipes = new ArrayList<>();

        private Builder(String id, Material material, Component name) {
            this.id = id;
            this.material = material;
            this.name = name;
            this.nameKey = defaultNameKey(id);
        }

        public Builder nameKey(String nameKey) {
            this.nameKey = nameKey;
            return this;
        }

        public Builder lore(List<Component> lore) {
            this.lore.clear();
            this.lore.addAll(lore);
            return this;
        }

        public Builder loreKey(String loreKey) {
            this.loreKey = loreKey;
            return this;
        }

        public Builder addLore(Component line) {
            this.lore.add(line);
            return this;
        }

        public Builder category(String categoryId) {
            this.categoryId = categoryId;
            return this;
        }

        public Builder order(int order) {
            this.order = order;
            return this;
        }

        public Builder guideCategory(String guideCategoryId) {
            this.guideCategoryId = guideCategoryId;
            return this;
        }

        public Builder guideOrder(double guideOrder) {
            if (!Double.isFinite(guideOrder)) {
                throw new IllegalArgumentException("Guide order must be finite.");
            }
            this.guideOrder = guideOrder;
            return this;
        }

        public Builder guideFuelProfile(String guideFuelProfile) {
            this.guideFuelProfile = guideFuelProfile;
            return this;
        }

        public Builder version(int version) {
            this.version = version;
            return this;
        }

        public Builder hidden(boolean hidden) {
            this.hidden = hidden;
            return this;
        }

        public Builder giveable(boolean giveable) {
            this.giveable = giveable;
            return this;
        }

        public Builder kind(SfxItemKind kind) {
            this.kind = kind;
            return this;
        }

        public Builder variant(String variant) {
            this.variant = variant;
            return this;
        }

        public Builder headTexture(String textureHash) {
            this.headTextureHash = textureHash;
            return this;
        }

        public Builder colorRgb(int colorRgb) {
            this.colorRgb = colorRgb & 0xFFFFFF;
            return this;
        }

        public Builder flag(String flag) {
            if (flag != null && !flag.isBlank()) {
                this.flags.add(flag.trim().toLowerCase());
            }
            return this;
        }

        public Builder itemFlag(String flag) {
            if (flag != null && !flag.isBlank()) {
                this.itemFlags.add(flag.trim().toUpperCase());
            }
            return this;
        }

        public Builder enchantment(String enchantment, int level) {
            if (enchantment != null && !enchantment.isBlank() && level > 0) {
                this.enchantments.put(enchantment.trim(), level);
            }
            return this;
        }

        public Builder unbreakable(boolean unbreakable) {
            this.unbreakable = unbreakable;
            return this;
        }

        public Builder addRecipe(SfxRecipe recipe) {
            this.recipes.add(recipe);
            return this;
        }

        public SfxItemDefinition build() {
            return new SfxItemDefinition(this);
        }

        public Builder permission(String permission) {
            this.permission = permission;
            return this;
        }

        public Builder usePermission(String usePermission) {
            this.usePermission = usePermission;
            return this;
        }

        public Builder useCooldown(float seconds, String group) {
            if (!Float.isFinite(seconds) || seconds <= 0.0f) {
                throw new IllegalArgumentException("Use cooldown must be a positive finite number.");
            }
            this.cooldownSeconds = seconds;
            this.cooldownGroup = group;
            return this;
        }

        private static String defaultNameKey(String id) {
            return "items." + normalizeId(id).replace(':', '.').replace('/', '.') + ".name";
        }
    }
}
