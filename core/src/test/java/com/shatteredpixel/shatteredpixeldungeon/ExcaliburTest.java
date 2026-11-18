package com.shatteredpixel.shatteredpixeldungeon;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.items.Recipe;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.ChaliceOfBlood;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.Excalibur;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.WornShortsword;
import com.watabou.utils.Bundle;
import org.junit.Test;
import java.util.ArrayList;
import java.util.Arrays;
import static org.junit.Assert.*;

public class ExcaliburTest {
    private WornShortsword sword(int level, int scrolls) {
        WornShortsword s = new WornShortsword(); s.level(level); s.identify(false);
        for (int i=0;i<scrolls;i++) s.recordScrollUpgrade(); return s;
    }
    private ChaliceOfBlood chalice(int level) {
        ChaliceOfBlood c=new ChaliceOfBlood(); c.level(level); c.identify(false); return c;
    }
    private ArrayList<Item> inputs(Item... items) { return new ArrayList<>(Arrays.asList(items)); }
    @Test public void exactRecipeConsumesBothAndCreatesPlainTierFiveSword() {
        ArrayList<Item> items=inputs(chalice(10),sword(5,5));
        Excalibur.Recipe recipe=new Excalibur.Recipe();
        assertTrue(recipe.testIngredients(items)); assertEquals(50,recipe.cost(items));
        assertTrue(Recipe.findRecipes(items).stream().anyMatch(r->r instanceof Excalibur.Recipe));
        Item preview=recipe.sampleOutput(items); assertEquals(1,items.get(0).quantity());
        Excalibur result=(Excalibur)recipe.brew(items);
        assertEquals(0,items.get(0).quantity()); assertEquals(0,items.get(1).quantity());
        assertEquals(0,result.trueLevel()); assertEquals(5,result.tier);
        assertEquals(5,result.min(0)); assertEquals(30,result.max(0));
        assertTrue(result.isIdentified()); assertEquals(preview.getClass(),result.getClass());
    }
    @Test public void rejectsWrongLevelsFakeUpgradesCursesDuplicatesAndExtrasWithoutConsumption() {
        Excalibur.Recipe r=new Excalibur.Recipe();
        ArrayList<ArrayList<Item>> bad=new ArrayList<>();
        bad.add(inputs(sword(4,4),chalice(10))); bad.add(inputs(sword(6,6),chalice(10)));
        bad.add(inputs(sword(5,0),chalice(10))); bad.add(inputs(sword(5,5),chalice(9)));
        bad.add(inputs(sword(5,5),sword(5,5))); bad.add(inputs(sword(5,5),chalice(10),new Item()));
        WornShortsword cursed=sword(5,5); cursed.cursed=true; bad.add(inputs(cursed,chalice(10)));
        WornShortsword hidden=sword(5,5); hidden.levelKnown=false; bad.add(inputs(hidden,chalice(10)));
        for (ArrayList<Item> items:bad) {
            assertFalse(r.testIngredients(items)); assertNull(r.brew(items));
            for(Item i:items) assertEquals(1,i.quantity());
        }
    }
    @Test public void provenanceSurvivesSaveAndLegacySaveRemainsUsable() {
        Bundle bundle=new Bundle(); sword(5,5).storeInBundle(bundle);
        WornShortsword restored=new WornShortsword(); restored.restoreFromBundle(bundle);
        assertTrue(restored.readyForExcalibur());
        Bundle fake=new Bundle(); sword(5,0).storeInBundle(fake);
        restored = new WornShortsword(); restored.restoreFromBundle(fake);
        assertEquals(5, restored.trueLevel()); assertFalse(restored.readyForExcalibur());
        Bundle legacy=new Bundle(); legacy.put("level",5); legacy.put("quantity",1);
        restored = new WornShortsword(); restored.restoreFromBundle(legacy);
        assertEquals(5, restored.trueLevel()); assertTrue(restored.readyForExcalibur());
    }
    @Test public void passivesWorkAtZeroChargeWithoutHealingOrDuplicateBonuses() {
        Hero hero = new Hero();
        Excalibur sword = new Excalibur();
        hero.HP = 17;
        assertEquals(0, Excalibur.maxHealthBonus(hero));
        hero.belongings.weapon = sword;
        sword.activate(hero);
        assertEquals(0, sword.chargePercent());
        assertEquals(36, hero.HT);
        assertEquals(17, hero.HP);
        assertEquals(4f, Excalibur.regenerationMultiplier(hero), 0f);
        hero.HP = 35;
        sword.activate(hero);
        assertEquals(35, hero.HP);
        Excalibur secondary = new Excalibur();
        hero.belongings.secondWep = secondary;
        secondary.activate(hero);
        assertEquals(36, hero.HT);
        assertEquals(1, hero.buffs(Excalibur.ExcaliburCharge.class).size());
        Buff.affect(hero, MagicImmune.class);
        assertEquals(1f, Excalibur.regenerationMultiplier(hero), 0f);
        hero.belongings.weapon = hero.belongings.secondWep = null;
        hero.buff(Excalibur.ExcaliburCharge.class).act();
        assertEquals(20, hero.HT);
        assertEquals(20, hero.HP);
        assertNull(hero.buff(Excalibur.ExcaliburCharge.class));
    }

    @Test public void chargeCapsPersistsAndDoesNotChangeBaseDamage() {
        Hero hero = new Hero();
        Excalibur sword = new Excalibur();
        hero.belongings.weapon = sword;
        sword.activate(hero);
        for (int i = 0; i < 37; i++) hero.buff(Excalibur.ExcaliburCharge.class).act();
        assertEquals("37%", sword.status());
        Bundle saved = new Bundle();
        sword.storeInBundle(saved);
        Excalibur restored = new Excalibur();
        restored.restoreFromBundle(saved);
        assertEquals(37, restored.chargePercent());
        for (int i = 0; i < 100; i++) hero.buff(Excalibur.ExcaliburCharge.class).act();
        assertEquals(100, sword.chargePercent());
        assertEquals(5, sword.min(0));
        assertEquals(30, sword.max(0));
        assertEquals(4f, Excalibur.regenerationMultiplier(hero), 0f);
        hero.belongings.weapon = null;
        hero.buff(Excalibur.ExcaliburCharge.class).act();
        assertEquals(100, sword.chargePercent());
    }

    @Test public void legacySwordStartsUncharged() {
        Bundle legacy = new Bundle();
        legacy.put("level", 0);
        legacy.put("quantity", 1);
        Excalibur sword = new Excalibur();
        sword.restoreFromBundle(legacy);
        assertEquals(0, sword.chargePercent());
    }
}
