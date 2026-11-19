package com.elfmcys.yesstevemodel.client;

import com.elfmcys.yesstevemodel.model.LocalRoamingStore;
import com.elfmcys.yesstevemodel.model.ServerModelManager;

public final class ClientRoamingState {
    public static final LocalRoamingStore STORE=new LocalRoamingStore(ServerModelManager.FOLDER.resolve("client_state/roaming"));
    private ClientRoamingState() {}
}
