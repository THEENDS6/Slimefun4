package cc.theends6.sfx.internal.addon;

import java.io.File;
import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.jar.JarFile;

public final class SfxAddonJarResources {
    private SfxAddonJarResources() {
    }

    public static List<String> contentRelativePaths(File jarFile) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            return jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> contentRelativePath(entry.getName()))
                    .filter(path -> path != null && !path.isBlank())
                    .sorted()
                    .toList();
        }
    }

    public static List<String> languageEntryNames(File jarFile, String language) throws IOException {
        try (JarFile jar = new JarFile(jarFile)) {
            return jar.stream()
                    .filter(entry -> !entry.isDirectory())
                    .map(entry -> entry.getName().replace('\\', '/'))
                    .filter(name -> isLanguageEntry(name, language))
                    .sorted()
                    .toList();
        }
    }

    public static String contentRelativePath(String entryName) {
        String normalized = entryName.replace('\\', '/');
        int bundledContentIndex = normalized.indexOf("/content/");
        if (normalized.startsWith("content/addons/") && bundledContentIndex > 0) {
            return normalized.substring(bundledContentIndex + "/content/".length());
        }
        if (normalized.startsWith("content/") && !normalized.startsWith("content/addons/")) {
            return normalized.substring("content/".length());
        }
        return null;
    }

    public static boolean isLanguageEntry(String entryName, String language) {
        String normalized = entryName.replace('\\', '/');
        return normalized.equals("lang/" + language + ".yml")
                || (normalized.startsWith("content/addons/")
                && normalized.endsWith("/lang/" + language + ".yml"));
    }

    public static Comparator<java.util.jar.JarEntry> byEntryName() {
        return Comparator.comparing(entry -> entry.getName().replace('\\', '/'));
    }
}
