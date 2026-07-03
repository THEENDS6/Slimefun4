package cc.theends6.sfx.internal.configurable;

import java.util.Locale;
import java.util.Map;

record SfxConfigurableMachineUiDefinition(Map<String, SfxConfigurableMachineUiPanel> panels) {
    SfxConfigurableMachineUiDefinition {
        panels = panels == null ? Map.of() : Map.copyOf(panels);
    }

    SfxConfigurableMachineUiPanel panel(SfxConfigurableMachineHolder.PanelType panelType) {
        return panel(panelKey(panelType));
    }

    SfxConfigurableMachineUiPanel panel(String key) {
        return panels.get(normalize(key));
    }

    private static String panelKey(SfxConfigurableMachineHolder.PanelType panelType) {
        return panelType.name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    static String normalize(String key) {
        return key == null ? "" : key.trim().replace('_', '-').toLowerCase(Locale.ROOT);
    }
}
