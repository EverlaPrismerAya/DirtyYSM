package com.elfmcys.yesstevemodel.config;

public enum ModelLoadingMode {
    FULL, PAGE, POTATO;
    public enum Request { PRELOAD, PAGE, USE }
    public boolean allows(Request request) {
        return request == Request.USE || this == FULL || (this == PAGE && request == Request.PAGE);
    }
    public boolean downloadsOnPage() { return allows(Request.PAGE); }
}
