package com.shatteredpixel.shatteredpixeldungeon.windows;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.items.Gold;
import com.shatteredpixel.shatteredpixeldungeon.items.EnergyCrystal;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.Artifact;
import com.shatteredpixel.shatteredpixeldungeon.items.artifacts.ChaliceOfBlood;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.WornShortsword;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.CellSelector;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.utils.DeveloperCatalog;
import com.shatteredpixel.shatteredpixeldungeon.utils.GLog;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Locale;

public class WndDeveloper extends WndOptions {
    private static String msg(String key) { return Messages.get(WndDeveloper.class, key); }
    private static boolean allowed() {
        return SPDSettings.developerMode() && Dungeon.hero != null && Dungeon.hero.isAlive() && Dungeon.level != null;
    }
    public WndDeveloper() {
        super(msg("title"), msg("body"), msg("items"), msg("mobs"), msg("kit"), msg("energy"), msg("floor"), msg("mask"), msg("furioso"));
    }
    @Override protected void onSelect(int index) {
        if (!allowed()) return;
        if (index < 2) search(index == 0);
        else if (index == 2) {
            WornShortsword sword = new WornShortsword();
            for (int i=0;i<5;i++) { sword.upgrade(); sword.recordScrollUpgrade(); }
            give(sword);
            ChaliceOfBlood chalice = new ChaliceOfBlood(); chalice.upgrade(10); give(chalice);
            Dungeon.energy += 50;
            GLog.p(msg("kit_done"));
        } else if(index==3) { Dungeon.energy += 50; GLog.p(msg("energy_done")); }
        else if(index==4) chooseFloor();
        else if(index==5) give(new com.shatteredpixel.shatteredpixeldungeon.items.TengusMask());
        else if(index==6) give(new com.shatteredpixel.shatteredpixeldungeon.items.FuriosoCharge());
    }
    public static int parseFloor(String value) {
        int depth=Integer.parseInt(value.trim());
        if(depth<1 || depth>26)throw new IllegalArgumentException();
        return depth;
    }
    private static void chooseFloor() {
        GameScene.show(new WndTextInput(msg("floor"),msg("floor_hint"),Integer.toString(Dungeon.depth),2,false,msg("go"),msg("cancel")) {
            @Override public void onSelect(boolean positive,String text) {
                if(!positive || !allowed())return;
                final int depth;
                try {depth=parseFloor(text);} catch(IllegalArgumentException e){GLog.w(msg("floor_invalid"));return;}
                if(depth==Dungeon.depth && Dungeon.branch==0)return;
                Dungeon.hero.curAction=null;Dungeon.hero.resting=false;
                com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene.returnDepth=depth;
                com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene.returnBranch=0;
                com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene.returnPos=-1;
                com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene.mode=com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene.Mode.RETURN;
                com.shatteredpixel.shatteredpixeldungeon.ShatteredPixelDungeon.switchScene(com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene.class);
            }
        });
    }
    private static void search(final boolean items) {
        GameScene.show(new WndTextInput(msg(items ? "items" : "mobs"), msg("search_hint"), "", 40, false, msg("search"), msg("cancel")) {
            @Override public void onSelect(boolean positive, String text) {
                if (!positive || !allowed()) return;
                ArrayList<Class<?>> found = new ArrayList<>();
                String q = text.trim().toLowerCase(Locale.ROOT);
                for (Class<?> type : items ? DeveloperCatalog.ITEMS : DeveloperCatalog.MOBS) {
                    if (Modifier.isAbstract(type.getModifiers())) continue;
                    try { type.getConstructor(); } catch (NoSuchMethodException e) { continue; }
                    if (label(type).toLowerCase(Locale.ROOT).contains(q) || type.getSimpleName().toLowerCase(Locale.ROOT).contains(q)) found.add(type);
                }
                found.sort((a,b) -> label(a).compareTo(label(b)));
                page(items, found, 0);
            }
        });
    }
    private static String label(Class<?> type) {
        String label = Messages.get(type, "name");
        return label.equals(Messages.NO_TEXT_FOUND) ? type.getSimpleName() : Messages.titleCase(label);
    }
    private static void page(final boolean items, final ArrayList<Class<?>> found, final int start) {
        if (!allowed()) return;
        int end = Math.min(start+5, found.size());
        ArrayList<String> options = new ArrayList<>();
        for (int i=start;i<end;i++) options.add(label(found.get(i)));
        final int count = options.size();
        options.add(msg("previous")); options.add(msg("next")); options.add(msg("search"));
        GameScene.show(new WndOptions(msg(items ? "items" : "mobs"), found.isEmpty() ? msg("no_results") : (start+1)+"–"+end+" / "+found.size(), options.toArray(new String[0])) {
            @Override protected boolean enabled(int i) {
                if (i==count) return start>0;
                if (i==count+1) return start+5<found.size();
                return true;
            }
            @Override protected void onSelect(int i) {
                if (!allowed()) return;
                if (i<count) {
                    if (items) grant(found.get(start+i)); else spawn(found.get(start+i));
                } else if (i==count) page(items,found,Math.max(0,start-5));
                else if (i==count+1) page(items,found,start+5);
                else search(items);
            }
        });
    }
    private static void grant(final Class<?> type) {
        GameScene.show(new WndTextInput(label(type), msg("amount_hint"), "1,0", 8, false, msg("give"), msg("cancel")) {
            @Override public void onSelect(boolean positive, String text) {
                if (!positive || !allowed()) return;
                try {
                    String[] parts = text.trim().split(",");
                    if (parts.length!=2) throw new IllegalArgumentException();
                    int count=Integer.parseInt(parts[0].trim()), level=Integer.parseInt(parts[1].trim());
                    Item probe=(Item)type.getConstructor().newInstance();
                    if (count<1 || count>99 || level<0 || level>(probe instanceof Artifact ? 10:20)
                            || (level>0 && !probe.isUpgradable() && !(probe instanceof Artifact))) throw new IllegalArgumentException();
                    for (int i=0;i<count;i++) {
                        Item item=(Item)type.getConstructor().newInstance();
                        if (item instanceof Artifact) ((Artifact)item).transferUpgrade(level);
                        else item.upgrade(level);
                        give(item);
                    }
                    GLog.p(msg("given"));
                } catch (ReflectiveOperationException | IllegalArgumentException e) { GLog.w(msg("invalid")); }
            }
        });
    }
    private static void give(Item item) {
        item.identify();
        if (item instanceof Gold) { Dungeon.gold += item.quantity(); return; }
        if (item instanceof EnergyCrystal) { Dungeon.energy += item.quantity(); return; }
        if (!item.collect()) Dungeon.level.drop(item, Dungeon.hero.pos).sprite.drop();
    }
    private static void spawn(final Class<?> type) {
        GameScene.selectCell(new CellSelector.Listener() {
            @Override public String prompt() { return msg("spawn_prompt"); }
            @Override public void onSelect(Integer cell) {
                if (cell==null || !allowed()) return;
                if (cell<0 || cell>=Dungeon.level.length() || !Dungeon.level.heroFOV[cell]
                        || !Dungeon.level.passable[cell] || Dungeon.level.pit[cell] || Actor.findChar(cell)!=null) {
                    GLog.w(msg("bad_cell")); return;
                }
                try {
                    Mob mob=(Mob)type.getConstructor().newInstance();
                    if (Char.hasProp(mob, Char.Property.LARGE) && !Dungeon.level.openSpace[cell]) {
                        GLog.w(msg("bad_cell")); return;
                    }
                    mob.pos=cell; mob.state=mob.WANDERING; mob.alignment=Char.Alignment.ENEMY;
                    GameScene.add(mob); Dungeon.level.occupyCell(mob);
                    GLog.p(msg("spawned"));
                } catch (ReflectiveOperationException e) { GLog.w(msg("invalid")); }
            }
        });
    }
}
