package com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.ChaliceOfBlood;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import java.util.ArrayList;

/** A plain tier-five sword for now; no special or duelist ability. */
public class Excalibur extends MeleeWeapon {
    { tier = 5; image = ItemSpriteSheet.GREATSWORD;
      hitSound = Assets.Sounds.HIT_SLASH; hitSoundPitch = 1f; }
    @Override public String defaultAction() { return AC_EQUIP; }
    @Override public ArrayList<String> actions(Hero hero) {
        ArrayList<String> result = super.actions(hero); result.remove(AC_ABILITY); return result;
    }
    @Override public void execute(Hero hero, String action) {
        if (!AC_ABILITY.equals(action)) super.execute(hero, action);
    }
    @Override public String abilityInfo() { return ""; }

    public static class Recipe extends com.shatteredpixel.shatteredpixeldungeon.items.Recipe {
        @Override public boolean testIngredients(ArrayList<Item> ingredients) {
            if (ingredients.size() != 2) return false;
            boolean sword = false, chalice = false;
            for (Item item : ingredients) {
                if (!item.isIdentified() || item.cursed || item.quantity() != 1) return false;
                if (item.getClass() == WornShortsword.class && ((WornShortsword)item).readyForExcalibur()) sword = true;
                else if (item.getClass() == ChaliceOfBlood.class && item.trueLevel() == 10) chalice = true;
                else return false;
            }
            return sword && chalice;
        }
        @Override public int cost(ArrayList<Item> ingredients) { return 50; }
        @Override public Item sampleOutput(ArrayList<Item> ingredients) { return new Excalibur().identify(); }
        @Override public Item brew(ArrayList<Item> ingredients) {
            if (!testIngredients(ingredients)) return null;
            for (Item item : ingredients) item.quantity(0);
            return sampleOutput(ingredients);
        }
    }
}
