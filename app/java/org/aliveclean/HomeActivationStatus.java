package org.aliveclean;

import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.*;
import android.view.*;
import android.widget.*;

/** Small asynchronous Root/module status row; never consults installed managers. */
final class HomeActivationStatus {
    private final Activity activity;
    private final TextView moduleLabel,rootLabel;
    private final GradientDrawable moduleDot=new GradientDrawable(),rootDot=new GradientDrawable();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService queries=java.util.concurrent.Executors.newSingleThreadExecutor(task->{
        Thread thread=new Thread(task,"OpenAliveModuleStatus");thread.setDaemon(true);return thread;
    });
    private int generation;
    private long moduleDeadline;
    private volatile boolean started,closed;
    private boolean queryInFlight,requestModule,moduleKnown,module;
    private RootAuthorization.Status root;
    private RootAuthorization.Registration rootWatch;
    private final Runnable refresh=this::queryModule;

    HomeActivationStatus(Activity activity,Look look,ViewGroup parent){
        this.activity=activity;
        LinearLayout row=new LinearLayout(activity);row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);row.setPadding(dp(16),dp(18),dp(16),dp(4));
        moduleLabel=indicator(row,look,moduleDot,"LSP 检测中");
        View gap=new View(activity);row.addView(gap,new LinearLayout.LayoutParams(dp(8),1));
        rootLabel=indicator(row,look,rootDot,"ROOT 检测中");
        row.setOnClickListener(v->start(true));row.setContentDescription("重新检测模块和 Root 状态");
        parent.addView(row,new LinearLayout.LayoutParams(-1,-2));
    }
    private TextView indicator(LinearLayout row,Look look,GradientDrawable dot,String text){
        LinearLayout pill=new LinearLayout(activity);pill.setOrientation(LinearLayout.HORIZONTAL);pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setBackground(look.round(look.surface,16));pill.setPadding(dp(12),dp(7),dp(14),dp(7));
        View light=new View(activity);dot.setShape(GradientDrawable.OVAL);dot.setColor(0xff95999f);light.setBackground(dot);
        pill.addView(light,new LinearLayout.LayoutParams(dp(7),dp(7)));
        TextView label=look.text(text,12.5f,look.muted,false);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-2,-2);lp.setMarginStart(dp(8));pill.addView(label,lp);
        row.addView(pill,new LinearLayout.LayoutParams(-2,-2));return label;
    }

    void start(){start(false);}
    private void start(boolean retry){
        if(closed)return;
        started=true;
        main.removeCallbacks(refresh);
        moduleDeadline=SystemClock.uptimeMillis()+1500;
        requestModule=true;moduleKnown=false;module=false;
        main.post(refresh);
        if(rootWatch!=null)return;
        root=null;show();
        int request=generation;
        rootWatch=RootAuthorization.watch(retry,result->{
            if(!started||closed||activity.isDestroyed()||request!=generation)return;
            rootWatch=null;root=result;show();
        });
    }
    void stop(){
        started=false;generation++;main.removeCallbacks(refresh);
        // A Root permission dialog may itself pause this activity. Detach only
        // the UI listener; the process-wide request finishes within 15 seconds.
        if(rootWatch!=null){rootWatch.close();rootWatch=null;}
    }
    void close(){stop();closed=true;queries.shutdownNow();}

    private void queryModule(){
        if(!started||closed||activity.isDestroyed()||queryInFlight)return;
        queryInFlight=true;
        int request=generation;boolean announce=requestModule;requestModule=false;
        Context app=activity.getApplicationContext();
        queries.execute(()->{
            boolean active=false;
            if(started&&!closed){
                if(announce)ModuleActivation.request(app);
                active=ModuleActivation.active(app);
            }
            final boolean current=active;
            main.post(()->{
                queryInFlight=false;
                if(!started||closed||activity.isDestroyed())return;
                if(request==generation){module=current;moduleKnown=true;show();}
                // Schedule only after the preceding query finishes. A slow
                // provider cannot accumulate periodic tasks or block the UI.
                long delay=request!=generation||requestModule?0:SystemClock.uptimeMillis()<moduleDeadline?300:2000;
                main.removeCallbacks(refresh);main.postDelayed(refresh,delay);
            });
        });
    }

    private void show(){
        if(!started||closed)return;
        boolean modulePending=!moduleKnown||(!module&&SystemClock.uptimeMillis()<moduleDeadline);
        moduleDot.setColor(modulePending?0xff95999f:module?0xff35ba79:0xffee5c5c);
        moduleLabel.setText(modulePending?"LSP 检测中":module?"LSP 已激活":"LSP 未激活");
        boolean granted=root==RootAuthorization.Status.GRANTED;
        rootDot.setColor(root==null?0xff95999f:granted?0xff35ba79:0xffee5c5c);
        rootLabel.setText(root==null?"ROOT 检测中":granted?"ROOT 已授权":"ROOT 未授权");
        rootLabel.setContentDescription(root==null?"ROOT 检测中":root.text);
    }

    private int dp(int value){return Math.round(value*activity.getResources().getDisplayMetrics().density);}
}
