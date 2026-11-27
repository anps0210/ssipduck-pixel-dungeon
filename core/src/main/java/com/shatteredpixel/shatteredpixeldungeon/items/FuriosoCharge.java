package com.shatteredpixel.shatteredpixeldungeon.items;

import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.RolandAbilities;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import java.util.ArrayList;

/** Developer-only consumable. Does not change subclass or talent allocation. */
public class FuriosoCharge extends Item {
    public static final String AC_CHARGE="CHARGE";
    {image=ItemSpriteSheet.ENERGY;stackable=true;bones=false;defaultAction=AC_CHARGE;}
    @Override public boolean isUpgradable(){return false;}
    @Override public boolean isIdentified(){return true;}
    @Override public ArrayList<String> actions(Hero hero){
        ArrayList<String> result=super.actions(hero);
        if(SPDSettings.developerMode())result.add(AC_CHARGE);
        return result;
    }
    public static boolean canCharge(Hero hero){
        return SPDSettings.developerMode() && hero!=null && hero.isAlive()
            && hero.subClass==HeroSubClass.CHAMPION && hero.hasTalent(Talent.COMBINED_LETHALITY);
    }
    public boolean charge(Hero hero){
        if(!canCharge(hero) || !hero.belongings.contains(this))return false;
        for(RolandAbilities.Kind kind:RolandAbilities.Kind.values())RolandAbilities.recordFamily(hero,kind);
        detach(hero.belongings.backpack);
        updateQuickslot();
        return true;
    }
    @Override public void execute(Hero hero,String action){
        super.execute(hero,action);
        if(AC_CHARGE.equals(action))GLog.i(Messages.get(this,charge(hero)?"charged":"requires"));
    }
}
