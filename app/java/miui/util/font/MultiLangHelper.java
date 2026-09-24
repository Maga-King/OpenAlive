package miui.util.font;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Font-file service for the isolated original clock runtime on other vendors. */
public final class MultiLangHelper {
    private static volatile String fontPath;
    public static void configure(String path){
        if(path==null||path.isEmpty())throw new IllegalArgumentException("Original font path required");
        fontPath=path;
    }
    public static String getMiuiFontPathByLocale(Locale locale){return fontPath;}
    public static List<String> getMiproFileList(Locale locale){
        String path=fontPath;return path==null?Collections.emptyList():Collections.singletonList(path);
    }
    public static String getLangByMiuiFontPath(String path){return null;}
    private MultiLangHelper(){}
}
