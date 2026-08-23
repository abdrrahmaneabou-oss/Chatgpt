package com.idbetoch.diag;

interface IShoulderDiagService {
    int getBackendUid();
    void startScan(int seconds);
    boolean isRunning();
    String getResult();
    void clearResult();
    void appendAppKeyEvent(String eventLine);
}
