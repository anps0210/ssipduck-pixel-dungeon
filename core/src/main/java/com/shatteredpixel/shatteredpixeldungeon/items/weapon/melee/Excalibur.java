package com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee;

import com.shatteredpixel.shatteredpixeldungeon.Assets;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.MagicImmune;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Belongings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.watabou.utils.Bundle;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.ChaliceOfBlood;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import java.util.ArrayList;

/** Tier-five sword with an independent charge reserve and Avalon passives. */
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

    public static final int MAX_HEALTH_BONUS = 16;
    public static final float REGEN_MULTIPLIER = 4f;
    private static final String CHARGE = "excalibur_charge";
    private int charge;

    public int chargePercent() { return charge; }

    @Override public String status() { return charge + "%"; }

    @Override public String desc() {
        return super.desc() + "\n\n" + Messages.get(this, "charge", charge);
    }

    // Flat bonus, independent of upgrades, charge and the number of equipped copies.
    public static int maxHealthBonus(Hero hero) {
        return hero != null && (hero.belongings.weapon instanceof Excalibur
                || hero.belongings.secondWep instanceof Excalibur) ? MAX_HEALTH_BONUS : 0;
    }

    public static float regenerationMultiplier(Hero hero) {
        if (hero == null || hero.buff(MagicImmune.class) != null) return 1f;
        return hero.belongings.weapon() instanceof Excalibur
                || hero.belongings.secondWep() instanceof Excalibur ? REGEN_MULTIPLIER : 1f;
    }

    @Override public void activate(Char ch) {
        super.activate(ch);
        if (ch instanceof Hero) {
            Buff.affect(ch, ExcaliburCharge.class);
            if (!Belongings.bundleRestoring) ((Hero) ch).updateHT(false);
        }
    }

    @Override public boolean doUnequip(Hero hero, boolean collect, boolean single) {
        if (!super.doUnequip(hero, collect, single)) return false;
        if (maxHealthBonus(hero) == 0) Buff.detach(hero, ExcaliburCharge.class);
        hero.updateHT(false);
        return true;
    }

    @Override public void storeInBundle(Bundle bundle) {
        super.storeInBundle(bundle);
        bundle.put(CHARGE, charge);
    }

    @Override public void restoreFromBundle(Bundle bundle) {
        super.restoreFromBundle(bundle);
        // Older saves have no charge field and start at zero.
        charge = Math.max(0, Math.min(100, bundle.getInt(CHARGE)));
    }

    private void chargeTurn() {
        if (charge < 100) {
            charge++;
            updateQuickslot();
        }
    }

    /** One timer per hero, even if both weapon slots hold Excalibur. */
    public static class ExcaliburCharge extends Buff {
        @Override public boolean act() {
            if (!(target instanceof Hero)) {
                detach();
                return true;
            }
            Hero hero = (Hero) target;
            if (maxHealthBonus(hero) == 0) {
                detach();
                hero.updateHT(false);
                return true;
            }
            if (hero.belongings.weapon() instanceof Excalibur) {
                ((Excalibur) hero.belongings.weapon()).chargeTurn();
            }
            if (hero.belongings.secondWep() instanceof Excalibur
                    && hero.belongings.secondWep() != hero.belongings.weapon()) {
                ((Excalibur) hero.belongings.secondWep()).chargeTurn();
            }
            spend(TICK);
            return true;
        }
    }

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
