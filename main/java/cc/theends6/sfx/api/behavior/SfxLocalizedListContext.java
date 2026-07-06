package cc.theends6.sfx.api.behavior;

import java.util.List;
import java.util.Map;

public interface SfxLocalizedListContext {
    String path();

    List<String> rawList(String path);

    String rawText(String path);

    String applyPlaceholders(String value, Map<String, String> placeholders);
}
