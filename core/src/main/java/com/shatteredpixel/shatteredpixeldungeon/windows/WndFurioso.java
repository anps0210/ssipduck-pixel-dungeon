package com.shatteredpixel.shatteredpixeldungeon.windows;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Game;
import com.watabou.noosa.Image;
import com.watabou.noosa.audio.Sample;
import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.watabou.utils.Point;
import com.watabou.utils.PointF;

/** Presentation only: never moves actors, rolls damage, or advances game time. */
public class WndFurioso extends Window {
    public static final float ENTER=.45f, STRIKES=10.47f, RETURN=.5f;
    public static final float SOUND_DURATION=13.696f;
    public static final float HOLD=SOUND_DURATION-STRIKES-RETURN;
    // Original choreography cues mapped to transients in the supplied audio (seconds).
    private static final float[][] AUDIO_CUES={
        {0,0},{.15f,.14f},{.21f,.20f},{.39f,.65f},{.45f,.71f},{.62f,1.23f},
        {.73f,1.34f},{.85f,1.90f},{.92f,1.96f},{1.02f,2.08f},
        {1.19f,2.55f},{1.33f,2.78f},{1.39f,2.84f},{1.50f,3.20f},
        {1.59f,3.30f},{1.73f,3.58f},{1.82f,3.68f},{1.96f,4.07f},
        {2.05f,4.17f},{2.19f,4.86f},{2.25f,4.92f},{2.35f,5.04f},
        {2.43f,5.59f},{2.54f,5.73f},{2.70f,6.10f},{2.79f,6.22f},{2.94f,6.38f},
        {3.00f,7.04f},{3.08f,7.12f},{3.44f,7.75f},{3.53f,7.84f},{3.73f,8.05f},
        {3.90f,8.70f},{4.00f,8.82f},{4.12f,9.34f},{4.23f,9.45f},{4.39f,9.62f},
        {5.04f,10.10f},{5.38f,10.34f},{5.60f,10.47f}
    };
    public static float audioTime(float cue){
        if(cue<=0)return 0;
        for(int i=1;i<AUDIO_CUES.length;i++)if(cue<=AUDIO_CUES[i][0]){
            float p=(cue-AUDIO_CUES[i-1][0])/(AUDIO_CUES[i][0]-AUDIO_CUES[i-1][0]);
            return AUDIO_CUES[i-1][1]+p*(AUDIO_CUES[i][1]-AUDIO_CUES[i-1][1]);
        }
        return STRIKES;
    }
    public static final float IMPACT=ENTER+STRIKES, DURATION=IMPACT+HOLD+RETURN;
    // time, Roland x/y, target x/y. Coordinates are presentation-only stage units.
    // Repeated positions deliberately hold a pose; short intervals are fast dashes.
    private static final float[][] MOTION={
        // 1. Two ranged hits: Roland stays back, target is knocked away twice.
        {0,-.95f,0,.25f,0}, {.15f,-.95f,0,.25f,0},
        {.21f,-.95f,0,.37f,0}, {.39f,-.95f,0,.37f,0},
        {.45f,-.95f,0,.49f,0}, {.62f,-.95f,0,.49f,0},
        // 2. Dash straight through.
        {.73f,.86f,0,.49f,0}, {.85f,.86f,0,.49f,0},
        // 3. One stationary hit (only target recoils).
        {.92f,.86f,0,.45f,0}, {1.02f,.86f,0,.49f,0},
        // 4. Approach, stop, then attack.
        {1.19f,.69f,0,.49f,0}, {1.33f,.69f,0,.49f,0},
        {1.39f,.69f,0,.46f,0}, {1.50f,.69f,0,.49f,0},
        // 5. Three fast crossings, with readable pauses between them.
        {1.59f,.02f,0,.49f,0}, {1.73f,.02f,0,.49f,0},
        {1.82f,.86f,0,.49f,0}, {1.96f,.86f,0,.49f,0},
        {2.05f,.02f,0,.49f,0}, {2.19f,.02f,0,.49f,0},
        // 6. Two attacks from the same position.
        {2.25f,.02f,0,.53f,0}, {2.35f,.02f,0,.49f,0},
        {2.43f,.02f,0,.54f,0}, {2.54f,.02f,0,.49f,0},
        // 7. Lift and drop: body motion only, no weapon/effect.
        {2.70f,.13f,-.24f,.49f,0}, {2.79f,.13f,.05f,.49f,.04f},
        {2.94f,.13f,0,.49f,0},
        // 8. One horizontal flash-through, then hold on the opposite side.
        {3.00f,.13f,0,.49f,0}, {3.08f,.90f,0,.49f,0},
        {3.44f,.90f,0,.49f,0},
        // 9. Shotgun: large knockback away from Roland, who stands on the right.
        {3.53f,.90f,0,-.18f,0}, {3.73f,.90f,0,-.18f,0},
        // 10. Chase and strike down.
        {3.90f,.06f,-.20f,-.18f,0}, {4.00f,.06f,.04f,-.18f,.03f},
        {4.12f,.06f,0,-.18f,0},
        // 11. Fast rising cross to the other side.
        {4.23f,-.48f,-.22f,-.18f,0}, {4.39f,-.48f,0,-.18f,0},
        // 12. Pause, wind up, then a large downward movement.
        {5.04f,-.48f,0,-.18f,0}, {5.38f,-.40f,-.32f,-.18f,0},
        {5.60f,-.40f,.06f,-.18f,.04f}
    };
    static {for(float[] frame:MOTION)frame[0]=audioTime(frame[0]);}
    /** component: 0=Roland x, 1=Roland y, 2=target x, 3=target y. */
    public static float motion(float time,int component){return motion(time,component,.55f);}
    public static float motion(float time,int component,float minimumGap){
        if(component<0 || component>3)throw new IllegalArgumentException();
        int column=component+1;
        if(time<=0)return keyframe(0,column,minimumGap);
        for(int i=1;i<MOTION.length;i++)if(time<=MOTION[i][0]){
            float p=smooth((time-MOTION[i-1][0])/(MOTION[i][0]-MOTION[i-1][0]));
            return keyframe(i-1,column,minimumGap)+(keyframe(i,column,minimumGap)-keyframe(i-1,column,minimumGap))*p;
        }
        return keyframe(MOTION.length-1,column,minimumGap);
    }
    private static float keyframe(int index,int column,float minimumGap){
        float[] frame=MOTION[index];
        if(column!=1 || frame[0]<1.34f)return frame[column];
        // Use a stable anchor so target recoil doesn't drag Roland along with it.
        float anchor=frame[0]<=8.05f?.49f:-.18f;
        float delta=frame[1]-anchor;
        float distance=minimumGap+Math.max(0,Math.abs(delta)-.20f)*.4f;
        return anchor+Math.copySign(distance,delta);
    }
    public static float attackOffset(float time){return motion(time,0);}
    private static float smooth(float x){return x*x*(3-2*x);}
    private final Hero hero;
    private final Char enemy;
    private final Image player, target;
    private final ColorBlock dim;
    private final PointF playerStart, targetStart;
    private final float playerScale,targetScale,stageScale,targetStageScale;
    private final boolean playerVisible,targetVisible;
    private final Runnable impact,complete,cancel;
    private float elapsed;
    private boolean applied,finished,soundStarted;
    private long soundId=-1;

