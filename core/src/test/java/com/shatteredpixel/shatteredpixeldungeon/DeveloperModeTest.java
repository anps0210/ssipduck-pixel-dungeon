package com.shatteredpixel.shatteredpixeldungeon;

import com.badlogic.gdx.Preferences;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.watabou.utils.GameSettings;
import org.junit.Test;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.Map;
import static org.junit.Assert.*;

public class DeveloperModeTest {
    @Test public void defaultsOffAndProtectsDamageAndDeathOnlyWhenEnabled() {
        Map<String,Object> values=new HashMap<>();
        Preferences prefs=(Preferences)Proxy.newProxyInstance(Preferences.class.getClassLoader(),new Class[]{Preferences.class},(p,m,a)->{
            if (m.getName().startsWith("put")) { values.put((String)a[0],a[1]); return p; }
            if (m.getName().equals("contains")) return values.containsKey(a[0]);
            if (m.getName().startsWith("get") && a!=null && a.length==2) return values.getOrDefault(a[0],a[1]);
            return null;
        });
        GameSettings.set(prefs);
        try {
            assertFalse(SPDSettings.developerMode());
            SPDSettings.developerMode(true);
            Hero hero=new Hero(); hero.HP=13;
            hero.damage(999,new Object()); assertEquals(13,hero.HP);
            hero.die(new Object()); assertEquals(13,hero.HP);
            SPDSettings.developerMode(false); assertFalse(SPDSettings.developerMode());
        } finally { GameSettings.set(null); Dungeon.hero=null; }
    }

    @Test public void floorSelectionRejectsInvalidFloors(){
        assertEquals(1,com.shatteredpixel.shatteredpixeldungeon.windows.WndDeveloper.parseFloor("1"));
        assertEquals(26,com.shatteredpixel.shatteredpixeldungeon.windows.WndDeveloper.parseFloor(" 26 "));
        for(String invalid:new String[]{"0","27","-1","abc","1.5",""}){
            try {com.shatteredpixel.shatteredpixeldungeon.windows.WndDeveloper.parseFloor(invalid);fail(invalid);}
            catch(IllegalArgumentException expected){}
        }
    }
    @Test public void developerCatalogIncludesBothTestingItems(){
        java.util.List<Class<?>> catalog=java.util.Arrays.asList(com.shatteredpixel.shatteredpixeldungeon.utils.DeveloperCatalog.ITEMS);
        assertTrue(catalog.contains(com.shatteredpixel.shatteredpixeldungeon.items.TengusMask.class));
        assertTrue(catalog.contains(com.shatteredpixel.shatteredpixeldungeon.items.FuriosoCharge.class));
    }
    @Test public void furiosoMotionCrossesTargetAndFinishesBeforeReturn(){
        float low=0,high=0;
        for(float t=0;t<2.5f;t+=.01f){
            float x=com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.attackOffset(t);
            assertTrue(Float.isFinite(x));low=Math.min(low,x);high=Math.max(high,x);
        }
        assertTrue(low<-.8f);assertTrue(high>.4f);
        assertTrue(com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.IMPACT
            <com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.DURATION);
    }

    private float motion(float t,int axis){return com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.motion(com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.audioTime(t),axis);}
    @Test public void revisedFuriosoHasTwoRangedKnockbacksThenThreeCrossings(){
        assertEquals(motion(0,0),motion(.62f,0),0);
        assertTrue(motion(.21f,2)>motion(0,2));
        assertTrue(motion(.45f,2)>motion(.21f,2));
        assertTrue(motion(.73f,0)>motion(.73f,2));
        assertEquals(motion(.85f,0),motion(1.02f,0),0);
        assertTrue(motion(1.59f,0)<motion(1.59f,2));
        assertTrue(motion(1.82f,0)>motion(1.82f,2));
        assertTrue(motion(2.05f,0)<motion(2.05f,2));
        assertEquals(motion(2.19f,0),motion(2.54f,0),0);
    }
    @Test public void revisedFuriosoHasSingleHorizontalCrossingShotgunChaseAndFinalPause(){
        float previous=motion(3f,0);
        assertTrue(previous<motion(3f,2));
        for(float t=3f;t<=3.44f;t+=.005f){
            assertEquals(0,motion(t,1),0);
            assertTrue(motion(t,0)>=previous-.0001f);
            previous=motion(t,0);
        }
        assertTrue(motion(3.08f,0)>motion(3.08f,2));
        assertEquals(motion(3.08f,0),motion(3.44f,0),0);
        float shotgun=Math.abs(motion(3.53f,2)-motion(3.44f,2));
        assertTrue(shotgun>3*Math.abs(motion(.21f,2)-motion(0,2)));
        assertEquals(motion(3.44f,0),motion(3.73f,0),0);
        assertTrue(Math.abs(motion(3.90f,0)-motion(3.90f,2))<Math.abs(motion(3.73f,0)-motion(3.73f,2)));
        assertTrue(motion(4.23f,0)<motion(4.23f,2));
        assertEquals(motion(4.39f,0),motion(5.04f,0),0);
        assertEquals(motion(4.39f,1),motion(5.04f,1),0);
        assertTrue(motion(5.38f,1)<0);assertTrue(motion(5.6f,1)>0);
    }

    @Test public void meleeSpacingAdaptsToLargeEnemiesAndKeepsApproachMovement(){
        for(float time:new float[]{.73f,1.19f,1.50f,1.59f,1.82f,2.05f,2.70f,3.08f,3.90f,4.23f,5.6f}){
            float normal=com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.motion(com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.audioTime(time),0,.55f);
            float large=com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.motion(com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.audioTime(time),0,.85f);
            float target=motion(time,2);
            assertTrue(Math.abs(normal-target)>=.55f-.0001f);
            assertTrue(Math.abs(large-target)>=.85f-.0001f);
            assertTrue(Math.abs(large-target)>Math.abs(normal-target));
        }
        assertTrue(motion(1.19f,0)<motion(1.02f,0));
        // Crossing remains continuous and passes through the target, without snapping sides.
        float left=motion(3f,0),right=motion(3.08f,0);
        assertEquals((left+right)/2,motion(3.04f,0),.0001f);
    }

    @Test public void furiosoAudioCuesMatchImpactsAndPreserveFinalReverb(){
        assertEquals(.20f,com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.audioTime(.21f),.0001f);
        assertEquals(.71f,com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.audioTime(.45f),.0001f);
        assertEquals(7.84f,com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.audioTime(3.53f),.0001f);
        assertEquals(10.47f,com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.STRIKES,0);
        assertEquals(com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.SOUND_DURATION,
            com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.DURATION-com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.ENTER,.0001f);
        float previous=-1;
        for(float t=0;t<5.6f;t+=.01f){
            float mapped=com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.audioTime(t);
            assertTrue(mapped>previous);previous=mapped;
        }
    }
}
