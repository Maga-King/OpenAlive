package com.oplus.app;

import android.os.IBinder;

/** Test-only API fixture. Deliberately uses different transaction IDs. */
public interface IOplusProtectConnection {
    String DESCRIPTOR="com.oplus.app.IOplusProtectConnection";
    final class Stub {
        public static IBinder asInterface(IBinder binder){return binder;}
        public static String getDefaultTransactionName(int code){
            return code==41?"onSuccess":code==42?"onError":code==43?"onTimeout":null;
        }
    }
}