    public WndFurioso(Hero hero,Char enemy,Runnable impact,Runnable complete,Runnable cancel){
        super((int)PixelScene.uiCamera.width,(int)PixelScene.uiCamera.height);
        this.hero=hero;this.enemy=enemy;this.impact=impact;this.complete=complete;this.cancel=cancel;
        chrome.visible=false;shadow.visible=false;
        dim=new ColorBlock(width,height,0xFF000000);dim.alpha(0);add(dim);
        player=new Image();player.copy(hero.sprite);player.scale=new PointF(1,1);
        target=new Image();target.copy(enemy.sprite);target.scale=new PointF(1,1);
        playerStart=screenPosition(hero.sprite);targetStart=screenPosition(enemy.sprite);
        playerScale=hero.sprite.scale.x*hero.sprite.camera().zoom/camera.zoom;
        targetScale=enemy.sprite.scale.x*enemy.sprite.camera().zoom/camera.zoom;
        stageScale=Math.min(3f,width/90f);
        targetStageScale=Math.min(stageScale,Math.min(width*.28f/target.width,height*.3f/target.height));
        playerVisible=hero.sprite.visible;targetVisible=enemy.sprite.visible;
        hero.sprite.visible=false;enemy.sprite.visible=false;
        // Draw Roland last, including when passing through a large enemy.
        add(target);add(player);
        pose(0);
    }
    private PointF screenPosition(Image image){
        Point screen=image.camera().cameraToScreen(image.x,image.y);
        return camera.screenToCamera(screen.x,screen.y);
    }
    private void place(Image image,float x,float y,float scale){
        image.scale.set(scale);image.x=x;image.y=y;
    }
    private void pose(float time){
        float enter=smooth(Math.min(1,time/ENTER));
        float back=smooth(Math.max(0,Math.min(1,(time-IMPACT-HOLD)/RETURN)));
        float blend=enter*(1-back);
        dim.alpha(.86f*blend);
        float floor=height*.57f;
        float phase=Math.max(0,Math.min(STRIKES,time-ENTER));
        float span=width*.28f;
        // Keep a visible gap between sprite edges at attack endpoints.
        // Interpolate widened keyframes, rather than clamping mid-dash positions.
        float minimumGap=Math.max(.55f,(player.width*stageScale/2+target.width*targetStageScale/2+6f)/span);
        float playerCenter=width*.5f+motion(phase,0,minimumGap)*span;
        float targetCenter=width*.5f+motion(phase,2)*span;
        float tx=targetCenter-target.width*targetStageScale/2;
        float ty=floor-target.height*targetStageScale+motion(phase,3)*span;
        float px=playerCenter-player.width*stageScale/2;
        float py=floor-player.height*stageScale+motion(phase,1)*span;
        place(player,playerStart.x+(px-playerStart.x)*blend,playerStart.y+(py-playerStart.y)*blend,playerScale+(stageScale-playerScale)*blend);
        place(target,targetStart.x+(tx-targetStart.x)*blend,targetStart.y+(ty-targetStart.y)*blend,targetScale+(targetStageScale-targetScale)*blend);
        player.flipHorizontal=playerCenter>targetCenter;player.frame(player.frame());
        target.flipHorizontal=targetCenter>playerCenter;target.frame(target.frame());
        if(applied && !enemy.isAlive())target.alpha(1-back);
    }
    @Override public void update(){
        super.update();
        elapsed+=Game.elapsed;
        if(!soundStarted && elapsed>=ENTER){
            soundStarted=true;
            // Start animation time zero on the same frame as audio playback.
            elapsed=ENTER;
            soundId=Sample.INSTANCE.play(Assets.Sounds.FURIOSO);
        }
        pose(elapsed);
        if(!applied && elapsed>=IMPACT){applied=true;impact.run();}
        if(elapsed>=DURATION)hide();
    }
    @Override public void onBackPressed(){} // A combat action cannot be skipped by back/tap.
    @Override public void destroy(){
        if(!finished){
            finished=true;
            Sample.INSTANCE.stop(Assets.Sounds.FURIOSO,soundId);
            hero.sprite.visible=playerVisible;enemy.sprite.visible=targetVisible;
            if(applied)complete.run();else cancel.run();
        }
        super.destroy();
    }
}
