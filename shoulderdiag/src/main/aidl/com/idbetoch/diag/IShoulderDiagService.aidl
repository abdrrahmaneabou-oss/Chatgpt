package com.idbetoch.diag;

interface IShoulderDiagService {
    int getBackendUid();
    boolean isBusy();
    String getSessionState();
    String getResult();
    void clearResult();
    void stopActiveSession();

    // Goal-oriented first capture: collect only the evidence needed to reproduce
    // physical REDMAGIC shoulder R/L behavior for PixelTrigger's left half.
    void startFirstCapture(int prepareSeconds, int captureSeconds);

    // Advanced interactive Shizuku shell. The engine waits prepareSeconds first,
    // then runs the script for up to captureSeconds (0 = let the command finish).
    void runScript(String script, int prepareSeconds, int captureSeconds);

    // App-window KeyEvents are appended only while a session is active.
    void appendAppKeyEvent(String eventLine);
}
