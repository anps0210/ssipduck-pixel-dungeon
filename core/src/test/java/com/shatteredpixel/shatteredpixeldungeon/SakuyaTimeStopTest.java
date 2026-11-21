package com.shatteredpixel.shatteredpixeldungeon;

import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.scrolls.ScrollOfIdentify;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.watabou.utils.Bundle;
import org.junit.*;
import java.util.*;
import static org.junit.Assert.*;

public class SakuyaTimeStopTest {
    static class Room extends Level {
        protected boolean build() { return true; }
        protected void createMobs() {}
        protected void createItems() {}
    }
    static class RecordingHero extends Hero {
        final List<Integer> strikes = new ArrayList<>();
        final List<Integer> powers = new ArrayList<>();
        @Override public boolean attack(Char enemy, float mult, float bonus, float accuracy) {
            strikes.add(enemy.id());
            powers.add(buff(Preparation.class).attackLevel());
            return true;
        }
    }
    RecordingHero hero;
    Room room;
    final List<Mob> mobs = new ArrayList<>();
    @Before public void setup() {
        hero = new RecordingHero(); hero.heroClass = HeroClass.ROGUE;
        hero.subClass = HeroSubClass.ASSASSIN; hero.pos = 44;
        Talent.initClassTalents(hero);
        Dungeon.hero = hero; Dungeon.depth = 1; Dungeon.branch = 0;
        room = new Room(); room.setSize(10,10); room.mobs = new HashSet<>();
        Arrays.fill(room.map,Terrain.EMPTY); Arrays.fill(room.passable,true); Arrays.fill(room.heroFOV,true);
        Dungeon.level = room;
    }
    @After public void cleanup() {
        for (com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff b:hero.buffs()) hero.remove(b);
        for(Mob m:mobs) Actor.remove(m);
        ActionIndicator.action = null;
        Dungeon.hero = null; Dungeon.level = null;
    }
    Mob enemy(int pos) {
        Mob m = new Mob() {}; m.HP = m.HT = 20; m.pos = pos;
        m.alignment = Char.Alignment.ENEMY; Actor.add(m); room.mobs.add(m); mobs.add(m); return m;
    }
    static class SelectionStop extends SakuyaTimeStop {
        int confirmations, prompts;
        @Override public void confirmEnd() { confirmations++; }
        @Override public void promptTargets() { prompts++; }
        com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector.Listener selector() {
            return createTargetSelector();
        }
    }
    @Test public void targetButtonOnlyAppearsForCheckMaidIncludingRestoredStops() {
        for (HeroSubClass subclass : new HeroSubClass[]{HeroSubClass.NONE, HeroSubClass.FREERUNNER, HeroSubClass.ASSASSIN}) {
            hero.subClass = subclass;
            SakuyaTimeStop stop = SakuyaTimeStop.start(hero,5,null);
            assertEquals(subclass == HeroSubClass.ASSASSIN, ActionIndicator.action == stop);
            Bundle b = new Bundle(); stop.storeInBundle(b); hero.remove(stop);
            ActionIndicator.clearAction();
            SakuyaTimeStop restored = new SakuyaTimeStop(); restored.restoreFromBundle(b);
            assertNull(ActionIndicator.action);
            // Hero restores buffs before restoring its subclass.
            hero.subClass = HeroSubClass.NONE; restored.attachTo(hero);
            hero.subClass = subclass; restored.refreshAction();
            assertEquals(subclass == HeroSubClass.ASSASSIN, ActionIndicator.action == restored);
            if (subclass != HeroSubClass.ASSASSIN) {
                restored.doAction();
                assertNotNull(hero.buff(SakuyaTimeStop.class));
                assertFalse(restored.pending());
            }
            restored.finish();
        }
    }
    @Test public void selectionCleanupDoesNotOpenConfirmationOrEndTimeStop() {
        SakuyaTimeStop initial = SakuyaTimeStop.start(hero,5,null);
        Bundle b = new Bundle(); initial.storeInBundle(b); hero.remove(initial);
        SelectionStop stop = new SelectionStop(); stop.restoreFromBundle(b); stop.attachTo(hero);
        Mob m = enemy(45);
        com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector.Listener listener = stop.selector();
        listener.onSelect(m.pos);
        listener.onSelect(null); // GameScene.ready() replacing the completed listener.
        listener.onSelect(null);
        assertEquals(1,stop.markedCount());
        assertEquals(0,stop.confirmations); assertEquals(0,stop.prompts);
        assertEquals(5f,stop.remaining(),0f); assertFalse(stop.pending());
        listener = stop.selector();
        listener.onSelect(null); listener.onSelect(null);
        assertEquals(1,stop.confirmations); // Actual cancellation is handled exactly once.
    }
    @Test public void expiredSelectionReopensOnceWithoutSpuriousCancellation() {
        SelectionStop stop = new SelectionStop(); stop.attachTo(hero); stop.requestEnd();
        Mob m = enemy(45);
        com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector.Listener listener = stop.selector();
        listener.onSelect(m.pos); listener.onSelect(null);
        assertTrue(stop.pending()); assertEquals(1,stop.markedCount());
        assertEquals(1,stop.prompts); assertEquals(0,stop.confirmations);
    }
    @Test public void marksAreFreeUniqueAndResolveFromOriginalPositionOnlyAtEnd() {
        SakuyaTimeStop stop = SakuyaTimeStop.start(hero,10,null);
        Mob a=enemy(45),b=enemy(46);
        assertTrue(stop.mark(a)); assertFalse(stop.mark(a)); assertFalse(stop.mark(b));
        assertEquals(10f,stop.remaining(),0f); assertTrue(hero.strikes.isEmpty());
        for(int i=0;i<3;i++){stop.waitedTurn();stop.processTime(1);}
        assertTrue(stop.mark(b)); assertEquals(0,stop.markedWaits(a.id())); assertEquals(3,stop.markedWaits(b.id()));
        stop.finish();
        assertEquals(Arrays.asList(a.id(),b.id()),hero.strikes);
        assertEquals(Arrays.asList(1,2),hero.powers);
        assertEquals(44,hero.pos); assertNull(hero.buff(SakuyaTimeStop.class));
        stop.finish(); assertEquals(2,hero.strikes.size());
    }
    @Test public void budgetExpiresIntoSelectionAndMovementDoesNotPrepare() {
        SakuyaTimeStop stop=SakuyaTimeStop.start(hero,3,null);
        float time=hero.cooldown();
        hero.spendConstant(2); assertEquals(time,hero.cooldown(),0f);
        assertEquals(0,stop.waited()); assertEquals(1f,stop.remaining(),0f);
        stop.waitedTurn();hero.spendConstant(1);
        assertTrue(stop.pending()); assertEquals(1,stop.waited());
        stop.waitedTurn();assertEquals(1,stop.waited());
        assertNotNull(hero.buff(SakuyaTimeStop.class));
        stop.finish();assertNull(hero.buff(SakuyaTimeStop.class));
    }
    @Test public void pendingSelectionAndMarkedStagesSurviveSave() {
        SakuyaTimeStop stop=SakuyaTimeStop.start(hero,1,null);Mob m=enemy(45);
        stop.waitedTurn();assertTrue(stop.mark(m));stop.processTime(1);
        Bundle b=new Bundle();stop.storeInBundle(b);hero.remove(stop);
        SakuyaTimeStop restored=new SakuyaTimeStop();restored.restoreFromBundle(b);restored.attachTo(hero);
        assertTrue(restored.pending());assertEquals(1,restored.markedWaits(m.id()));
        assertFalse(restored.mark(m));restored.finish();assertEquals(Arrays.asList(m.id()),hero.strikes);
    }
    @Test public void hiddenAlliedAndBlockedTargetsAreRejected() {
        SakuyaTimeStop stop=SakuyaTimeStop.start(hero,10,null);Mob m=enemy(45);
        room.heroFOV[m.pos]=false;assertFalse(stop.mark(m));room.heroFOV[m.pos]=true;
        m.alignment=Char.Alignment.ALLY;assertFalse(stop.mark(m));m.alignment=Char.Alignment.ENEMY;
        for(int i=0;i<9;i++)stop.waitedTurn();m.pos=47;
        room.solid[46]=true;assertFalse(stop.mark(m));room.solid[46]=false;
        assertTrue(stop.mark(m));
    }
    @Test public void scrollTalentHasFixedDurationCooldownAndNoRefresh() {
        hero.talents.get(1).put(Talent.INSCRIBED_STEALTH,2);
        Talent.onScrollUsed(hero,hero.pos,2f,ScrollOfIdentify.class);
        SakuyaTimeStop stop=hero.buff(SakuyaTimeStop.class);
        assertEquals(5f,stop.remaining(),0f);
        assertNotNull(hero.buff(SakuyaTimeStop.ScrollCooldown.class));
        stop.processTime(2);
        Talent.onScrollUsed(hero,hero.pos,1f,ScrollOfIdentify.class);
        assertEquals(3f,stop.remaining(),0f);
        stop.finish();Talent.onScrollUsed(hero,hero.pos,1f,ScrollOfIdentify.class);
        assertNull(hero.buff(SakuyaTimeStop.class));
    }
    @Test public void firstStrikeReplacesOldDamageBonus() {
        hero.talents.get(0).put(Talent.SUCKER_PUNCH,1);
        SakuyaTimeStop stop=SakuyaTimeStop.start(hero,5,null);
        Mob m=enemy(45);
        assertNotNull(hero.buff(SakuyaTimeStop.FirstStrike.class));
        stop.finish();
        assertTrue(m.surprisedBy(hero));
        assertEquals(10,Talent.onAttackProc(hero,m,10));
        assertNull(hero.buff(SakuyaTimeStop.FirstStrike.class));
        assertNull(m.buff(Talent.SuckerPunchTracker.class));
    }
    @Test public void secondRankGrantsTwoStrikesAndSavesRemainingCount() {
        hero.talents.get(0).put(Talent.SUCKER_PUNCH,2);
        SakuyaTimeStop.start(hero,5,null).finish();
        Mob m=enemy(45);
        assertTrue(m.surprisedBy(hero));
        assertEquals(10,Talent.onAttackProc(hero,m,10));
        SakuyaTimeStop.FirstStrike strike=hero.buff(SakuyaTimeStop.FirstStrike.class);
        assertNotNull(strike);
        Bundle b=new Bundle();strike.storeInBundle(b);hero.remove(strike);
        SakuyaTimeStop.FirstStrike restored=new SakuyaTimeStop.FirstStrike();
        restored.restoreFromBundle(b);restored.attachTo(hero);
        assertTrue(m.surprisedBy(hero));
        assertEquals(10,Talent.onAttackProc(hero,m,10));
        assertNull(hero.buff(SakuyaTimeStop.FirstStrike.class));
        assertFalse(SakuyaTimeStop.surprise(hero));
    }
    @Test public void protectionAccumulatesOnStoppedTurnsAndRespectsCap() {
        hero.talents.get(0).put(Talent.PROTECTIVE_SHADOWS,2);
        SakuyaTimeStop stop=SakuyaTimeStop.start(hero,10,null);
        stop.processTime(4);
        assertEquals(4,hero.buff(Barrier.class).shielding());
        stop.processTime(4);
        assertEquals(5,hero.buff(Barrier.class).shielding());
    }
    @Test public void invisibleAloneNoLongerPreparesCheckMaid() {
        Buff.affect(hero,Invisibility.class,10f);
        assertNull(hero.buff(Preparation.class));
        assertFalse(SakuyaTimeStop.stealth(hero));
        SakuyaTimeStop.start(hero,3,null);
        assertTrue(SakuyaTimeStop.stealth(hero));
    }
    @Test public void watchChargeAndPendingSourceSurviveSave() {
        class Watch extends com.shatteredpixel.shatteredpixeldungeon.items.artifacts.TimekeepersHourglass {
            void setCharges(int n) { charge=n; }
            int charges() { return charge; }
        }
        Watch watch=new Watch();watch.setCharges(2);hero.belongings.artifact=watch;
        // Activation reserves the first charge before starting its two-turn segment.
        watch.consumeStoppedCharge();
        SakuyaTimeStop stop=SakuyaTimeStop.start(hero,4,watch);
        stop.processTime(2);assertEquals(1,watch.charges());
        Bundle b=new Bundle();stop.storeInBundle(b);hero.remove(stop);
        SakuyaTimeStop restored=new SakuyaTimeStop();restored.restoreFromBundle(b);restored.attachTo(hero);
        assertTrue(restored.fromWatch());
        restored.processTime(1);assertEquals(0,watch.charges());
        restored.processTime(1);assertTrue(restored.pending());assertEquals(0,watch.charges());
    }
}
