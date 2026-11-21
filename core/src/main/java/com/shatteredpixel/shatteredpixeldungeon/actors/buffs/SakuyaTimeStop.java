package com.shatteredpixel.shatteredpixeldungeon.actors.buffs;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.watabou.noosa.particles.Emitter;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroSubClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.TimekeepersHourglass;
import com.shatteredpixel.shatteredpixeldungeon.mechanics.Ballistica;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.ui.ActionIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.BuffIndicator;
import com.shatteredpixel.shatteredpixeldungeon.ui.HeroIcon;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.watabou.noosa.Game;
import com.watabou.noosa.Visual;
import com.watabou.utils.Bundle;
import java.util.ArrayList;
import java.util.LinkedHashMap;

/** Hero actions consume this budget instead of advancing the world's actor clock. */
public class SakuyaTimeStop extends Buff implements ActionIndicator.Action {
    { type = buffType.POSITIVE; }
    private float remaining, elapsed;
    private int waited, startDepth, startBranch, watchId = -1;
    private float watchPaid = 2f;
    private boolean finishing, resolving, selecting;
    private final LinkedHashMap<Integer, Integer> marks = new LinkedHashMap<>();
    private final ArrayList<Integer> presses = new ArrayList<>();

    public static boolean active(Char ch) { return ch != null && ch.buff(SakuyaTimeStop.class) != null; }
    public static boolean surprise(Char ch) {
        if (ch == null) return false;
        SakuyaTimeStop stop = ch.buff(SakuyaTimeStop.class);
        return ch.buff(FirstStrike.class) != null || (stop != null && stop.resolving && stop.checkmate());
    }
    public static boolean stealth(Char ch) {
        return ch instanceof Hero && ((Hero)ch).heroClass == HeroClass.ROGUE ? active(ch) : ch.invisible > 0;
    }
    public static SakuyaTimeStop start(Hero hero, float duration, TimekeepersHourglass watch) {
        SakuyaTimeStop stop = hero.buff(SakuyaTimeStop.class);
        if (stop != null) return stop; // No stacking, budget reset, or duplicate pending strikes.
        stop = new SakuyaTimeStop();
        stop.remaining = duration;
        stop.startDepth = Dungeon.depth; stop.startBranch = Dungeon.branch;
        // An item is not an Actor; use a source flag, and resolve the unique watch via belongings.
        stop.watchId = watch == null ? -1 : 1;
        stop.attachTo(hero);
        if (hero.hasTalent(Talent.SUCKER_PUNCH)) Buff.affect(hero, FirstStrike.class).strikes = hero.pointsInTalent(Talent.SUCKER_PUNCH);
        if (hero.hasTalent(Talent.PROTECTIVE_SHADOWS)) Buff.affect(hero, Talent.ProtectiveShadowsTracker.class);
        ActionIndicator.setAction(stop);
        return stop;
    }
    public boolean fromWatch() { return watchId == 1; }
    public float remaining() { return remaining; }
    public int waited() { return waited; }
    public int markedCount() { return marks.size(); }
    public int markedWaits(int id) { return marks.containsKey(id) ? marks.get(id) : -1; }
    public boolean pending() { return finishing && !resolving; }
    public boolean checkmate() { return target instanceof Hero && ((Hero)target).subClass == HeroSubClass.ASSASSIN; }
    public void waitedTurn() { if (!finishing && checkmate()) waited++; }

