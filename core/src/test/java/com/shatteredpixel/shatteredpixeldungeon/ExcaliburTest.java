package com.shatteredpixel.shatteredpixeldungeon;

import com.shatteredpixel.shatteredpixeldungeon.items.Item;
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
        restored.restoreFromBundle(fake); assertFalse(restored.readyForExcalibur());
        Bundle legacy=new Bundle(); legacy.put("level",5); legacy.put("quantity",1);
        restored.restoreFromBundle(legacy); assertTrue(restored.readyForExcalibur());
    }
}
