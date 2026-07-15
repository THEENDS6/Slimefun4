package cc.theends6.sfx.internal.topology;

import cc.theends6.sfx.api.block.SfxBlockInstanceRecord;

public interface SfxTopologyDomainPolicy {
    SfxTopologyDomainKey domain();

    SfxTopologyCapabilities capabilities(SfxBlockInstanceRecord instance);

    default SfxTopologyStatus evaluateStatus(SfxTopologyComponent component) {
        int controllers = component.controllers().size();
        if (controllers <= 0) {
            return SfxTopologyStatus.INACTIVE;
        }
        return controllers == 1 ? SfxTopologyStatus.ONLINE : SfxTopologyStatus.MULTIPLE_CONTROLLERS;
    }
}
