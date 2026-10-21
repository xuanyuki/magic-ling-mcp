package ml.bridge;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.items.*;
import com.shatteredpixel.shatteredpixeldungeon.levels.*;
import com.shatteredpixel.shatteredpixeldungeon.ui.*;
import com.watabou.noosa.*;
import com.watabou.utils.Point;
import java.lang.reflect.Field;
import java.util.*;

final class Collector {
    private static final Field MEMBERS;
    static {
        try { MEMBERS = Group.class.getDeclaredField("members"); MEMBERS.setAccessible(true); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    static boolean busy() {
        if (!(Game.scene() instanceof com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene)) return false;
        // This fork retains current=Hero while its actor thread waits for player input.
        return Actor.processing() && !(Dungeon.hero != null && Dungeon.hero.ready
            && Actor.getCurrentActorClass() == Dungeon.hero.getClass());
    }

    static Map<String,Object> collect(int radius) throws Exception {
        Map<String,Object> state = Json.obj("scene", Game.scene() == null
            ? "none" : Game.scene().getClass().getSimpleName());
        // A retained Dungeon.hero in title/menu scenes is not the displayed run.
        boolean displayed=Game.scene() instanceof com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
        boolean runScene=displayed || Game.scene() instanceof com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene;
        if (runScene
                && Dungeon.hero != null && Dungeon.level != null) {
            Hero h = Dungeon.hero;
            Level l = Dungeon.level;
            int width = l.width();
            Map<String,Object> hero = Json.obj("name", h.className(), "depth", Dungeon.depth,
                "hp", h.HP, "maxHp", h.HT, "level", h.lvl, "xp", h.exp,
                "nextXp", h.maxExp(), "strength", h.STR(), "gold", Dungeon.gold,
                "ready", h.ready, "x", h.pos % width, "y", h.pos / width);
            if (displayed && Camera.main != null) hero.put("screen",location(h.pos,width,h.pos).get("screen"));
            hero.put("profession",Details.hero(h));
            Hunger hunger = h.buff(Hunger.class);
            if (hunger != null) {
                hero.put("satiety", Math.max(0, (int)Hunger.STARVING - hunger.hunger()));
                hero.put("satietyMax", (int)Hunger.STARVING);
            }
            List<String> buffs = new ArrayList<>();
            List<Object> buffDetails=new ArrayList<>();
            int shield=0;
            for (Buff b : h.buffs()) {
                if (b instanceof ShieldBuff) shield+=((ShieldBuff)b).shielding();
                // Hidden passive counters are not UI information.
                if (b.icon() == BuffIndicator.NONE) continue;
                String s = Policy.clean(b.name());
                String icon = Policy.clean(b.iconTextDisplay());
                buffs.add(s + (icon.isEmpty() ? "" : "(" + icon + ")"));
                buffDetails.add(Details.buff(b));
            }
            Collections.sort(buffs);
            hero.put("shield",shield);
            hero.put("buffs", buffs);
            buffDetails.sort(Comparator.comparing(Json::encode)); hero.put("buffDetails",buffDetails);
            state.put("hero", hero);
            List<Object> inventory = new ArrayList<>();
            for (Item item : h.belongings) if (item != null) {
                Map<String,Object> i = item(item);
                String slot = item == h.belongings.weapon ? "weapon"
                    : item == h.belongings.armor ? "armor"
                    : item == h.belongings.artifact ? "artifact"
                    : item == h.belongings.ring ? "ring"
                    : item == h.belongings.misc ? "misc"
                    : item == h.belongings.secondWep ? "secondWeapon" : "";
                if (!slot.isEmpty()) i.put("equipped", slot);
                inventory.add(i);
            }
            state.put("inventory", inventory);
            state.put("containers",bag(h.belongings.backpack,new IdentityHashMap<>()));
            List<Map<String,Object>> mobs = new ArrayList<>();
            // Iterate a copy only when Actor.processing()==false, on the render thread.
            for (Mob mob : new ArrayList<>(l.mobs)) {
                if (!displayed || !Policy.visible(l.heroFOV, mob.pos) || mob.sprite == null
                        || !mob.sprite.visible || mob.invisible > 0 || mob.HP <= 0) continue;
                // A disguised mimic must never disclose its real species or contents.
                if (mob instanceof Mimic && mob.alignment == Char.Alignment.NEUTRAL) continue;
                Map<String,Object> m = location(mob.pos, width, h.pos);
                m.put("name", Policy.clean(mob.name()));
                m.put("alignment", mob.alignment.toString().toLowerCase(Locale.ROOT));
                // The bar communicates a ratio. Do not expose exact hidden HP/HT.
                m.put("health", mob.HP * 4 > mob.HT * 3 ? "高"
                    : mob.HP * 4 > mob.HT ? "中" : "低");
                mobs.add(m);
            }
            mobs.sort(Comparator.comparingInt(m -> (Integer)m.get("cell")));
            state.put("visibleActors", mobs);
            List<Object> features = new ArrayList<>();
            for (int cell = 0; cell < l.length(); cell++) {
                if (!displayed || !Policy.visible(l.heroFOV, cell)) continue;
                Heap heap = l.heaps.get(cell);
                if (heap != null && !heap.hidden) {
                    Map<String,Object> f = location(cell, width, h.pos);
                    f.put("kind", "heap");
                    // Container label only, never peek at unopened chest loot.
                    f.put("name", Policy.clean(heap.toString()));
                    if ((heap.type == Heap.Type.HEAP || heap.type == Heap.Type.FOR_SALE)
                            && heap.peek() != null) f.put("topItem", item(heap.peek()));
                    features.add(f);
                }
                com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap trap = l.traps.get(cell);
                if (trap != null && trap.visible) {
                    Map<String,Object> f = location(cell, width, h.pos);
                    f.put("kind", "trap"); f.put("name", Policy.clean(trap.name()));
                    f.put("active", trap.active); features.add(f);
                }
                com.shatteredpixel.shatteredpixeldungeon.plants.Plant plant = l.plants.get(cell);
                if (plant != null) {
                    Map<String,Object> f = location(cell, width, h.pos);
                    f.put("kind", "plant"); f.put("name", Policy.clean(plant.name()));
                    features.add(f);
                }
            }
            state.put("features", features);
            List<Object> effects = new ArrayList<>();
            for (com.shatteredpixel.shatteredpixeldungeon.actors.blobs.Blob blob : l.blobs.values()) {
                if (blob.cur == null) continue;
                List<Object> cells = new ArrayList<>();
                for (int cell=0; cell<Math.min(blob.cur.length,l.length()); cell++)
                    if (displayed && blob.cur[cell]>0 && Policy.visible(l.heroFOV,cell))
                        cells.add(Arrays.asList(cell%width-h.pos%width,cell/width-h.pos/width));
                if (!cells.isEmpty()) {
                    String description=Policy.clean(blob.tileDesc());
                    if (!description.isEmpty()) effects.add(Json.obj("description",description,"offsets",cells));
                }
            }
            effects.sort(Comparator.comparing(Json::encode));
            state.put("visibleEffects", effects);
            if (displayed) state.put("map", map(l, h.pos, radius));
            state.put("knownMap",KnownMap.collect(l,h.pos,radius,displayed));
        } else KnownMap.clear();
        Ui.collect(state);
        return state;
    }

    static Map<String,Object> bag(com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag bag,
            IdentityHashMap<Item,Boolean> visited) {
        Map<String,Object> result=item(bag);
        List<Object> contents=new ArrayList<>(); result.put("items",contents);
        if (visited.put(bag,true)!=null) { result.put("truncated",true); return result; }
        for (Item entry:bag.items) contents.add(entry instanceof com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag
            ? bag((com.shatteredpixel.shatteredpixeldungeon.items.bags.Bag)entry,visited) : item(entry));
        return result;
    }

    static Map<String,Object> item(Item i) {
        Map<String,Object> m = Json.obj("name", Policy.clean(i.toString()),
            "quantity", i.quantity(), "identified", i.isIdentified());
        String status = i.status();
        if (status != null && !status.isBlank()) m.put("status", status);
        Details.item(i,m);
        // Never trueName(), getClass(), trueLevel(), cursed or loot generation.
        return m;
    }
    static Map<String,Object> location(int cell, int width, int hero) {
        Map<String,Object> result=Json.obj("cell", cell, "x", cell % width, "y", cell / width,
            "dx", cell % width - hero % width, "dy", cell / width - hero / width);
        if (Camera.main != null) {
            int size=com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap.SIZE;
            Point p=Camera.main.cameraToScreen((cell%width+0.5f)*size,(cell/width+0.5f)*size);
            if (Gdx.graphics != null && (p.x<0 || p.y<0
                    || p.x>=Gdx.graphics.getWidth() || p.y>=Gdx.graphics.getHeight()))
                result.put("offscreen",true);
            else result.put("screen",Arrays.asList(p.x,p.y));
        }
        return result;
    }
    static Map<String,Object> map(Level l, int pos, int radius) {
        int w = l.width(), hx = pos % w, hy = pos / w;
        int x0 = Math.max(0, hx-radius), y0 = Math.max(0, hy-radius);
        int x1 = Math.min(w-1, hx+radius), y1 = Math.min(l.height()-1, hy+radius);
        List<String> rows = new ArrayList<>();
        for (int y = y0; y <= y1; y++) {
            StringBuilder row = new StringBuilder();
            for (int x = x0; x <= x1; x++) {
                int cell = x + y*w;
                row.append(cell == pos ? "@" : !Policy.visible(l.heroFOV,cell) ? "?"
                    : Policy.symbol(l.map[cell]));
            }
            rows.add(row.toString());
        }
        Map<String,Object> result=Json.obj("origin", Arrays.asList(x0,y0), "rows", rows,
            "scope", "current_fov", "radius", radius);
        if (Camera.main != null) {
            int size=com.shatteredpixel.shatteredpixeldungeon.tiles.DungeonTilemap.SIZE;
            // Keep floats until the final cell projection to avoid accumulating pixel rounding.
            result.put("screenProjection",Json.obj("worldZero",Arrays.asList(
                Camera.main.x-Camera.main.scroll.x*Camera.main.zoom,
                Camera.main.y-Camera.main.scroll.y*Camera.main.zoom),
                "cellPixels",size*Camera.main.zoom));
            if (Gdx.graphics != null) result.put("viewport",
                Arrays.asList(Gdx.graphics.getWidth(),Gdx.graphics.getHeight()));
        }
        return result;
    }
    @SuppressWarnings("unchecked")
    static void scan(Gizmo node, List<Object> texts, List<Object> buttons,
            List<String> logs, boolean inWindow, boolean inLog, int depth) throws Exception {
        if (node == null || !node.exists || !node.visible || depth > 32) return;
        inWindow |= node instanceof Window;
        inLog |= node instanceof GameLog;
        if (node instanceof RenderedTextBlock) {
            String s = Policy.clean(((RenderedTextBlock)node).text());
            if (!s.isEmpty()) {
                if (inLog) logs.add(s);
                else if ((inWindow || !(Game.scene() instanceof com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene))
                        && onCamera((RenderedTextBlock)node)) texts.add(s);
            }
        }
        if (inWindow && node instanceof BitmapText) {
            String s=Policy.clean(((BitmapText)node).text());
            if (!s.isEmpty()) texts.add(s);
        }
        if (node instanceof Button) {
            Button b = (Button)node;
            Camera c = b.camera();
            if (c != null && b.width() > 0 && b.height() > 0) {
                Point p = c.cameraToScreen(b.centerX(), b.centerY());
                // Labels are gathered only from rendered descendants.
                List<String> label = new ArrayList<>();
                labels(b, label, 0);
                if (b instanceof ItemSlot) {
                    Field field=ItemSlot.class.getDeclaredField("item");
                    field.setAccessible(true);
                    Item displayed=(Item)field.get(b);
                    // Only the actual item already bound to a visible UI slot.
                    if (displayed != null && !Policy.clean(displayed.toString()).isEmpty())
                        label.add(0,Policy.clean(displayed.toString()));
                }
                if (onCamera(b) && (inWindow || (b instanceof ItemSlot && b.active && !label.isEmpty())))
                    buttons.add(Json.obj(
                    "label",label.isEmpty() ? "无文字图标" : String.join("/",label),
                    "screen",Arrays.asList(p.x,p.y),"enabled",b.active,"inWindow",inWindow));
            }
        }
        if (node instanceof Group) {
            Group group = (Group)node;
            List<Gizmo> children;
            synchronized (group) { children = new ArrayList<>((List<Gizmo>)MEMBERS.get(group)); }
            for (Gizmo child : children) scan(child,texts,buttons,logs,inWindow,inLog,depth+1);
        }
    }
    static boolean onCamera(com.watabou.noosa.ui.Component c) {
        Camera camera=c.camera();
        if (camera==null) return false;
        Point a=camera.cameraToScreen(c.left(),c.top()), b=camera.cameraToScreen(c.right(),c.bottom());
        return b.x>=camera.x && b.y>=camera.y && a.x<camera.x+camera.screenWidth()
            && a.y<camera.y+camera.screenHeight();
    }
    @SuppressWarnings("unchecked")
    static void labels(Group group, List<String> labels, int depth) throws Exception {
        if (depth > 12) return;
        List<Gizmo> children;
        synchronized (group) { children = new ArrayList<>((List<Gizmo>)MEMBERS.get(group)); }
        for (Gizmo g : children) {
            if (g == null || !g.exists || !g.visible) continue;
            if (g instanceof RenderedTextBlock) {
                String s = Policy.clean(((RenderedTextBlock)g).text());
                if (!s.isEmpty()) labels.add(s);
            } else if (g instanceof BitmapText) {
                String s=Policy.clean(((BitmapText)g).text());
                if (!s.isEmpty()) labels.add(s);
            } else if (g instanceof Group) labels((Group)g,labels,depth+1);
        }
    }
}
