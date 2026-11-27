package com.shatteredpixel.shatteredpixeldungeon.windows;

import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.watabou.noosa.ColorBlock;
import com.watabou.noosa.Game;
import com.watabou.noosa.Image;
import com.watabou.utils.Point;
import com.watabou.utils.PointF;

/** Presentation only: never moves actors, rolls damage, or advances game time. */
public class WndFurioso extends Window {
    public static final float ENTER=.45f, STRIKES=2.45f, HOLD=.18f, RETURN=.5f;
    public static final float IMPACT=ENTER+STRIKES, DURATION=IMPACT+HOLD+RETURN;
    // Alternating lunges, a short pause, then the final crossing slash.
    private static final float[] TIMES={0,.14f,.30f,.47f,.66f,.84f,1.05f,1.25f,1.49f,1.72f,1.98f,2.20f,2.45f};
    private static final float[] OFFSETS={-1,-.2f,.42f,-.32f,.38f,-.35f,.34f,-.28f,.4f,-.35f,-.85f,-.85f,.55f};
    public static float attackOffset(float time){
        for(int i=1;i<TIMES.length;i++)if(time<=TIMES[i]){
            float p=Math.max(0,(time-TIMES[i-1])/(TIMES[i]-TIMES[i-1]));
            // Hold briefly, then accelerate into each impact.
            p=smooth(Math.min(1,p/.55f));
            return OFFSETS[i-1]+(OFFSETS[i]-OFFSETS[i-1])*p;
        }
        return OFFSETS[OFFSETS.length-1];
    }
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
    private boolean applied,finished;

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
        add(player);add(target);
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
        float tx=width*.64f-target.width*targetStageScale/2;
        float ty=floor-target.height*targetStageScale;
        float offset=attackOffset(Math.max(0,Math.min(STRIKES,time-ENTER)));
        float px=width*.64f+offset*width*.32f-player.width*stageScale/2;
        float py=floor-player.height*stageScale;
        place(player,playerStart.x+(px-playerStart.x)*blend,playerStart.y+(py-playerStart.y)*blend,playerScale+(stageScale-playerScale)*blend);
        place(target,targetStart.x+(tx-targetStart.x)*blend,targetStart.y+(ty-targetStart.y)*blend,targetScale+(targetStageScale-targetScale)*blend);
        player.flipHorizontal=px>tx;player.frame(player.frame());
        target.flipHorizontal=false;target.frame(target.frame());
        if(applied && !enemy.isAlive())target.alpha(1-back);
    }
    @Override public void update(){
        super.update();
        elapsed+=Game.elapsed;
        pose(elapsed);
        if(!applied && elapsed>=IMPACT){applied=true;impact.run();}
        if(elapsed>=DURATION)hide();
    }
    @Override public void onBackPressed(){} // A combat action cannot be skipped by back/tap.
    @Override public void destroy(){
        if(!finished){
            finished=true;
            hero.sprite.visible=playerVisible;enemy.sprite.visible=targetVisible;
            if(applied)complete.run();else cancel.run();
        }
        super.destroy();
    }
}
