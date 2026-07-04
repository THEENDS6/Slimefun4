package cc.theends6.sfx.internal.recipe;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.bukkit.configuration.file.YamlConfiguration;

public final class SfxCompiledRecipeShapeValidator {
    private SfxCompiledRecipeShapeValidator() {
    }

    public static void main(String[] args) {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: SfxCompiledRecipeShapeValidator <compiled recipe root>");
        }
        File root = new File(args[0]);
        if (!root.isDirectory()) {
            throw new IllegalArgumentException("Compiled recipe root is not a directory: " + root.getAbsolutePath());
        }
        List<File> files = new ArrayList<>();
        collectYaml(root, files);
        files.sort(Comparator.comparing(File::getPath));
        for (File file : files) {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
            SfxRecipeYamlLoader.validateCompiledYamlShape(yaml, root.toPath().relativize(file.toPath()).toString().replace('\\', '/'));
        }
        System.out.println("Validated compiled recipe runtime shapes: " + files.size() + " YAML file(s).");
    }

    private static void collectYaml(File dir, List<File> files) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (child.isDirectory()) {
                collectYaml(child, files);
            } else if (child.isFile() && child.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) {
                files.add(child);
            }
        }
    }
}
