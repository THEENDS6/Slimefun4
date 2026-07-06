package cc.theends6.sfx.internal.addon;

import java.io.File;
import java.util.List;

public final class SfxAddonJarResourceSmokeCli {
    private SfxAddonJarResourceSmokeCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: SfxAddonJarResourceSmokeCli <addon-jar>");
        }
        File jar = new File(args[0]);
        List<String> content = SfxAddonJarResources.contentRelativePaths(jar);
        require(content.contains("templates/root-smoke.yml"), "missing root content overlay");
        require(content.contains("templates/bundled-style-smoke.yml"), "missing bundled-style content overlay");
        require(!content.contains("lang/en-US.yml"), "language file must not be treated as content");

        List<String> language = SfxAddonJarResources.languageEntryNames(jar, "en-US");
        require(language.contains("lang/en-US.yml"), "missing root language overlay");
        require(language.contains("content/addons/smoke/lang/en-US.yml"), "missing bundled-style language overlay");
        System.out.println("Validated SFX addon jar resource smoke paths.");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
