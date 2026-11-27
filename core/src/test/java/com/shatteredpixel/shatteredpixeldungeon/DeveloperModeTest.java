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
}
