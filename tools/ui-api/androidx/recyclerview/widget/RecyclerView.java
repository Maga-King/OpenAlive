package androidx.recyclerview.widget;

/** Compile-only signatures. The APK runs the unchanged ROM implementation. */
public abstract class RecyclerView extends android.view.ViewGroup {
    public RecyclerView(android.content.Context c){super(c);}
    public abstract void setAdapter(Adapter adapter);
    public abstract void setLayoutManager(LayoutManager manager);
    public abstract void setItemAnimator(ItemAnimator animator);
    public static abstract class ItemAnimator {}
    public static abstract class LayoutManager {}
    public static abstract class ViewHolder {
        public final android.view.View itemView;
        public ViewHolder(android.view.View view){itemView=view;}
    }
    public static abstract class Adapter<VH extends ViewHolder> {
        public abstract VH onCreateViewHolder(android.view.ViewGroup parent,int type);
        public abstract void onBindViewHolder(VH holder,int position);
        public abstract int getItemCount();
        public final void notifyDataSetChanged(){}
    }
}
