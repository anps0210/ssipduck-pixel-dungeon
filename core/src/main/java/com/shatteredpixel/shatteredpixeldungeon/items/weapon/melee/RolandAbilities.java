package com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.watabou.utils.Bundle;

import java.util.ArrayList;
import java.util.Locale;

/** Roland's nine weapon families (twelve variants), independent of weapon stats and save classes. */
public final class RolandAbilities {
    private RolandAbilities() {}
    public enum Kind {
        NONE(0), MOOK(2), ALLAS(1), ALLAS_GAUNTLET(2), ZELKOVA(2), OLDBOY(2),
        WHEELS_ATK(4), WHEELS_DEF(3), CRYSTAL(2), RANGA(1),
        LOGIC_PISTOL(3), LOGIC_SHOTGUN(4), DURANDAL(1), FURIOSO(0);
        public final int charge;
        Kind(int charge) { this.charge = charge; }
        public String key() { return name().toLowerCase(Locale.ROOT); }
    }
    public static Kind kind(MeleeWeapon w) {
        // Exact classes: Excalibur and other special derived weapons keep their own mechanics.
        Class<?> c = w.getClass();
        if (c == Rapier.class) return Kind.DURANDAL;
        if (c == Dirk.class || c == Longsword.class) return Kind.ZELKOVA;
        if (c == Quarterstaff.class || c == HandAxe.class || c == Greatshield.class) return Kind.OLDBOY;
        if (c == Sickle.class || c == Scimitar.class) return Kind.RANGA;
        if (c == Shortsword.class || c == Mace.class || c == AssassinsBlade.class || c == WarHammer.class) return Kind.MOOK;
        if (c == Spear.class || c == Whip.class) return Kind.LOGIC_PISTOL;
        if (c == RoundShield.class || c == Flail.class) return Kind.WHEELS_ATK;
        if (c == Sword.class || c == Katana.class || c == Greatsword.class) return Kind.CRYSTAL;
        if (c == Sai.class || c == RunicBlade.class || c == Glaive.class) return Kind.ALLAS;
        if (c == BattleAxe.class || c == Greataxe.class) return Kind.WHEELS_DEF;
        if (c == Gauntlet.class) return Kind.ALLAS_GAUNTLET;
        if (c == WarScythe.class) return Kind.LOGIC_SHOTGUN;
        return Kind.NONE;
    }
    public static String name(MeleeWeapon w) { return Messages.get(RolandAbilities.class, kind(w).key()+"_name"); }
    public static String info(MeleeWeapon w) {
        Kind k = kind(w);
        return "_"+name(w)+"_\n"+Messages.get(RolandAbilities.class, k.key()+"_desc")
                +"\n"+Messages.get(RolandAbilities.class,"charge",k.charge);
    }
    public static int cost(MeleeWeapon w, Hero h) {
        CrystalReturn pending = h == null ? null : h.buff(CrystalReturn.class);
        return kind(w) == Kind.CRYSTAL && pending != null && pending.matches(w) ? 0 : kind(w).charge;
    }
    private static boolean hostile(Hero hero, Char ch) {
        return ch != null && ch != hero && ch.isAlive() && ch.alignment == Char.Alignment.ENEMY && !hero.isCharmedBy(ch);
    }
    private static boolean clearLine(int from, int to) {
        return new Ballistica(from,to,Ballistica.STOP_SOLID | Ballistica.STOP_TARGET).collisionPos == to;
    }
    private static boolean landing(Hero hero, int cell) {
        return Dungeon.level.insideMap(cell) && (Actor.findChar(cell) == null || cell == hero.pos)
                && (Dungeon.level.passable[cell] || (hero.flying && Dungeon.level.avoid[cell]));
    }
    public static int behind(Hero hero, int target) {
        int width = Dungeon.level.width();
        int x = target%width + Integer.signum(target%width-hero.pos%width);
        int y = target/width + Integer.signum(target/width-hero.pos/width);
        if (x <= 0 || x >= width-1 || y <= 0 || y >= Dungeon.level.height()-1) return -1;
        return y*width+x;
    }
    /** Validate everything before spending charge, moving, or changing buffs. */
    public static boolean valid(MeleeWeapon w, Hero hero, Integer cell) {
        Kind k = kind(w);
        if (k == Kind.NONE || hero.heroClass != HeroClass.DUELIST || !w.isEquipped(hero)
                || w.STRReq() > hero.STR() || Dungeon.level == null) return false;
        MeleeWeapon.Charger charger = hero.buff(MeleeWeapon.Charger.class);
        if (charger == null || charger.charges+charger.partialCharge < cost(w,hero)) return false;
        if (k == Kind.WHEELS_DEF) return true;
        if (cell == null || !Dungeon.level.insideMap(cell) || !Dungeon.level.heroFOV[cell]) return false;
        int distance = Dungeon.level.distance(hero.pos,cell);
        if (k == Kind.RANGA) {
            if (hero.rooted || distance < 1 || distance > 5 || !landing(hero,cell) || !clearLine(hero.pos,cell)) return false;
            int dx = Math.abs(cell % Dungeon.level.width()-hero.pos % Dungeon.level.width());
            int dy = Math.abs(cell / Dungeon.level.width()-hero.pos / Dungeon.level.width());
            return dx == 0 || dy == 0 || dx == dy;
        }
        Char enemy = Actor.findChar(cell);
        if (!hostile(hero,enemy)) return false;
        CrystalReturn pending = hero.buff(CrystalReturn.class);
        if (k == Kind.CRYSTAL && pending != null && pending.matches(w)) {
            return !hero.rooted && pending.depth == Dungeon.depth && pending.branch == Dungeon.branch
                    && landing(hero,pending.origin) && Dungeon.level.distance(pending.origin,cell) <= w.reachFactor(hero)
                    && clearLine(pending.origin,cell);
        }
        if (!clearLine(hero.pos,cell)) return false;
        switch (k) {
            case MOOK: return distance <= w.reachFactor(hero)+2;
            case LOGIC_PISTOL: return distance <= 3;
            case LOGIC_SHOTGUN: return distance == 1;
            case ALLAS:
                if (hero.rooted || distance > 2) return false;
                if (distance == 1) return true;
                int step = new Ballistica(hero.pos,cell,Ballistica.STOP_TARGET).path.get(1);
                return landing(hero,step) && Dungeon.level.adjacent(step,cell);
            case ALLAS_GAUNTLET: return !hero.rooted && distance <= 5 && landing(hero,behind(hero,cell));
            case CRYSTAL: return !hero.rooted && distance <= w.reachFactor(hero) && landing(hero,behind(hero,cell));
            default: return distance <= w.reachFactor(hero);
        }
    }
    public static boolean use(MeleeWeapon w, Hero hero, Integer cell) {
        if (!valid(w,hero,cell)) { GLog.w(Messages.get(RolandAbilities.class,"invalid")); return false; }
        if (hero.sprite == null) { resolve(w,hero,cell); return true; }
        hero.busy();
        hero.sprite.attack(cell == null ? hero.pos : cell, () -> resolve(w,hero,cell));
        return true;
    }
    private static void relocate(Hero hero, int cell) {
        hero.move(cell,false); // includes doors, plants and traps; walls/pits checked before use
        if (hero.sprite != null) {
            hero.sprite.place(cell);
            Dungeon.observe();
            com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.updateFog();
        }
    }
    private static void resolve(MeleeWeapon w, Hero hero, Integer cell) {
        // Animation callbacks can run after another effect has changed the target or tile.
        if (!hero.isAlive() || !valid(w,hero,cell)) { hero.next(); return; }
        Kind k = kind(w);
        Char enemy = cell == null ? null : Actor.findChar(cell);
        CrystalReturn pending = hero.buff(CrystalReturn.class);
        boolean returning = k == Kind.CRYSTAL && pending != null && pending.matches(w);
        int origin = hero.pos;
        w.beforeAbilityUsed(hero,enemy);
        float delay = hero.attackDelay();
        try {
            if (pending != null && !returning) pending.detach();
            switch (k) {
                case WHEELS_DEF:
                    Buff.affect(hero,Barrier.class).setShield(Math.round(w.damageRoll(hero)*0.8f));
                    break;
                case ALLAS_GAUNTLET:
                    relocate(hero,behind(hero,cell));
                    if (hero.isAlive()) Buff.prolong(hero,Haste.class,5f);
                    break;
                case RANGA:
                    ArrayList<Char> victims = new ArrayList<>();
                    Ballistica line = new Ballistica(origin,cell,Ballistica.STOP_TARGET | Ballistica.STOP_SOLID);
                    for (int i = 1; i < line.dist; i++) {
                        Char ch = Actor.findChar(line.path.get(i));
                        if (hostile(hero,ch)) victims.add(ch);
                    }
                    relocate(hero,cell);
                    for (Char victim : victims) {
                        if (!hero.isAlive()) break;
                        strike(w,hero,victim,0.8f,false,k);
                    }
                    break;
                case CRYSTAL:
                    relocate(hero,returning ? pending.origin : behind(hero,cell));
                    if (returning) pending.detach();
                    else {
                        CrystalReturn next = Buff.affect(hero,CrystalReturn.class);
                        next.origin=origin; next.depth=Dungeon.depth; next.branch=Dungeon.branch;
                        next.weaponId=w.rolandId();
                        delay=0f;
                    }
                    if (hero.isAlive()) strike(w,hero,enemy,1f,true,k);
                    break;
                case ALLAS:
                    if (Dungeon.level.distance(origin,cell)>1)
                        relocate(hero,new Ballistica(origin,cell,Ballistica.STOP_TARGET).path.get(1));
                    if (hero.isAlive()) strike(w,hero,enemy,1f,false,k);
                    break;
                case LOGIC_PISTOL:
                    strike(w,hero,enemy,0.6f,false,k);
                    if (hero.isAlive() && hostile(hero,enemy)) strike(w,hero,enemy,0.6f,false,k);
                    break;
                default:
                    float multiplier = k == Kind.WHEELS_ATK ? 1.7f : k == Kind.LOGIC_SHOTGUN ? 1.3f : 1f;
                    strike(w,hero,enemy,multiplier,false,k);
            }
        } finally {
            w.afterAbilityUsed(hero);
        }
        recordFamily(hero,k);
        Invisibility.dispel();
        hero.spendAndNext(delay);
        MeleeWeapon.updateQuickslot();
    }
    private static void strike(MeleeWeapon w, Hero hero, Char enemy, float multiplier, boolean surprise, Kind k) {
        if (!hostile(hero,enemy)) return;
        Strike tracker = Buff.affect(hero,Strike.class);
        tracker.enemyId=enemy.id(); tracker.surprise=surprise;
        tracker.dealt=tracker.overkill=tracker.carried=0;
        boolean hit;
        try {
            boolean guaranteed = surprise || k == Kind.ZELKOVA || k == Kind.OLDBOY || k == Kind.DURANDAL || k == Kind.FURIOSO;
            hit=hero.attack(enemy,multiplier,0,guaranteed ? Char.INFINITE_ACCURACY : 1f);
        } finally { tracker.detach(); }
        if (!hit) return;
        if (k == Kind.FURIOSO) {
            int rank=hero.pointsInTalent(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.COMBINED_LETHALITY);
            if(enemy.isAlive()) {
                Buff.prolong(enemy,Vulnerable.class,rank+1);
                if(tracker.dealt>0)Buff.affect(enemy,Bleeding.class).set(tracker.dealt*0.05f*rank);
                if(rank==2)Buff.prolong(enemy,FuriosoDaze.class,3f);
                if(rank>=3)Buff.prolong(enemy,Daze.class,4f);
            }
            if(rank>=3)Buff.prolong(hero,Haste.class,5f);
        }
        if (enemy.isAlive()) {
            if (k == Kind.MOOK || k == Kind.LOGIC_SHOTGUN) {
                float bleed = tracker.dealt*(k == Kind.MOOK ? 0.5f : 0.15f);
                if (bleed > 0) Buff.affect(enemy,Bleeding.class).set(bleed);
            }
            if (k == Kind.ZELKOVA) Buff.prolong(enemy,Vulnerable.class,5f);
            if (k == Kind.OLDBOY) Buff.prolong(enemy,Daze.class,5f); // halves both accuracy and evasion
        } else {
            MeleeWeapon.onAbilityKill(hero,enemy);
            if (k == Kind.DURANDAL) {
                int saved=Math.max(0,tracker.overkill-tracker.carried);
                if (saved>0) Buff.affect(hero,DurandalDamage.class).amount=saved;
                Buff.prolong(hero,DurandalStrength.class,10f);
            }
        }
    }
    /** Called after shield absorption, before damage clamps HP to zero. */
    public static void onDamage(Char victim, Object source, int damage) {
        if (!(source instanceof Hero)) return;
        Strike tracker=((Hero)source).buff(Strike.class);
        if (tracker != null && tracker.enemyId == victim.id()) {
            tracker.dealt+=Math.max(0,damage);
            tracker.overkill=Math.max(tracker.overkill,Math.max(0,-victim.HP));
        }
    }
    public static int consumeDamage(Hero hero, int damage) {
        DurandalDamage reserve=hero.buff(DurandalDamage.class);
        if (reserve == null || damage < 0) return damage;
        int bonus=reserve.amount;
        Strike tracker=hero.buff(Strike.class);
        if (tracker != null) tracker.carried=bonus;
        reserve.detach();
        return damage+bonus;
    }
    public static boolean surprise(Char ch) {
        Strike s=ch.buff(Strike.class);
        return s != null && s.surprise;
    }
    public static int family(Kind kind) {
        switch(kind) {
            case MOOK:return 0;
            case ALLAS:case ALLAS_GAUNTLET:return 1;
            case ZELKOVA:return 2;
            case OLDBOY:return 3;
            case WHEELS_ATK:case WHEELS_DEF:return 4;
            case CRYSTAL:return 5;
            case RANGA:return 6;
            case LOGIC_PISTOL:case LOGIC_SHOTGUN:return 7;
            case DURANDAL:return 8;
            default:return -1;
        }
    }
    public static void recordFamily(Hero hero,Kind kind) {
        if(hero.subClass!=com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass.CHAMPION
                || !hero.hasTalent(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.COMBINED_LETHALITY))return;
        int family=family(kind);
        if(family>=0)Buff.affect(hero,FuriosoProgress.class).mask|=1<<family;
        MeleeWeapon.Charger charger=hero.buff(MeleeWeapon.Charger.class);
        if(charger!=null)charger.refreshActionIndicator();
        MeleeWeapon.updateQuickslot();
    }
    public static int furiosoCount(Hero hero) {
        FuriosoProgress p=hero==null?null:hero.buff(FuriosoProgress.class);
        return p==null?0:Integer.bitCount(p.mask & 511);
    }
    public static boolean furiosoReady(Hero hero) {
        return hero!=null && hero.subClass==com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass.CHAMPION
                && hero.hasTalent(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.COMBINED_LETHALITY)
                && furiosoCount(hero)==9;
    }
    public static boolean validFurioso(Hero hero,Integer cell) {
        if(!furiosoReady(hero) || !(hero.belongings.weapon() instanceof MeleeWeapon)
                || Dungeon.level==null || cell==null || !Dungeon.level.insideMap(cell) || !Dungeon.level.heroFOV[cell])return false;
        MeleeWeapon w=(MeleeWeapon)hero.belongings.weapon();
        return w.STRReq()<=hero.STR() && hostile(hero,Actor.findChar(cell))
                && Dungeon.level.distance(hero.pos,cell)<=w.reachFactor(hero) && clearLine(hero.pos,cell);
    }
    public static void promptFurioso(Hero hero) {
        if(!furiosoReady(hero))return;
        com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.selectCell(new com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector.Listener(){
            private boolean handled;
            @Override public void onSelect(Integer cell){
                if(handled)return; handled=true;
                if(cell!=null)useFurioso(hero,cell);
            }
            @Override public String prompt(){return Messages.get(RolandAbilities.class,"furioso_prompt");}
        });
    }
    public static boolean useFurioso(Hero hero,Integer cell) {
        if(!validFurioso(hero,cell))return false;
        final float delay=hero.attackDelay();
        Runnable action=()->{
            if(!validFurioso(hero,cell))return;
            MeleeWeapon w=(MeleeWeapon)hero.belongings.weapon();
            hero.buff(FuriosoProgress.class).mask=0;
            MeleeWeapon.Charger charger=hero.buff(MeleeWeapon.Charger.class);
            if(charger!=null)charger.refreshActionIndicator();
            int rank=hero.pointsInTalent(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.COMBINED_LETHALITY);
            hero.belongings.abilityWeapon=w;
            try {strike(w,hero,Actor.findChar(cell),1.5f+0.5f*rank,false,Kind.FURIOSO);}
            finally {w.afterAbilityUsed(hero);}
            Invisibility.dispel();
            com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator.refresh();MeleeWeapon.updateQuickslot();
        };
        Char enemy=Actor.findChar(cell);
        if(hero.sprite==null || enemy.sprite==null){action.run();hero.spendAndNext(delay);}
        else {
            hero.busy();
            com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene.show(
                new com.shatteredpixel.shatteredpixeldungeon.windows.WndFurioso(hero,enemy,action,
                    ()->hero.spendAndNext(delay),hero::next));
        }
        return true;
    }
    /** Once per enemy, after a successful damaging attack reaches the health threshold. */
    public static void stagger(Char victim,Object source,int damage) {
        if(!(source instanceof Hero) || damage<=0 || victim.HP<=0 || victim.alignment!=Char.Alignment.ENEMY
                || victim.buff(StaggerUsed.class)!=null)return;
        Hero hero=(Hero)source;
        int rank=hero.pointsInTalent(com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent.SWIFT_EQUIP);
        if(rank<=0 || victim.HP>victim.HT*(rank==1?0.33f:0.5f))return;
        Buff.affect(victim,StaggerUsed.class);
        if(Char.hasProp(victim,Char.Property.BOSS) || Char.hasProp(victim,Char.Property.MINIBOSS))
            Buff.prolong(victim,Vulnerable.class,rank);
        else Buff.prolong(victim,Paralysis.class,rank);
    }
    public static class StaggerUsed extends Buff {
        @Override public boolean act(){spend(TICK);return true;}
    }
    public static class FuriosoDaze extends FlavourBuff {
        {type=buffType.NEGATIVE;}
        @Override public int icon(){return BuffIndicator.DAZE;}
    }
    public static class FuriosoProgress extends Buff {
        public int mask;
        {type=buffType.POSITIVE;}
        @Override public int icon(){return BuffIndicator.WEAPON;}
        @Override public String desc(){return Messages.get(this,"desc",Integer.bitCount(mask&511));}
        @Override public void storeInBundle(Bundle b){super.storeInBundle(b);b.put("families",mask);}
        @Override public void restoreFromBundle(Bundle b){super.restoreFromBundle(b);mask=b.getInt("families")&511;}
    }
    public static class Strike extends Buff {
        int enemyId, dealt, overkill, carried;
        boolean surprise;
    }
    public static class DurandalDamage extends Buff {
        public int amount;
        { type=buffType.POSITIVE; }
        @Override public int icon() { return BuffIndicator.WEAPON; }
        @Override public String desc() { return Messages.get(this,"desc",amount); }
        @Override public void storeInBundle(Bundle b) { super.storeInBundle(b); b.put("amount",amount); }
        @Override public void restoreFromBundle(Bundle b) { super.restoreFromBundle(b); amount=b.getInt("amount"); }
    }
    public static class DurandalStrength extends FlavourBuff {
        { type=buffType.POSITIVE; }
        @Override public int icon() { return BuffIndicator.UPGRADE; }
        @Override public String desc() { return Messages.get(this,"desc",dispTurns()); }
    }
    public static class CrystalReturn extends Buff {
        public int origin, depth, branch;
        public String weaponId;
        { type=buffType.POSITIVE; }
        public boolean matches(MeleeWeapon w) {
            return depth == Dungeon.depth && branch == Dungeon.branch && w.rolandId().equals(weaponId);
        }
        @Override public boolean act() {
            if (depth != Dungeon.depth || branch != Dungeon.branch) detach();
            spend(TICK); return true;
        }
        @Override public int icon() { return BuffIndicator.WEAPON; }
        @Override public void storeInBundle(Bundle b) {
            super.storeInBundle(b); b.put("origin",origin); b.put("depth",depth); b.put("branch",branch); b.put("weapon",weaponId);
        }
        @Override public void restoreFromBundle(Bundle b) {
            super.restoreFromBundle(b); origin=b.getInt("origin"); depth=b.getInt("depth"); branch=b.getInt("branch"); weaponId=b.getString("weapon");
        }
    }
}
