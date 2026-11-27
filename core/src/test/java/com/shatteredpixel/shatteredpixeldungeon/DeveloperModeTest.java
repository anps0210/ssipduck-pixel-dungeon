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

    private float motion(float t,int axis){return com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso.motion(t,axis);}
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
    @Test public void revisedFuriosoHasDiagonalCrossingsShotgunChaseAndFinalPause(){
        assertTrue(motion(3f,1)<0);assertTrue(motion(3.08f,1)>0);
        assertTrue(motion(3.13f,1)<0);assertTrue(motion(3.21f,1)>0);
        float shotgun=Math.abs(motion(3.53f,2)-motion(3.44f,2));
        assertTrue(shotgun>3*Math.abs(motion(.21f,2)-motion(0,2)));
        assertEquals(motion(3.44f,0),motion(3.73f,0),0);
        assertTrue(Math.abs(motion(3.90f,0)-motion(3.90f,2))<Math.abs(motion(3.73f,0)-motion(3.73f,2)));
        assertTrue(motion(4.23f,0)<motion(4.23f,2));
        assertEquals(motion(4.39f,0),motion(5.04f,0),0);
        assertEquals(motion(4.39f,1),motion(5.04f,1),0);
        assertTrue(motion(5.38f,1)<0);assertTrue(motion(5.6f,1)>0);
    }
}
