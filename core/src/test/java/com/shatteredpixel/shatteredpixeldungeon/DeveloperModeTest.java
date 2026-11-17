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
}
