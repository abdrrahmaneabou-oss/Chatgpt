package com.pixeltrigger.app.input;

interface IShizukuInputService {
    int getBackendUid();
    int probeCapability();
    String getCapabilityDetail();
    int injectTap(long triggerId, float x, float y, long requestedDurationMs, int displayId);
    long getLastDownNs();
    long getLastUpNs();
    void destroy() = 16777114;
}
