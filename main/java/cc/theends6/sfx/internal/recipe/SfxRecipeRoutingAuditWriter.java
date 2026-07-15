package cc.theends6.sfx.internal.recipe;

import cc.theends6.sfx.api.item.SfxRecipeSlot;
import cc.theends6.sfx.internal.machine.DefaultManualMachineRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;


public final class SfxRecipeRoutingAuditWriter {
    private SfxRecipeRoutingAuditWriter() {
    }

    public static void write(Path dataDirectory, DefaultSfxRecipeRegistry recipes,
                             DefaultManualMachineRegistry machines, Logger logger) {
        Path debugDirectory = dataDirectory.resolve("debug");
        Path target = debugDirectory.resolve("recipe-routing-audit.yml");
        Path temporary = debugDirectory.resolve("recipe-routing-audit.yml.tmp");
        try {
            Files.createDirectories(debugDirectory);
            Files.writeString(temporary, render(recipes, machines), StandardCharsets.UTF_8);
            Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException exception) {
            logger.warning("Failed to write recipe routing audit: " + exception.getMessage());
        }
    }

    static String render(DefaultSfxRecipeRegistry recipes, DefaultManualMachineRegistry machines) {
        StringBuilder out = new StringBuilder(64_000);
        Map<String, List<Route>> signatures = new LinkedHashMap<>();
        List<SfxRecipeDefinition> definitions = recipes.definitions().stream()
                .sorted(Comparator.comparing(SfxRecipeDefinition::id)).toList();
        out.append("summary:\n  recipes: ").append(definitions.size()).append("\nrecipes:\n");
        for (SfxRecipeDefinition definition : definitions) {
            List<String> resolved = recipes.executionMachineIds(definition.id(), machines);
            out.append("  - id: ").append(quoted(definition.id())).append('\n');
            out.append("    recipe-type: ").append(quoted(definition.recipeType())).append('\n');
            out.append("    operation: ").append(definition.operation().name().toLowerCase()).append('\n');
            out.append("    source: ").append(quoted(definition.source())).append('\n');
            out.append("    runtime: ").append(definition.runtimeEnabled()).append('\n');
            appendList(out, "    declared-machine-ids", definition.runtimeMachineIds());
            appendList(out, "    declared-machine-tags", definition.runtimeMachineTags());
            appendList(out, "    resolved-machine-ids", resolved);
            appendSlots(out, "    inputs", definition.inputs());
            appendOutputs(out, "    outputs", definition.outputs());
            appendOutputs(out, "    random-outputs", definition.randomOutputs());
            String signature = inputSignature(definition);
            for (String machineId : resolved) {
                signatures.computeIfAbsent(machineId + "|" + signature, ignored -> new ArrayList<>())
                        .add(new Route(machineId, signature, definition.id()));
            }
        }
        List<List<Route>> conflicts = signatures.values().stream().filter(routes -> routes.size() > 1).toList();
        out.append("potential-input-conflicts:\n");
        if (conflicts.isEmpty()) {
            out.append("  []\n");
        } else {
            for (List<Route> routes : conflicts) {
                Route first = routes.getFirst();
                out.append("  - machine: ").append(quoted(first.machineId())).append('\n');
                out.append("    input-signature: ").append(quoted(first.signature())).append('\n');
                appendList(out, "    recipes", routes.stream().map(Route::recipeId).toList());
            }
        }
        return out.toString();
    }

    private static void appendSlots(StringBuilder out, String key, List<SfxRecipeSlot> slots) {
        out.append(key).append(":\n");
        for (int index = 0; index < slots.size(); index++) {
            SfxRecipeSlot slot = slots.get(index);
            if (slot == null || slot.isEmpty()) {
                continue;
            }
            out.append("      - slot: ").append(index).append('\n');
            out.append("        item: ").append(quoted(slot.isSfxItem() ? slot.sfxItemId() : slot.material().name())).append('\n');
            out.append("        amount: ").append(slot.amount()).append('\n');
        }
    }

    private static void appendOutputs(StringBuilder out, String key, List<SfxRecipeOutputDefinition> outputs) {
        out.append(key).append(":\n");
        if (outputs.isEmpty()) {
            out.append("      []\n");
            return;
        }
        for (SfxRecipeOutputDefinition output : outputs) {
            out.append("      - item: ").append(quoted(output.isSfxItem() ? output.sfxItemId() : output.material().name())).append('\n');
            out.append("        amount: ").append(output.amount()).append('\n');
        }
    }

    private static void appendList(StringBuilder out, String key, List<String> values) {
        out.append(key).append(":");
        if (values.isEmpty()) {
            out.append(" []\n");
            return;
        }
        out.append('\n');
        for (String value : values) {
            out.append("      - ").append(quoted(value)).append('\n');
        }
    }

    private static String inputSignature(SfxRecipeDefinition definition) {
        List<String> parts = new ArrayList<>();
        for (int index = 0; index < definition.inputs().size(); index++) {
            SfxRecipeSlot slot = definition.inputs().get(index);
            if (slot == null || slot.isEmpty()) {
                continue;
            }
            String identity = slot.isSfxItem() ? "sfx:" + slot.sfxItemId() : "vanilla:" + slot.material().name();
            parts.add(definition.operation() == SfxRecipeOperation.SHAPED ? index + "=" + identity : identity);
        }
        if (definition.operation() != SfxRecipeOperation.SHAPED) {
            parts.sort(String::compareTo);
        }
        return parts.isEmpty() ? "empty" : String.join(",", parts);
    }

    private static String quoted(String value) {
        return "\"" + value.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }

    private record Route(String machineId, String signature, String recipeId) {
    }
}
