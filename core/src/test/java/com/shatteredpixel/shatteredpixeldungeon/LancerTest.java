package com.shatteredpixel.shatteredpixeldungeon;

import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.DriedRose.GhostHero;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Poison;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Warlock;
import com.watabou.utils.Bundle;
import org.junit.Test;
import static org.junit.Assert.*;

public class LancerTest {
    @Test public void continuationTriggersOnceAndDecaysWithTime() {
        GhostHero lancer = new GhostHero();
        lancer.HP = lancer.HT = 40;
        float normalDelay = lancer.attackDelay();
        assertNull(lancer.buff(GhostHero.BattleContinuation.class));
        lancer.HP = 0;
        assertTrue(lancer.isAlive());
        GhostHero.BattleContinuation shield = lancer.buff(GhostHero.BattleContinuation.class);
        assertEquals(24, shield.shielding());
        assertEquals(normalDelay / 2f, lancer.attackDelay(), 0.0001f);
        shield.act();
        assertEquals(20, shield.shielding());
        assertTrue(lancer.isAlive());
        assertEquals(20, shield.shielding());
        shield.absorbDamage(20);
        assertFalse(lancer.isAlive());
        assertEquals(0, shield.shielding());
    }

    @Test public void saveRetainsUsedContinuationAndRemainingShield() {
        GhostHero lancer = new GhostHero();
        lancer.HT = 40; lancer.HP = 0;
        assertTrue(lancer.isAlive());
        lancer.buff(GhostHero.BattleContinuation.class).absorbDamage(7);
        Bundle bundle = new Bundle(); lancer.storeInBundle(bundle);
        GhostHero restored = new GhostHero(); restored.restoreFromBundle(bundle);
        assertTrue(restored.isAlive());
        assertEquals(17, restored.buff(GhostHero.BattleContinuation.class).shielding());
        restored.buff(GhostHero.BattleContinuation.class).absorbDamage(17);
        assertFalse(restored.isAlive());
    }

    @Test public void projectileProtectionExcludesPoisonAndRoseLoss() {
        GhostHero lancer = new GhostHero();
        assertTrue(lancer.isRangedDamage(new Warlock.DarkBolt()));
        assertFalse(lancer.isRangedDamage(new Poison()));
        assertFalse(lancer.isRangedDamage(new GhostHero.NoRoseDamage()));
    }
}
