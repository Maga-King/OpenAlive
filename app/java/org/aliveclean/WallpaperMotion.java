package org.aliveclean;

interface WallpaperMotion {
    void change(int scene,boolean animate);
    void advance(long time,float speed);
    void follow(float x,float y);
    void pause();
    boolean active();
}
