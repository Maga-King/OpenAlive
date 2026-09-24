package org.aliveclean;

import org.json.JSONObject;

/** An editor-local draft. The ColorOS Apply/Cancel actions own persistent settings. */
final class NativeClockEditSession {
    interface Host {
        String read() throws Exception;
        void write(String config) throws Exception;
    }
    private final Host host;
    private final String original;
    private boolean closed;

    NativeClockEditSession(Host host) throws Exception {
        this.host=host;
        original=host.read();
        validate(original);
    }

    void select(String packageName, String clockConfig) throws Exception {
        if(closed)throw new IllegalStateException("Clock edit session is closed");
        if(packageName==null||packageName.isEmpty())throw new IllegalArgumentException("Clock package missing");
        new JSONObject(clockConfig);
        String previous=host.read();
        JSONObject current=validate(previous);
        // Carry the current widget and base-UI choices through a clock switch.
        current.put("pkg",packageName);
        current.put("clockStyleConfig",clockConfig);
        try {
            host.write(current.toString());
            JSONObject actual=validate(host.read());
            if(!packageName.equals(actual.getString("pkg")))throw new IllegalStateException("Clock was not accepted by native host");
            if(NativeClockProvider.contains(packageName)
                    &&!same(new JSONObject(clockConfig),new JSONObject(actual.getString("clockStyleConfig"))))
                throw new IllegalStateException("Clock configuration was not accepted by native host");
        } catch(Exception error) {
            try {host.write(previous);} catch(Exception rollback){error.addSuppressed(rollback);}
            throw error;
        }
    }

    void cancel() throws Exception {
        if(closed)return;
        host.write(original);
        closed=true;
    }
    void accept(){closed=true;}
    private static boolean same(Object first,Object second)throws Exception{
        if(first instanceof JSONObject&&second instanceof JSONObject){
            JSONObject a=(JSONObject)first,b=(JSONObject)second;
            if(a.length()!=b.length())return false;
            java.util.Iterator<String> keys=a.keys();
            while(keys.hasNext()){String key=keys.next();if(!b.has(key)||!same(a.get(key),b.get(key)))return false;}
            return true;
        }
        if(first instanceof org.json.JSONArray&&second instanceof org.json.JSONArray){
            org.json.JSONArray a=(org.json.JSONArray)first,b=(org.json.JSONArray)second;
            if(a.length()!=b.length())return false;
            for(int i=0;i<a.length();i++)if(!same(a.get(i),b.get(i)))return false;
            return true;
        }
        return java.util.Objects.equals(first,second);
    }
    private static JSONObject validate(String json) throws Exception {
        JSONObject value=new JSONObject(json);
        if(value.getString("pkg").isEmpty())throw new IllegalArgumentException("Empty native clock package");
        value.getString("clockStyleConfig");
        return value;
    }
}
