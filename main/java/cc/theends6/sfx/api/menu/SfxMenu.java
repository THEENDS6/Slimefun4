package cc.theends6.sfx.api.menu;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

public final class SfxMenu {
    private final int rows;
    private final Component title;
    private final Map<Integer, SfxMenuButton> buttons;
    private final boolean cancelPlayerClicks;
    private final Consumer<org.bukkit.entity.Player> closeHandler;

    private SfxMenu(Builder builder) {
        this.rows = builder.rows;
        this.title = builder.title;
        this.buttons = Collections.unmodifiableMap(new HashMap<>(builder.buttons));
        this.cancelPlayerClicks = builder.cancelPlayerClicks;
        this.closeHandler = builder.closeHandler;
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

    public boolean cancelPlayerClicks() {
        return cancelPlayerClicks;
    }

    public Consumer<org.bukkit.entity.Player> closeHandler() {
        return closeHandler;
    }

    public static Builder builder(Component title) {
        return new Builder(title);
    }

    public static final class Builder {
        private int rows = 3;
        private final Component title;
        private final Map<Integer, SfxMenuButton> buttons = new HashMap<>();
        private boolean cancelPlayerClicks = true;
        private Consumer<org.bukkit.entity.Player> closeHandler;

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

        public SfxMenu build() {
            return new SfxMenu(this);
        }
    }
}
