package com.getsolace.ai.aidl;

oneway interface ISingBoxServiceCallback {
    void onStateChanged(int state, String activeLabel, String lastError, boolean manuallyStopped);
    void onUrlTestNodeDelayResult(long requestId, int delay);
}
