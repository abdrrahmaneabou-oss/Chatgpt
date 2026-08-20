package com.pixeltrigger.app.input;

interface IShizukuInputService {
    int getBackendUid() = 1;
    int probeCapability() = 2;
    String getCapabilityDetail() = 3;
    oneway void injectTapFast(
        long triggerId,
        float x,
        float y,
        int displayId,
        long frameTimestampNs,
        long captureCallbackNs,
        long sampleStartNs,
        long sampleEndNs,
        long detectionStartNs,
        long fireDecisionNs,
        long requestCreatedNs,
        long binderSubmitStartNs,
        long samplerEntryGapNs,
        long imageTimestampGapNs
    ) = 4;
    long getLastDownNs() = 5;
    long getLastUpNs() = 6;
    String getLatencyDetail() = 7;
    String getLatencyTraceReport() = 8;
    void clearLatencyTraceHistory() = 9;
    void destroy() = 16777114;
}
