package cc.theends6.sfx.internal.util;

import java.lang.reflect.Method;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class HeadTextures {
    private HeadTextures() {
    }

    public static void apply(ItemMeta meta, String textureHash) {
        if (!(meta instanceof SkullMeta skullMeta) || textureHash == null || textureHash.isBlank()) {
            return;
        }
        try {
            Object profile = createProfile(textureHash);
            if (profile == null) {
                return;
            }
            Object textures = invoke(profile, "getTextures");
            if (textures != null) {
                invokeBest(textures, "setSkin", new URL("https://textures.minecraft.net/texture/" + textureHash));
                invokeBest(profile, "setTextures", textures);
            }
            if (!invokeBest(skullMeta, "setPlayerProfile", profile)) {
                invokeBest(skullMeta, "setOwnerProfile", profile);
            }
        } catch (Throwable ignored) {
            
        }
    }

    private static Object createProfile(String textureHash) {
        UUID uuid = UUID.nameUUIDFromBytes(textureHash.getBytes(StandardCharsets.UTF_8));
        Object profile = invokeStatic(Bukkit.class, "createPlayerProfile", uuid, "SFX-" + textureHash.substring(0, Math.min(12, textureHash.length())));
        if (profile != null) {
            return profile;
        }
        return invokeStatic(Bukkit.class, "createProfile", uuid, "SFX-" + textureHash.substring(0, Math.min(12, textureHash.length())));
    }

    private static Object invokeStatic(Class<?> owner, String method, Object... args) {
        try {
            Method matched = findMethod(owner, method, args);
            if (matched == null) {
                return null;
            }
            return matched.invoke(null, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object invoke(Object target, String method, Object... args) {
        try {
            Method matched = findMethod(target.getClass(), method, args);
            if (matched == null) {
                return null;
            }
            return matched.invoke(target, args);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static boolean invokeBest(Object target, String method, Object... args) {
        return invoke(target, method, args) != null || findMethod(target.getClass(), method, args) != null;
    }

    private static Method findMethod(Class<?> owner, String name, Object... args) {
        for (Method method : owner.getMethods()) {
            if (!method.getName().equals(name)) {
                continue;
            }
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (parameterTypes.length != args.length) {
                continue;
            }
            boolean matches = true;
            for (int i = 0; i < parameterTypes.length; i++) {
                if (args[i] == null) {
                    continue;
                }
                if (!wrap(parameterTypes[i]).isAssignableFrom(args[i].getClass())) {
                    matches = false;
                    break;
                }
            }
            if (matches) {
                method.setAccessible(true);
                return method;
            }
        }
        return null;
    }

    private static Class<?> wrap(Class<?> type) {
        if (!type.isPrimitive()) {
            return type;
        }
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == short.class) return Short.class;
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == float.class) return Float.class;
        if (type == double.class) return Double.class;
        return Void.class;
    }
}