    public void processTime(float time) {
        if (resolving || finishing || time <= 0) return;
        float used = Math.min(time, remaining);
        int before = (int)elapsed;
        elapsed += used; remaining = Math.max(0, remaining-used);
        if (fromWatch()) {
            watchPaid -= used;
            TimekeepersHourglass watch = ((Hero)target).belongings.getItem(TimekeepersHourglass.class);
            while (watchPaid < -0.001f && watch != null) {
                watch.consumeStoppedCharge(); watchPaid += 2f;
            }
        }
        for (int tick = before; tick < (int)elapsed; tick++) {
            Talent.ProtectiveShadowsTracker protection = target.buff(Talent.ProtectiveShadowsTracker.class);
            if (protection != null) protection.stoppedTurn();
            Momentum momentum = target.buff(Momentum.class);
            if (momentum != null) momentum.onStoppedTurn();
        }
        if (remaining <= 0) requestEnd();
        Item.updateQuickslot();
    }
    public void requestEnd() {
        if (resolving) return;
        if (checkmate()) {
            finishing = true;
            ((Hero)target).resting = false;
            ((Hero)target).curAction = null;
            ActionIndicator.setAction(this);
            target.next();
        } else finish();
    }
    public boolean mark(Char enemy) {
        Hero hero = (Hero)target;
        if (!checkmate() || enemy == null || !enemy.isAlive() || enemy.alignment != Char.Alignment.ENEMY
                || hero.isCharmedBy(enemy) || marks.containsKey(enemy.id()) || Dungeon.level == null
                || !Dungeon.level.heroFOV[enemy.pos]
                || Dungeon.level.distance(hero.pos, enemy.pos) > Preparation.AttackLevel.getLvl(waited).blinkDistance()
                || new Ballistica(hero.pos, enemy.pos, Ballistica.STOP_SOLID | Ballistica.STOP_TARGET).collisionPos != enemy.pos) return false;
        marks.put(enemy.id(), waited);
        if (enemy.sprite != null) enemy.sprite.showStatus(CharSprite.WARNING, Messages.get(this, "marked"));
        return true;
    }
    public void promptTargets() {
        if (selecting || resolving || target == null || !checkmate()) return;
        selecting = true;
        Game.runOnRenderThread(() -> GameScene.selectCell(new CellSelector.Listener() {
            public void onSelect(Integer cell) {
                selecting = false;
                if (cell == null) { confirmEnd(); return; }
                if (!mark(Actor.findChar(cell))) GLog.w(Messages.get(SakuyaTimeStop.this, "invalid"));
                // User can keep waiting/moving after a manual mark; ending phase stays in selection.
                if (finishing) promptTargets();
            }
            public String prompt() {
                return Messages.get(SakuyaTimeStop.this, "prompt",
                        Preparation.AttackLevel.getLvl(waited).blinkDistance(), marks.size());
            }
        }));
    }
    public void confirmEnd() {
        Game.runOnRenderThread(() -> GameScene.show(new WndOptions(
                Messages.get(this, "name"), Messages.get(this, "confirm"),
                Messages.get(this, "continue"), Messages.get(this, "finish")) {
            protected void onSelect(int index) { if (index == 1) finish(); else promptTargets(); }
        }));
    }
    public void finish() {
        if (resolving || target == null) return;
        resolving = true;
        Hero hero = (Hero)target;
        if (Dungeon.depth == startDepth && Dungeon.branch == startBranch && hero.isAlive()) {
            for (java.util.Map.Entry<Integer,Integer> mark : marks.entrySet()) {
                Actor actor = Actor.findById(mark.getKey());
                if (!(actor instanceof Char)) continue;
                Char enemy = (Char)actor;
                if (!enemy.isAlive() || enemy.alignment != Char.Alignment.ENEMY || hero.isCharmedBy(enemy)) continue;
                Preparation prep = Buff.affect(hero, Preparation.class);
                prep.setCheckmateTurns(mark.getValue());
                // Same accuracy, damage rolls, weapon procs, armour and execution threshold as assassination.
                hero.attack(enemy);
                prep.detach();
                if (!hero.isAlive()) break;
            }
        }
        marks.clear();
        super.detach();
        ActionIndicator.clearAction(this);
        if (Dungeon.level != null && Dungeon.depth == startDepth && Dungeon.branch == startBranch) {
            for (int cell : presses) {
                if (Dungeon.level.plants.get(cell) != null) Dungeon.level.plants.get(cell).trigger();
                if (Dungeon.level.traps.get(cell) != null) Dungeon.level.traps.get(cell).trigger();
            }
        }
        presses.clear();
        hero.resting = false; hero.curAction = null;
        hero.next();
        Item.updateQuickslot();
    }
    @Override public void detach() { finish(); }
    public void delayPress(int cell) { if (!presses.contains(cell)) presses.add(cell); }
    public void beforeTransition() {
        if (Dungeon.level != null) {
            for (int cell : presses) {
                com.shatteredpixel.shatteredpixeldungeon.plants.Plant plant = Dungeon.level.plants.get(cell);
                if (plant != null && !(plant instanceof com.shatteredpixel.shatteredpixeldungeon.plants.Rotberry))
                    Dungeon.level.uproot(cell);
                com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap trap = Dungeon.level.traps.get(cell);
                if (trap != null && trap.disarmedByActivation) trap.disarm();
            }
        }
        presses.clear(); finish();
    }
    @Override public void fx(boolean on) {
        if (!(target instanceof Hero)) return;
        Emitter.freezeEmitters = on;
        if (Dungeon.level == null) return;
        for (Mob mob : Dungeon.level.mobs.toArray(new Mob[0])) {
            if (mob.sprite == null) continue;
            if (on) mob.sprite.add(CharSprite.State.PARALYSED);
            else if (mob.paralysed <= 0) mob.sprite.remove(CharSprite.State.PARALYSED);
        }
    }
    @Override public int icon() { return BuffIndicator.TIME; }
    @Override public String desc() { return Messages.get(this, "desc", remaining, waited, marks.size()); }
    @Override public String actionName() { return Messages.get(this, "action"); }
    @Override public int actionIcon() { return HeroIcon.PREPARATION; }
    @Override public Visual primaryVisual() { return new HeroIcon(this); }
    @Override public int indicatorColor() { return 0x99CCFF; }
    @Override public void doAction() { if (checkmate()) promptTargets(); else requestEnd(); }
    @Override public void storeInBundle(Bundle b) {
        super.storeInBundle(b);
        b.put("remaining",remaining); b.put("elapsed",elapsed); b.put("waited",waited);
        b.put("depth",startDepth); b.put("branch",startBranch); b.put("watch",watchId);
        b.put("paid",watchPaid); b.put("finishing",finishing);
        int[] ids = new int[marks.size()], stages = new int[marks.size()];
        int index = 0;
        for (java.util.Map.Entry<Integer,Integer> mark : marks.entrySet()) {
            ids[index] = mark.getKey(); stages[index++] = mark.getValue();
        }
        int[] cells = new int[presses.size()];
        for (int i = 0; i < cells.length; i++) cells[i] = presses.get(i);
        b.put("ids",ids); b.put("stages",stages); b.put("presses",cells);
    }
    @Override public void restoreFromBundle(Bundle b) {
        super.restoreFromBundle(b);
        remaining=b.getFloat("remaining"); elapsed=b.getFloat("elapsed"); waited=b.getInt("waited");
        startDepth=b.getInt("depth"); startBranch=b.getInt("branch"); watchId=b.getInt("watch");
        watchPaid=b.getFloat("paid"); finishing=b.getBoolean("finishing");
        int[] ids=b.getIntArray("ids"), stages=b.getIntArray("stages");
        for(int i=0;i<Math.min(ids.length,stages.length);i++) marks.put(ids[i],stages[i]);
        for(int cell:b.getIntArray("presses")) presses.add(cell);
        ActionIndicator.setAction(this);
    }
    public static class FirstStrike extends Buff {
        private int strikes = 1;
        public void consume() { if (--strikes <= 0) detach(); }
        @Override public void storeInBundle(Bundle b) {
            super.storeInBundle(b);
            b.put("strikes", strikes);
        }
        @Override public void restoreFromBundle(Bundle b) {
            super.restoreFromBundle(b);
            // Older saves granted one strike and did not store a counter.
            strikes = b.contains("strikes") ? b.getInt("strikes") : 1;
        }
    }
    public static class ScrollCooldown extends FlavourBuff {
        @Override public int icon() { return BuffIndicator.TIME; }
    }
}
