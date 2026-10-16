package android.os;
import java.util.ArrayList;
public final class Handler {
    private static final class Task {final Runnable action;final long due;Task(Runnable r,long t){action=r;due=t;}}
    private final ArrayList<Task> tasks=new ArrayList<>();
    public boolean accepting=true;
    public boolean throwOnPost;
    public boolean post(Runnable action){return postDelayed(action,0);}
    public boolean postDelayed(Runnable action,long delay){if(throwOnPost)throw new IllegalStateException("queue failure");if(!accepting)return false;tasks.add(new Task(action,SystemClock.now+delay));return true;}
    public void removeCallbacks(Runnable action){tasks.removeIf(task->task.action==action);}
    public int queued(){return tasks.size();}
    public void runReady(){
        for(int loops=0;loops<1000;loops++){
            Task next=null;for(Task task:tasks)if(task.due<=SystemClock.now&&(next==null||task.due<next.due))next=task;
            if(next==null)return;tasks.remove(next);next.action.run();
        }
        throw new AssertionError("Handler loop did not settle");
    }
    public void advance(long ms){SystemClock.now+=ms;runReady();}
}
