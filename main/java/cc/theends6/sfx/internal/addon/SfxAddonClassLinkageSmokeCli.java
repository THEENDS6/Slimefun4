package cc.theends6.sfx.internal.addon;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public final class SfxAddonClassLinkageSmokeCli {
    private SfxAddonClassLinkageSmokeCli() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Expected addon jar path.");
        }
        File addonJar = new File(args[0]);
        if (!addonJar.isFile()) {
            throw new IllegalArgumentException("Missing addon jar: " + addonJar);
        }

        List<String> classNames = new ArrayList<>();
        try (JarFile jar = new JarFile(addonJar)) {
            jar.stream()
                    .map(JarEntry::getName)
                    .filter(name -> name.endsWith(".class") && !name.equals("module-info.class"))
                    .map(name -> name.substring(0, name.length() - 6).replace('/', '.'))
                    .forEach(classNames::add);
        }

        try (URLClassLoader addonLoader = new URLClassLoader(
                new URL[] {addonJar.toURI().toURL()},
                SfxAddonClassLinkageSmokeCli.class.getClassLoader())) {
            for (String className : classNames) {
                Class.forName(className, false, addonLoader);
            }
        }
        System.out.println("Linked " + classNames.size() + " addon class(es) through an isolated classloader.");
    }
}
