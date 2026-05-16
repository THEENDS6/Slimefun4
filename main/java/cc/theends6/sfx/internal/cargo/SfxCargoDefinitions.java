package cc.theends6.sfx.internal.cargo;

import java.util.LinkedHashMap;
import java.util.Map;

public final class SfxCargoDefinitions {
    private SfxCargoDefinitions() {
    }

    public static Map<String, SfxCargoComponentDefinition> create() {
        Map<String, SfxCargoComponentDefinition> definitions = new LinkedHashMap<>();
        define(definitions, "sf:cargo_manager", SfxCargoComponentType.MANAGER);
        define(definitions, "sf:cargo_node", SfxCargoComponentType.CONNECTOR);
        define(definitions, "sf:cargo_node_input", SfxCargoComponentType.INPUT_NODE);
        define(definitions, "sf:cargo_node_input_advanced", SfxCargoComponentType.ADVANCED_INPUT_NODE);
        define(definitions, "sf:cargo_node_output", SfxCargoComponentType.OUTPUT_NODE);
        define(definitions, "sf:cargo_node_output_advanced", SfxCargoComponentType.ADVANCED_OUTPUT_NODE);
        define(definitions, "sf:trash_can_block", SfxCargoComponentType.TRASH_CAN);
        define(definitions, "sf:reactor_access_port", SfxCargoComponentType.REACTOR_ACCESS_PORT);
        define(definitions, "sf:vanilla_auto_crafter", SfxCargoComponentType.VANILLA_AUTO_CRAFTER);
        define(definitions, "sf:enhanced_auto_crafter", SfxCargoComponentType.ENHANCED_AUTO_CRAFTER);
        define(definitions, "sf:armor_auto_crafter", SfxCargoComponentType.ARMOR_AUTO_CRAFTER);
        return definitions;
    }

    private static void define(Map<String, SfxCargoComponentDefinition> definitions, String id, SfxCargoComponentType type) {
        definitions.put(id, new SfxCargoComponentDefinition(id, type));
    }
}
