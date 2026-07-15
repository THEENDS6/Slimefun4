package cc.theends6.sfx.api.localization;

import java.util.List;


public interface SfxLocalizationView {
    String requiredText(String key);

    List<String> requiredList(String key);
}
