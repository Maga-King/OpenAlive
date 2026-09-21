package org.aliveclean;

import android.icu.util.ChineseCalendar;
import android.icu.util.Calendar;
import android.icu.util.TimeZone;
import java.util.Locale;

/** Calendar calculation uses the platform ICU data and the user's current time zone. */
final class AodCalendar {
    static String lunar(long now,String zone){
        ChineseCalendar c=new ChineseCalendar(TimeZone.getTimeZone(zone),Locale.CHINA);
        c.setTimeInMillis(now);
        int year=c.get(Calendar.YEAR)-1,month=c.get(Calendar.MONTH),day=c.get(Calendar.DATE);
        String stem="甲乙丙丁戊己庚辛壬癸",branch="子丑寅卯辰巳午未申酉戌亥";
        String[] months={"正","二","三","四","五","六","七","八","九","十","冬","腊"};
        String digits="一二三四五六七八九";
        String d=day==10?"初十":day==20?"二十":day==30?"三十":(day<10?"初":day<20?"十":"廿")+digits.charAt((day-1)%10);
        return ""+stem.charAt(year%10)+branch.charAt(year%12)+"年"+(c.get(Calendar.IS_LEAP_MONTH)==1?"闰":"")+months[month]+"月"+d;
    }
}
