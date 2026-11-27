package com.shatteredpixel.shatteredpixeldungeon.items.bags;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.KindOfWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.Weapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.MeleeWeapon;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.RolandAbilities;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import java.util.ArrayList;

/** Weapon-only bag; uses the normal inventory, curse, and equipment lifecycle. */
public class SilenceGloves extends Bag {
    public static final String AC_FURIOSO = "FURIOSO";
    { image=ItemSpriteSheet.SILENCE_GLOVES; bones=false; }
    @Override public int capacity(){return 30;}
    @Override public boolean canHold(Item item){return item instanceof Weapon && super.canHold(item);}
    public static SilenceGloves find(Hero hero){
        if(hero==null)return null;
        for(Bag b:hero.belongings.getBags())if(b instanceof SilenceGloves)return (SilenceGloves)b;
        return null;
    }
    public static boolean enabled(Hero hero){
        return hero!=null && hero.subClass==HeroSubClass.CHAMPION && !hero.belongings.lostInventory() && find(hero)!=null;
    }
    public static boolean freeEquip(Hero hero, Item item){
        SilenceGloves bag=find(hero);
        return enabled(hero) && (bag.contains(item) || item.isEquipped(hero));
    }
    public static void grant(Hero hero){
        if(hero.subClass!=HeroSubClass.CHAMPION)return;
        SilenceGloves bag=find(hero);
        if(bag==null){
            bag=new SilenceGloves();
            // A legacy save may be loaded while its inventory is lost. Allow only
            // the migration itself; the bag remains unusable until recovery.
            bag.keptThoughLostInvent=true;
            boolean collected=bag.collect(hero.belongings.backpack);
            bag.keptThoughLostInvent=false;
            if(!collected)return;
        }
        // Migrate the old Champion secondary slot without dropping or duplicating it.
        KindOfWeapon secondary=hero.belongings.secondWep;
        if(secondary!=null && bag.canHold(secondary)){
            hero.belongings.secondWep=null;
            if(!secondary.collect(bag))hero.belongings.secondWep=secondary;
        }
    }
    @Override public ArrayList<Item> quickUseItems(Hero hero) {
        ArrayList<Item> result=super.quickUseItems(hero);
        if(enabled(hero)) {
            KindOfWeapon equipped=hero.belongings.weapon();
            if(equipped!=null && !result.contains(equipped))result.add(0,equipped);
        }
        return result;
    }
    @Override public String status() {
        Hero hero=Dungeon.hero;
        MeleeWeapon.Charger charger=hero==null?null:hero.buff(MeleeWeapon.Charger.class);
        return enabled(hero) && charger!=null ? charger.charges+"/"+charger.chargeCap() : super.status();
    }
    public boolean quickAbility(Hero hero, Item item){
        if(!enabled(hero) || (!contains(item) && item!=hero.belongings.weapon()) || !(item instanceof MeleeWeapon))return false;
        MeleeWeapon weapon=(MeleeWeapon)item;
        if(RolandAbilities.kind(weapon)==RolandAbilities.Kind.NONE)return false;
        if(!weapon.isEquipped(hero) && !weapon.doEquip(hero))return false;
        weapon.execute(hero,MeleeWeapon.AC_ABILITY);
        return true;
    }
    @Override public ArrayList<String> actions(Hero hero){
        ArrayList<String> actions=super.actions(hero);
        // A subclass reward cannot be discarded or thrown away accidentally.
        actions.remove(AC_DROP);actions.remove(AC_THROW);
        if(RolandAbilities.furiosoReady(hero))actions.add(AC_FURIOSO);
        return actions;
    }
    @Override public String actionName(String action,Hero hero){
        return AC_FURIOSO.equals(action)?"Furioso":super.actionName(action,hero);
    }
    @Override public void execute(Hero hero,String action){
        if(!enabled(hero))return;
        if(AC_FURIOSO.equals(action))RolandAbilities.promptFurioso(hero);
        else super.execute(hero,action);
    }
    @Override public String desc(){
        return super.desc()+"\n\n"+Messages.get(this,"progress",RolandAbilities.furiosoCount(Dungeon.hero));
    }
    /** Donor is selected by tier first, then actual enchantment level. Never recursively borrow. */
    public static int resonantLevel(Hero hero,MeleeWeapon receiver,int ownLevel){
        SilenceGloves bag=find(hero);
        if(!enabled(hero) || !hero.hasTalent(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.TWIN_UPGRADES)
                || !(bag.contains(receiver)||receiver.isEquipped(hero)))return ownLevel;
        ArrayList<MeleeWeapon> candidates=new ArrayList<>();
        for(Item item:bag.items)if(item instanceof MeleeWeapon)candidates.add((MeleeWeapon)item);
        if(hero.belongings.weapon() instanceof MeleeWeapon)candidates.add((MeleeWeapon)hero.belongings.weapon());
        int highestTier=0,highestLevel=Integer.MIN_VALUE;
        for(MeleeWeapon w:candidates){
            if(w.tier>highestTier){highestTier=w.tier;highestLevel=w.actualBuffedLevel();}
            else if(w.tier==highestTier)highestLevel=Math.max(highestLevel,w.actualBuffedLevel());
        }
        int gap=3-hero.pointsInTalent(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.TWIN_UPGRADES);
        return receiver.tier+gap<=highestTier?Math.max(ownLevel,highestLevel):ownLevel;
    }
}
