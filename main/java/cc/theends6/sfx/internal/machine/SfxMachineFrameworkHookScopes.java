package cc.theends6.sfx.internal.machine;

import java.util.LinkedHashMap;
import java.util.Map;


public final class SfxMachineFrameworkHookScopes {
    public static final String DOMAIN = "framework.domain";
    public static final String ACTION = "framework.action";
    public static final String SOURCE = "framework.source";
    public static final String LEGACY_PATH = "framework.legacy-path";
    public static final String MENU_ACTION = "framework.menu.action";
    public static final String WORLD_MUTATION = "framework.world.mutation";
    public static final String TRANSFER_KIND = "framework.transfer.kind";
    public static final String NETWORK_DOMAIN = "framework.network.domain";

    private SfxMachineFrameworkHookScopes() {}

    public static Map<String, Object> attributes(String domain, String action, String source) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (domain != null) attributes.put(DOMAIN, domain);
        if (action != null) attributes.put(ACTION, action);
        if (source != null) attributes.put(SOURCE, source);
        attributes.put(LEGACY_PATH, Boolean.TRUE);
        return attributes;
    }
}
