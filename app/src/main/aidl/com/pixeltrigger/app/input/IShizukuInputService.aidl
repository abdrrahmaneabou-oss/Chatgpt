package com.pixeltrigger.app.input;

interface IShizukuInputService {
    int getBackendUid() = 1;
    int probeCapability() = 2;
    String getCapabilityDetail() = 3;
    int injectTap(long triggerId, float x, float y, long requestedDurationMs, int displayId) = 4;
    long getLastDownNs() = 5;
    long getLastUpNs() = 6;
    void destroy() = 16777114;
}
