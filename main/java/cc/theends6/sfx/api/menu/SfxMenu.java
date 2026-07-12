package cc.theends6.sfx.api.menu;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Function;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public final class SfxMenu {
    private final int rows;
    private final Component title;
    private final Map<Integer, SfxMenuButton> buttons;
    private final Map<Integer, Function<Player, ItemStack>> dynamicIcons;
    private final boolean cancelPlayerClicks;
    private final Consumer<org.bukkit.entity.Player> closeHandler;
    private final boolean restorePreviousOnClose;

    private SfxMenu(Builder builder) {
        this.rows = builder.rows;
        this.title = builder.title;
        this.buttons = Collections.unmodifiableMap(new HashMap<>(builder.buttons));
        this.dynamicIcons = Collections.unmodifiableMap(new HashMap<>(builder.dynamicIcons));
        this.cancelPlayerClicks = builder.cancelPlayerClicks;
        this.closeHandler = builder.closeHandler;
        this.restorePreviousOnClose = builder.restorePreviousOnClose;
    }

    public int rows() {
        return rows;
    }

    public Component title() {
        return title;
    }

    public Map<Integer, SfxMenuButton> buttons() {
        return buttons;
    }

    public Map<Integer, Function<Player, ItemStack>> dynamicIcons() {
        return dynamicIcons;
    }

    public boolean cancelPlayerClicks() {
        return cancelPlayerClicks;
    }

    public Consumer<org.bukkit.entity.Player> closeHandler() {
        return closeHandler;
    }

    public boolean restorePreviousOnClose() {
        return restorePreviousOnClose;
    }

    public static Builder builder(Component title) {
        return new Builder(title);
    }

    public static final class Builder {
        private int rows = 3;
        private final Component title;
        private final Map<Integer, SfxMenuButton> buttons = new HashMap<>();
        private final Map<Integer, Function<Player, ItemStack>> dynamicIcons = new HashMap<>();
        private boolean cancelPlayerClicks = true;
        private Consumer<org.bukkit.entity.Player> closeHandler;
        private boolean restorePreviousOnClose;

        private Builder(Component title) {
            this.title = title;
        }

        public Builder rows(int rows) {
            if (rows < 1 || rows > 6) {
                throw new IllegalArgumentException("Rows must be between 1 and 6.");
            }
            this.rows = rows;
            return this;
        }

        public Builder button(int slot, SfxMenuButton button) {
            this.buttons.put(slot, button);
            this.dynamicIcons.remove(slot);
            return this;
        }

        public Builder dynamicButton(int slot, SfxMenuButton button, Function<Player, ItemStack> iconProvider) {
            this.buttons.put(slot, button);
            this.dynamicIcons.put(slot, iconProvider);
            return this;
        }

        public Builder cancelPlayerClicks(boolean cancelPlayerClicks) {
            this.cancelPlayerClicks = cancelPlayerClicks;
            return this;
        }

        public Builder onClose(Consumer<org.bukkit.entity.Player> closeHandler) {
            this.closeHandler = closeHandler;
            return this;
        }

        public Builder restorePreviousOnClose(boolean restorePreviousOnClose) {
            this.restorePreviousOnClose = restorePreviousOnClose;
            return this;
        }

        public SfxMenu build() {
            return new SfxMenu(this);
        }
    }
}
