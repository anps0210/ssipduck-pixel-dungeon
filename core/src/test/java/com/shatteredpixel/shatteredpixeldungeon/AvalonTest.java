package com.shatteredpixel.shatteredpixeldungeon;

import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.ChaliceOfBlood;
import org.junit.Test;
import static org.junit.Assert.*;

/** Regression cases for the next requested test/build run. */
public class AvalonTest {
    @Test public void equippedBonusUpdatesWithoutHealingOrStacking() {
        Hero hero = new Hero(); hero.HP = 17;
        ChaliceOfBlood avalon = new ChaliceOfBlood(); avalon.level(5);
        assertEquals(20, hero.HT); // In the bag: no bonus.
        avalon.activate(hero);
        assertEquals(30, hero.HT); assertEquals(17, hero.HP);
        avalon.activate(hero); // Reactivation must not stack or temporarily clamp HP.
        assertEquals(30, hero.HT); assertEquals(17, hero.HP);
        avalon.upgrade();
        assertEquals(32, hero.HT); assertEquals(17, hero.HP);
        avalon.level(10);
        assertEquals(40, hero.HT);
        hero.HP = 37;
        avalon.activate(hero);
        assertEquals(40, hero.HT); assertEquals(37, hero.HP);
        hero.buff(ChaliceOfBlood.chaliceRegen.class).detach();
        assertEquals(20, hero.HT); assertEquals(20, hero.HP);
    }
    @Test public void zeroLevelAndDowngradeRecalculateFlatBonus() {
        Hero hero = new Hero();
        ChaliceOfBlood avalon = new ChaliceOfBlood(); avalon.activate(hero);
        assertEquals(20, hero.HT);
        avalon.upgrade(); assertEquals(22, hero.HT);
        avalon.degrade(); assertEquals(20, hero.HT);
        assertEquals(1.05f, ChaliceOfBlood.HEALING_MULTIPLIER, 0.00001f);
        hero.buff(ChaliceOfBlood.chaliceRegen.class).detach();
    }
}
