package org.aliveclean;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** One immutable presentation sample while its exact RGB mask is being decoded. */
final class FrameSample {
    final ByteBuffer effect=ByteBuffer.allocateDirect(36*4).order(ByteOrder.nativeOrder());
    final ByteBuffer base=ByteBuffer.allocateDirect(SceneMotion.COUNT*4).order(ByteOrder.nativeOrder());
    final ByteBuffer textures=ByteBuffer.allocateDirect(20*4).order(ByteOrder.nativeOrder());
    int mask;
    boolean pending,expanded;
    long capturedAt;
    void capture(FrameMotion frame,SceneMotion common,long now){
        if(pending)return;
        frame.ratios(common);
        effect.clear();effect.put(frame.packet());effect.flip();
        base.clear();base.put(common.packet());base.flip();
        mask=frame.style.index((long)frame.values[6]);capturedAt=now;expanded=frame.expanded();pending=true;
    }
    void clear(){pending=false;}
    void captureTextures(TextureMotion motion){textures.clear();textures.put(motion.packet());textures.flip();}
}
