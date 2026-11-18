package com.shatteredpixel.shatteredpixeldungeon.items.weapon;

import com.shatteredpixel.shatteredpixeldungeon.sprites.ItemSpriteSheet;

/** Sakuya's reusable knife, retaining SpiritBow's growth and combat rules. */
public class ScarletMansionKnife extends SpiritBow {
    { image = ItemSpriteSheet.THROWING_KNIFE; }

    @Override public SpiritArrow knockArrow() {
        SpiritArrow knife = super.knockArrow();
        knife.image = ItemSpriteSheet.THROWING_KNIFE;
        return knife;
    }
}
