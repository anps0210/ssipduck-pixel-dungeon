package com.shatteredpixel.shatteredpixeldungeon;

import com.shatteredpixel.shatteredpixeldungeon.actors.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.*;
import com.shatteredpixel.shatteredpixeldungeon.levels.*;
import com.watabou.utils.Bundle;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class RolandAbilitiesTest {
    private static Object previousBadges;
    private static com.badlogic.gdx.Application previousApp;
    private static com.badlogic.gdx.Files previousFiles;
    private static Object previousPreferences;
    @BeforeClass public static void initHeadlessBadges() throws Exception {
        previousApp=com.badlogic.gdx.Gdx.app;previousFiles=com.badlogic.gdx.Gdx.files;
        java.lang.reflect.Field preferences=com.watabou.utils.GameSettings.class.getDeclaredField("prefs");
        preferences.setAccessible(true);previousPreferences=preferences.get(null);
        Map<String,Object> testSettings=new HashMap<>();
        com.watabou.utils.GameSettings.set((com.badlogic.gdx.Preferences)java.lang.reflect.Proxy.newProxyInstance(
            RolandAbilitiesTest.class.getClassLoader(),new Class[]{com.badlogic.gdx.Preferences.class},(proxy,method,args)->{
                if(method.getName().equals("putBoolean")){testSettings.put((String)args[0],args[1]);return proxy;}
                if(method.getName().equals("getBoolean"))return testSettings.getOrDefault(args[0],args.length>1?args[1]:false);
                if(method.getName().equals("getString"))return "en";
                if(method.getName().equals("contains"))return true;
                if(method.getReturnType()==boolean.class)return false;
                if(method.getReturnType()==int.class)return 0;
                if(method.getReturnType()==float.class)return 0f;
                if(method.getReturnType()==long.class)return 0L;
                return method.getReturnType()==com.badlogic.gdx.Preferences.class?proxy:null;
            }));
        com.badlogic.gdx.Gdx.app=(com.badlogic.gdx.Application)java.lang.reflect.Proxy.newProxyInstance(
            RolandAbilitiesTest.class.getClassLoader(),new Class[]{com.badlogic.gdx.Application.class},(proxy,method,args)->null);
        com.badlogic.gdx.Gdx.files=(com.badlogic.gdx.Files)java.lang.reflect.Proxy.newProxyInstance(
            RolandAbilitiesTest.class.getClassLoader(),new Class[]{com.badlogic.gdx.Files.class},(proxy,method,args)->{
                if(method.getName().equals("internal"))return new com.badlogic.gdx.files.FileHandle("src/main/assets/"+args[0]);
                throw new UnsupportedOperationException(method.getName());
            });
        java.lang.reflect.Field field=Badges.class.getDeclaredField("global");field.setAccessible(true);
        previousBadges=field.get(null);
        HashSet<Badges.Badge> unlocked=new HashSet<>();unlocked.add(Badges.Badge.UNLOCK_DUELIST);
        field.set(null,unlocked);
    }
    @AfterClass public static void restoreBadges() throws Exception {
        java.lang.reflect.Field field=Badges.class.getDeclaredField("global");field.setAccessible(true);field.set(null,previousBadges);
        com.badlogic.gdx.Gdx.app=previousApp;com.badlogic.gdx.Gdx.files=previousFiles;
        com.watabou.utils.GameSettings.set((com.badlogic.gdx.Preferences)previousPreferences);
    }

    static class Room extends Level {
        protected boolean build(){return true;}
        protected void createMobs(){}
        protected void createItems(){}
        @Override public void occupyCell(Char ch){}
    }
    static class Fighter extends Hero {
        List<Float> multipliers=new ArrayList<>();
        List<Integer> targets=new ArrayList<>();
        List<Boolean> surprises=new ArrayList<>();
        float spent;
        boolean hit=true;
        @Override public boolean attack(Char target,float mult,float bonus,float accuracy){
            multipliers.add(mult); targets.add(target.id()); surprises.add(RolandAbilities.surprise(this));
            if (!hit) return false;
            int damage=RolandAbilities.consumeDamage(this,Math.round(20*mult));
            target.HP-=damage;
            RolandAbilities.onDamage(target,this,damage);
            return true;
        }
        @Override public float attackDelay(){return 1f;}
        @Override public void spendAndNext(float time){spent+=time;}
    }
    Fighter hero; Room room; List<Mob> mobs;
    @Before public void setup(){
        com.watabou.noosa.Game.version="test";
        hero=new Fighter();hero.heroClass=HeroClass.DUELIST;hero.pos=65;hero.STR=30;hero.HP=hero.HT=100;
        Dungeon.hero=hero;Dungeon.depth=1;Dungeon.branch=0;
        Talent.initClassTalents(hero);
        room=new Room();room.setSize(12,12);room.mobs=new HashSet<>();
        Arrays.fill(room.passable,true);Arrays.fill(room.heroFOV,true);Arrays.fill(room.map,Terrain.EMPTY);
        Dungeon.level=room;mobs=new ArrayList<>();
        Buff.affect(hero,MeleeWeapon.Charger.class).charges=20;
    }
    @After public void cleanup(){
        com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator.clearAction();
        for(Buff b:hero.buffs())hero.remove(b);
        for(Mob m:mobs)Actor.remove(m);
        Dungeon.hero=null;Dungeon.level=null;
    }
    Mob enemy(int pos){Mob m=new Mob(){};m.pos=pos;m.HP=m.HT=100;m.alignment=Char.Alignment.ENEMY;Actor.add(m);room.mobs.add(m);mobs.add(m);return m;}
    <T extends MeleeWeapon> T equip(T w){hero.belongings.weapon=w;return w;}
    int charge(){return hero.buff(MeleeWeapon.Charger.class).charges;}
    @Test public void assignmentAndCostsMatchEveryRequestedWeapon(){
        MeleeWeapon[] weapons={new Rapier(),new Dirk(),new Quarterstaff(),new Sickle(),new Shortsword(),new HandAxe(),new Spear(),new Whip(),new RoundShield(),new Mace(),new Scimitar(),new Sword(),new Sai(),new BattleAxe(),new AssassinsBlade(),new RunicBlade(),new Flail(),new Longsword(),new Katana(),new Greatsword(),new Greataxe(),new Greatshield(),new Gauntlet(),new WarScythe(),new WarHammer(),new Glaive()};
        String[] kinds={"DURANDAL","ZELKOVA","OLDBOY","RANGA","MOOK","OLDBOY","LOGIC_PISTOL","LOGIC_PISTOL","WHEELS_ATK","MOOK","RANGA","CRYSTAL","ALLAS","WHEELS_DEF","MOOK","ALLAS","WHEELS_ATK","ZELKOVA","CRYSTAL","CRYSTAL","WHEELS_DEF","OLDBOY","ALLAS_GAUNTLET","LOGIC_SHOTGUN","MOOK","ALLAS"};
        int[] costs={1,2,2,1,2,2,3,3,4,2,1,2,1,3,2,1,4,2,2,2,3,2,2,4,2,1};
        for(int i=0;i<weapons.length;i++){
            assertEquals(kinds[i],RolandAbilities.kind(weapons[i]).name());
            assertEquals(costs[i],weapons[i].abilityChargeUse(hero,null),0);
        }
        assertFalse(equip(new Dagger()).actions(hero).contains(MeleeWeapon.AC_ABILITY));
        assertEquals(RolandAbilities.Kind.NONE,RolandAbilities.kind(new Excalibur()));
    }
    @Test public void invalidTargetsAndInsufficientChargeHaveNoSideEffects(){
        Spear w=equip(new Spear());Mob m=enemy(69);
        assertFalse(RolandAbilities.valid(w,hero,m.pos));assertEquals(20,charge());assertEquals(65,hero.pos);
        m.pos=67;room.solid[66]=true;assertFalse(RolandAbilities.valid(w,hero,m.pos));room.solid[66]=false;
        m.alignment=Char.Alignment.ALLY;assertFalse(RolandAbilities.valid(w,hero,m.pos));m.alignment=Char.Alignment.ENEMY;
        room.heroFOV[m.pos]=false;assertFalse(RolandAbilities.valid(w,hero,m.pos));room.heroFOV[m.pos]=true;
        hero.buff(MeleeWeapon.Charger.class).charges=2;assertFalse(RolandAbilities.valid(w,hero,m.pos));
        assertTrue(hero.targets.isEmpty());assertEquals(0,hero.spent,0);
    }
    @Test public void pistolHitsTwiceForOneChargePaymentAndOneTurn(){
        Spear w=equip(new Spear());Mob m=enemy(68);
        assertTrue(RolandAbilities.use(w,hero,m.pos));
        assertEquals(Arrays.asList(0.6f,0.6f),hero.multipliers);assertEquals(76,m.HP);
        assertEquals(17,charge());assertEquals(1,hero.spent,0);
    }
    @Test public void mookExtendsRangeAndBleedingRequiresAHit(){
        Mace w=equip(new Mace());Mob m=enemy(68);
        assertTrue(RolandAbilities.use(w,hero,m.pos));assertNotNull(m.buff(Bleeding.class));
        assertEquals(18,charge());assertEquals(80,m.HP);
        m.buff(Bleeding.class).detach();hero.hit=false;
        assertTrue(RolandAbilities.use(w,hero,m.pos));assertNull(m.buff(Bleeding.class));
    }
    @Test public void wheelsAndShotgunUseRequestedDamageMultipliers(){
        Mob m=enemy(66);Flail w=equip(new Flail());
        assertTrue(RolandAbilities.use(w,hero,m.pos));assertEquals(1.7f,hero.multipliers.get(0),0);assertEquals(16,charge());
        WarScythe gun=equip(new WarScythe());assertTrue(RolandAbilities.use(gun,hero,m.pos));
        assertEquals(1.3f,hero.multipliers.get(1),0);assertNotNull(m.buff(Bleeding.class));assertEquals(12,charge());
    }
    @Test public void zelkovaAndOldboyDebuffsLastFiveTurns(){
        Mob m=enemy(66);assertTrue(RolandAbilities.use(equip(new Dirk()),hero,m.pos));
        assertEquals(5,m.buff(Vulnerable.class).cooldown(),0);
        assertTrue(RolandAbilities.use(equip(new HandAxe()),hero,m.pos));
        assertEquals(5,m.buff(Daze.class).cooldown(),0);
    }
    @Test public void allasMovesOneTileAndGauntletMovesBehindWithoutAttacking(){
        Mob m=enemy(67);Sai w=equip(new Sai());assertTrue(RolandAbilities.use(w,hero,m.pos));
        assertEquals(66,hero.pos);assertEquals(19,charge());assertEquals(1,hero.targets.size());
        hero.pos=65;Gauntlet g=equip(new Gauntlet());assertTrue(RolandAbilities.use(g,hero,m.pos));
        assertEquals(68,hero.pos);assertEquals(1,hero.targets.size());assertEquals(5,hero.buff(Haste.class).cooldown(),0);
        assertEquals(17,charge());
    }
    @Test public void rangaHitsOnlyEnemiesOnStraightPathAndRejectsWalls(){
        Mob a=enemy(66),b=enemy(68),off=enemy(78);
        Sickle w=equip(new Sickle());room.solid[67]=true;assertFalse(RolandAbilities.valid(w,hero,69));room.solid[67]=false;
        assertFalse(RolandAbilities.valid(w,hero,79));
        assertTrue(RolandAbilities.use(w,hero,69));assertEquals(69,hero.pos);
        assertEquals(Arrays.asList(a.id(),b.id()),hero.targets);assertEquals(84,a.HP);assertEquals(100,off.HP);
    }
    @Test public void crystalHasFreeFirstTurnAndFreeReturnChargeSurvivesSave(){
        Sword w=equip(new Sword());Mob m=enemy(66);
        assertTrue(RolandAbilities.use(w,hero,m.pos));assertEquals(67,hero.pos);assertEquals(0,hero.spent,0);assertEquals(18,charge());
        RolandAbilities.CrystalReturn buff=hero.buff(RolandAbilities.CrystalReturn.class);
        Bundle b=new Bundle();buff.storeInBundle(b);buff.detach();
        RolandAbilities.CrystalReturn restored=new RolandAbilities.CrystalReturn();restored.restoreFromBundle(b);restored.attachTo(hero);
        Bundle wb=new Bundle();w.storeInBundle(wb);Sword loaded=new Sword();loaded.restoreFromBundle(wb);equip(loaded);
        assertEquals(0,RolandAbilities.cost(loaded,hero));
        assertEquals(2,RolandAbilities.cost(new Sword(),hero));
        assertTrue(RolandAbilities.use(loaded,hero,m.pos));assertEquals(65,hero.pos);
        assertEquals(18,charge());assertEquals(1,hero.spent,0);assertEquals(Arrays.asList(true,true),hero.surprises);
        assertNull(hero.buff(RolandAbilities.CrystalReturn.class));
    }
    @Test public void crystalAndGauntletRejectOccupiedOrUnsafeDestinations(){
        Mob m=enemy(66),blocker=enemy(67);Sword w=equip(new Sword());
        assertFalse(RolandAbilities.valid(w,hero,m.pos));blocker.pos=80;room.passable[67]=false;
        assertFalse(RolandAbilities.valid(w,hero,m.pos));room.passable[67]=true;hero.rooted=true;
        assertFalse(RolandAbilities.valid(w,hero,m.pos));
    }
    @Test public void defensiveWheelsUsesWeaponRollAndDoesNotAttack(){
        BattleAxe w=equip(new BattleAxe());
        com.watabou.utils.Random.pushGenerator(1234L);
        int expected=Math.round(w.damageRoll(hero)*0.8f);
        com.watabou.utils.Random.popGenerator();
        com.watabou.utils.Random.pushGenerator(1234L);
        try { assertTrue(RolandAbilities.use(w,hero,hero.pos)); }
        finally { com.watabou.utils.Random.popGenerator(); }
        assertEquals(expected,hero.buff(Barrier.class).shielding());
        assertEquals(17,charge());assertEquals(1,hero.spent,0);assertTrue(hero.targets.isEmpty());
    }
    @Test public void crystalBlockedReturnDoesNotChargeOrTeleportAndLevelChangeInvalidatesIt(){
        Sword w=equip(new Sword());Mob m=enemy(66);
        assertTrue(RolandAbilities.use(w,hero,m.pos));
        Mob blocker=enemy(65);assertFalse(RolandAbilities.valid(w,hero,m.pos));
        assertEquals(18,charge());assertEquals(67,hero.pos);
        blocker.pos=80;Dungeon.depth=2;
        assertEquals(2,RolandAbilities.cost(w,hero));
        hero.buff(RolandAbilities.CrystalReturn.class).act();assertNull(hero.buff(RolandAbilities.CrystalReturn.class));
    }
    @Test public void storedDamageSurvivesAMissAndIsNotRecursivelyBanked(){
        Rapier w=equip(new Rapier());Mob m=enemy(66);
        Buff.affect(hero,RolandAbilities.DurandalDamage.class).amount=15;
        hero.hit=false;RolandAbilities.use(w,hero,m.pos);
        assertEquals(15,hero.buff(RolandAbilities.DurandalDamage.class).amount);
        hero.hit=true;m.HP=25;RolandAbilities.use(w,hero,m.pos);
        assertNull(hero.buff(RolandAbilities.DurandalDamage.class));
        assertNotNull(hero.buff(RolandAbilities.DurandalStrength.class));
    }
    @Test public void durandalStoresOverkillOnceAndRefreshesNonStackingStrength(){
        Rapier w=equip(new Rapier());Mob m=enemy(66);m.HP=5;
        assertTrue(RolandAbilities.use(w,hero,m.pos));
        assertEquals(15,hero.buff(RolandAbilities.DurandalDamage.class).amount);
        assertEquals(32,hero.STR());assertEquals(10,hero.buff(RolandAbilities.DurandalStrength.class).cooldown(),0);
        m.HP=100;assertTrue(RolandAbilities.use(w,hero,m.pos));assertEquals(65,m.HP);
        assertNull(hero.buff(RolandAbilities.DurandalDamage.class));assertEquals(32,hero.STR());
        m.HP=1;RolandAbilities.use(w,hero,m.pos);assertEquals(32,hero.STR());
        Bundle b=new Bundle();hero.buff(RolandAbilities.DurandalDamage.class).storeInBundle(b);
        RolandAbilities.DurandalDamage restored=new RolandAbilities.DurandalDamage();restored.restoreFromBundle(b);
        assertEquals(19,restored.amount);
    }

    void talent(Talent t,int rank){
        for (java.util.LinkedHashMap<Talent,Integer> tier:hero.talents) {
            if(tier.containsKey(t)){tier.put(t,rank);return;}
        }
        hero.talents.get(2).put(t,rank);
    }
    com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves fixer(){
        hero.subClass=HeroSubClass.CHAMPION;
        Talent.initSubclassTalents(hero);
        com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves.grant(hero);
        return com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves.find(hero);
    }
    @Test public void durandalUpgradeGrowthIsDoubled(){
        Rapier w=new Rapier();assertEquals(2,w.min(1)-w.min(0));assertEquals(4,w.max(1)-w.max(0));
        assertEquals(10,w.min(5)-w.min(0));assertEquals(20,w.max(5)-w.max(0));
    }
    @Test public void glovesGrantOnceCollectWeaponsAndPreserveContentsInSave(){
        Sword sword=new Sword();assertTrue(sword.collect(hero.belongings.backpack));
        com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves bag=fixer();
        assertNotNull(bag);assertTrue(bag.contains(sword));
        assertFalse(bag.canHold(new com.shatteredpixel.shatteredpixeldungeon.items.food.Food()));
        com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves.grant(hero);
        assertSame(bag,com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves.find(hero));
        Bundle b=new Bundle();bag.storeInBundle(b);
        com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves restored=new com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves();
        restored.restoreFromBundle(b);assertEquals(1,restored.items.size());assertTrue(restored.items.get(0) instanceof Sword);
    }
    @Test public void resonanceUsesTierFirstAndNeverPermanentlyUpgrades(){
        com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves bag=fixer();
        Greatsword highest=new Greatsword();highest.upgrade(4);highest.collect(bag);
        Shortsword lower=new Shortsword();lower.upgrade(9);lower.collect(bag);
        Rapier receiver=equip(new Rapier());talent(Talent.TWIN_UPGRADES,1);
        assertEquals(4,receiver.buffedLvl());assertEquals(0,receiver.level());assertEquals(9,lower.buffedLvl());
        Longsword tier4=new Longsword();tier4.collect(bag);assertEquals(0,tier4.buffedLvl());
        talent(Talent.TWIN_UPGRADES,2);assertEquals(4,tier4.buffedLvl());
        Greatshield equal=new Greatshield();equal.collect(bag);assertEquals(0,equal.buffedLvl());
        talent(Talent.TWIN_UPGRADES,3);assertEquals(4,equal.buffedLvl());
        highest.detachAll(bag);assertEquals(0,receiver.buffedLvl());
    }
    @Test public void gloveEquipmentSwapCostsNoTurnAndCursesDoNotLoseWeapons(){
        com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves bag=fixer();
        Sword old=equip(new Sword());Rapier next=new Rapier();next.collect(bag);
        assertTrue(next.doEquip(hero));assertSame(next,hero.belongings.weapon);assertTrue(bag.contains(old));assertFalse(bag.contains(next));assertEquals(0,hero.spent,0);
        next.cursed=true;assertFalse(old.doEquip(hero));assertSame(next,hero.belongings.weapon);assertTrue(bag.contains(old));
    }
    @Test public void staggerThresholdsAndOncePerEnemySurviveSave(){
        talent(Talent.SWIFT_EQUIP,1);Mob m=enemy(66);m.HP=34;
        RolandAbilities.stagger(m,hero,1);assertNull(m.buff(Paralysis.class));
        m.HP=33;RolandAbilities.stagger(m,hero,1);assertEquals(1,m.buff(Paralysis.class).cooldown(),0);
        Bundle b=new Bundle();m.buff(RolandAbilities.StaggerUsed.class).storeInBundle(b);
        m.buff(RolandAbilities.StaggerUsed.class).detach();m.buff(Paralysis.class).detach();
        RolandAbilities.StaggerUsed marker=new RolandAbilities.StaggerUsed();marker.restoreFromBundle(b);marker.attachTo(m);
        m.HP=10;RolandAbilities.stagger(m,hero,1);assertNull(m.buff(Paralysis.class));
        talent(Talent.SWIFT_EQUIP,2);Mob second=enemy(67);second.HP=50;RolandAbilities.stagger(second,hero,1);assertEquals(2,second.buff(Paralysis.class).cooldown(),0);
    }
    @Test public void bossesReceiveVulnerabilityInsteadOfStaggerParalysis(){
        talent(Talent.SWIFT_EQUIP,2);
        Mob boss=new Mob(){ {properties.add(Char.Property.BOSS);} };boss.HP=50;boss.HT=100;boss.alignment=Char.Alignment.ENEMY;mobs.add(boss);
        RolandAbilities.stagger(boss,hero,1);assertNull(boss.buff(Paralysis.class));assertEquals(2,boss.buff(Vulnerable.class).cooldown(),0);
    }
    void completeFamilies(){for(RolandAbilities.Kind k:RolandAbilities.Kind.values())RolandAbilities.recordFamily(hero,k);}
    @Test public void furiosoCountsNineFamiliesNotVariantsAndPersists(){
        fixer();talent(Talent.COMBINED_LETHALITY,1);
        RolandAbilities.recordFamily(hero,RolandAbilities.Kind.WHEELS_ATK);
        RolandAbilities.recordFamily(hero,RolandAbilities.Kind.WHEELS_DEF);assertEquals(1,RolandAbilities.furiosoCount(hero));
        completeFamilies();assertEquals(9,RolandAbilities.furiosoCount(hero));assertTrue(RolandAbilities.furiosoReady(hero));
        Bundle b=new Bundle();hero.buff(RolandAbilities.FuriosoProgress.class).storeInBundle(b);hero.buff(RolandAbilities.FuriosoProgress.class).detach();
        RolandAbilities.FuriosoProgress restored=new RolandAbilities.FuriosoProgress();restored.restoreFromBundle(b);restored.attachTo(hero);assertTrue(RolandAbilities.furiosoReady(hero));
        hero.subClass=HeroSubClass.MONK;assertFalse(RolandAbilities.furiosoReady(hero));
    }
    @Test public void furiosoRejectsInvalidTargetsAndConsumesOnlyRecordAndOneTurn(){
        fixer();talent(Talent.COMBINED_LETHALITY,3);equip(new Rapier());completeFamilies();Mob m=enemy(68);
        assertFalse(RolandAbilities.useFurioso(hero,m.pos));assertTrue(RolandAbilities.furiosoReady(hero));
        m.pos=66;hero.buff(MeleeWeapon.Charger.class).charges=0;
        assertTrue(RolandAbilities.useFurioso(hero,m.pos));assertEquals(3f,hero.multipliers.get(0),0);
        assertEquals(0,charge());assertEquals(0,RolandAbilities.furiosoCount(hero));assertEquals(1,hero.spent,0);
        assertEquals(4,m.buff(Vulnerable.class).cooldown(),0);assertNotNull(m.buff(Daze.class));assertNotNull(m.buff(Bleeding.class));
        assertEquals(5,hero.buff(Haste.class).cooldown(),0);assertFalse(RolandAbilities.useFurioso(hero,m.pos));
    }

    @Test public void fullGlovesRejectRemovalButAllowSwapWithoutItemLoss(){
        com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves bag=fixer();
        Rapier equipped=equip(new Rapier());
        for(int i=0;i<30;i++)assertTrue(new Rapier().collect(bag));
        assertFalse(equipped.doUnequip(hero,true));assertSame(equipped,hero.belongings.weapon);
        Rapier replacement=(Rapier)bag.items.get(0);
        assertTrue(replacement.doEquip(hero));assertSame(replacement,hero.belongings.weapon);
        assertEquals(30,bag.items.size());assertTrue(bag.contains(equipped));assertEquals(0,hero.spent,0);
    }
    @Test public void legacySecondaryWeaponMigratesIntoGloves(){
        Rapier secondary=new Rapier();hero.belongings.secondWep=secondary;
        com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves bag=fixer();
        assertNull(hero.belongings.secondWep);assertTrue(bag.contains(secondary));
    }

    @Test public void grantDuringLostInventoryDoesNotDiscardStoredWeapons(){
        Rapier weapon=new Rapier();assertTrue(weapon.collect(hero.belongings.backpack));
        Buff.affect(hero,LostInventory.class);
        com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves bag=fixer();
        assertNotNull(bag);assertTrue(bag.contains(weapon));
        assertFalse(com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves.enabled(hero));
        hero.buff(LostInventory.class).detach();
        assertTrue(com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves.enabled(hero));
        assertTrue(bag.contains(weapon));
    }

    @Test public void gloveQuickListIncludesEquippedWeaponWithoutStoringOrDuplicatingIt(){
        com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves bag=fixer();
        Rapier equipped=equip(new Rapier());
        assertTrue(bag.items.isEmpty());assertEquals(Arrays.asList(equipped),bag.quickUseItems(hero));
        Sword stored=new Sword();stored.collect(bag);
        assertEquals(2,bag.quickUseItems(hero).size());assertEquals(1,bag.items.size());
        assertFalse(bag.contains(equipped));
        assertTrue(stored.doEquip(hero));
        assertEquals(2,bag.quickUseItems(hero).size());assertTrue(bag.quickUseItems(hero).contains(equipped));
        assertTrue(bag.quickUseItems(hero).contains(stored));assertFalse(bag.contains(stored));
    }
    @Test public void glovesDisplayTheSameSharedChargeAsTheEquippedWeapon(){
        com.shatteredpixel.shatteredpixeldungeon.items.bags.SilenceGloves bag=fixer();
        Rapier equipped=equip(new Rapier());hero.lvl=17;
        MeleeWeapon.Charger charger=hero.buff(MeleeWeapon.Charger.class);charger.charges=3;
        assertEquals("3/7",bag.status());assertEquals(equipped.status(),bag.status());
        charger.gainCharge(1);assertEquals("4/7",bag.status());
        charger.charges=0;assertEquals("0/7",bag.status());
    }
    @Test public void furiosoIndicatorOnlyAppearsWhenReadyAndClearsImmediatelyAfterUse(){
        fixer();talent(Talent.COMBINED_LETHALITY,1);equip(new Rapier());
        MeleeWeapon.Charger charger=hero.buff(MeleeWeapon.Charger.class);
        charger.fx(true);assertNull(com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator.action);
        RolandAbilities.recordFamily(hero,RolandAbilities.Kind.WHEELS_ATK);
        charger.refreshActionIndicator();assertNull(com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator.action);
        completeFamilies();assertSame(charger,com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator.action);
        Mob m=enemy(66);assertTrue(RolandAbilities.useFurioso(hero,m.pos));
        assertNull(com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator.action);
        charger.fx(true);assertNull(com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator.action);
    }

    @Test public void developerChargeFillsNineFamiliesWithoutChangingTalentsOrSpendingTurns(){
        fixer();talent(Talent.COMBINED_LETHALITY,1);
        com.shatteredpixel.shatteredpixeldungeon.items.FuriosoCharge item=new com.shatteredpixel.shatteredpixeldungeon.items.FuriosoCharge();
        item.quantity(2);assertTrue(item.collect(hero.belongings.backpack));
        SPDSettings.developerMode(false);assertFalse(item.charge(hero));assertEquals(2,item.quantity());
        SPDSettings.developerMode(true);
        try {
            assertTrue(item.charge(hero));assertEquals(9,RolandAbilities.furiosoCount(hero));
            assertEquals(1,item.quantity());assertEquals(0,hero.spent,0);
            assertEquals(1,hero.pointsInTalent(Talent.COMBINED_LETHALITY));
            talent(Talent.COMBINED_LETHALITY,0);assertFalse(item.charge(hero));assertEquals(1,item.quantity());
        } finally {SPDSettings.developerMode(false);}
    }
}
