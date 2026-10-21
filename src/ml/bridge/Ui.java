package ml.bridge;

import com.badlogic.gdx.Gdx;
import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Talent;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.ui.*;
import com.watabou.noosa.*;
import com.watabou.noosa.ui.Component;
import com.watabou.utils.Point;
import java.lang.reflect.*;
import java.util.*;

/** Walks already rendered widgets. Never invokes hover handlers or UI actions. */
final class Ui {
    private static final Field MEMBERS;
    private static final Set<String> SCENES=new HashSet<>(Arrays.asList("GameScene","AlchemyScene",
        "TitleScene","StartScene","HeroSelectScene","RankingsScene","SurfaceScene","AmuletScene","JAmuletScene","JournalScene"));
    private static final Map<String,String> ROLES=new HashMap<>();
    static {
        try { MEMBERS=Group.class.getDeclaredField("members"); MEMBERS.setAccessible(true); }
        catch (Exception e) { throw new ExceptionInInitializerError(e); }
        ROLES.put("Toolbar.btnWait","等待"); ROLES.put("Toolbar.btnSearch","搜索"); ROLES.put("Toolbar.btnInventory","背包");
        ROLES.put("AlchemyScene.cancel","返回"); ROLES.put("AlchemyScene.repeat","重复配方");
        ROLES.put("AlchemyScene.energyAdd","补充炼金能量");
        ROLES.put("InputButton.slot","炼金原料"); ROLES.put("OutputSlot.slot","炼金结果");
        ROLES.put("CombineButton.button","合成");
        ROLES.put("HeroSelectScene.infoButton","职业详情"); ROLES.put("HeroSelectScene.challengeButton","挑战");
        ROLES.put("HeroSelectScene.btnExit","返回");
        ROLES.put("HeroSelectScene.startBtn","选择职业");
    }
    private final List<Object> texts=new ArrayList<>(),blocks=new ArrayList<>(),buttons=new ArrayList<>(),unresolved=new ArrayList<>();
    private final List<String> logs=new ArrayList<>(),windows=new ArrayList<>();
    private Window top;
    private final IdentityHashMap<Gizmo,String> roles=new IdentityHashMap<>();

    static void collect(Map<String,Object> state) throws Exception {
        Ui ui=new Ui();
        Gizmo scene=Game.scene();
        if (scene!=null) {
            ui.discover(scene,0);
            Rect screen=new Rect(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());
            if (ui.top!=null) ui.walk(ui.top,screen,true,true,false,0);
            else ui.walk(scene,screen,true,false,false,0);
        }
        boolean blocked=ui.top!=null || !"GameScene".equals(state.get("scene"))
            || Camera.main!=null && (!Camera.main.visible || !Camera.main.exists);
        for (String key:Arrays.asList("map","knownMap")) if (state.get(key) instanceof Map)
            ((Map<String,Object>)state.get(key)).put("interactionBlocked",blocked);
        state.put("ui",Json.obj("text",ui.texts,"textBlocks",ui.blocks,"buttons",ui.buttons,
            "windows",ui.windows,"modal",ui.top!=null,"captureScope",ui.top==null ? "scene" : "top_window"));
        state.put("screenLog",ui.logs);
        if (ui.windows.size()>1) ui.unresolved.add(Json.obj("kind","occluded_windows","reason","只采集最上层窗口；下层窗口需关闭遮挡后读取"));
        if (!SCENES.contains(state.get("scene"))) ui.unresolved.add(Json.obj("kind","scene","name",state.get("scene"),
            "reason","未专项适配的场景；仅提取已渲染控件，完整含义需截图"));
        ui.unresolved.add(Json.obj("kind","visuals","reason","美术、动画、自定义图块和未绑定文字的特效无法无损文字化；按需截图"));
        if (state.get("knownMap") instanceof Map) {
            Object cells=((Map<?,?>)state.get("knownMap")).get("cells");
            for (Object entry:(List<?>)cells) if (String.valueOf(((Map<?,?>)entry).get("name")).contains("需正常检视")) {
                ui.unresolved.add(Json.obj("kind","terrain","reason","已知地图包含特殊地形；基础字符不表示其完整含义，需正常检视或截图")); break;
            }
        }
        state.put("unresolved",ui.unresolved);
        state.put("coverage",Json.obj("profile","pc-0.9.6.5-2026100600","details","audited_allowlist_or_open_page",
            "ui","rendered_visible_widgets","graphics","screenshot_required","checklist","docs/COVERAGE.md"));
    }
    @SuppressWarnings("unchecked")
    static List<Gizmo> children(Group group) throws IllegalAccessException {
        synchronized (group) { return new ArrayList<>((List<Gizmo>)MEMBERS.get(group)); }
    }
    private void discover(Gizmo node,int depth) throws Exception {
        if (node==null || !node.visible || !node.exists || depth>40) return;
        if (!cameraVisible(node)) return;
        if (node instanceof Window) { top=(Window)node; windows.add(typeName(node)); }
        Class<?> cls=node.getClass();
        for (Class<?> c=cls;c!=null && Gizmo.class.isAssignableFrom(c);c=c.getSuperclass()) {
            for (Field field:c.getDeclaredFields()) {
                String role=ROLES.get(c.getSimpleName()+"."+field.getName());
                if (Arrays.asList("InputButton","OutputSlot","CombineButton").contains(c.getSimpleName()) &&
                    !c.getName().startsWith("com.shatteredpixel.shatteredpixeldungeon.scenes.AlchemyScene$")) role=null;
                if (role!=null && !Modifier.isStatic(field.getModifiers())) {
                    field.setAccessible(true); Object value=field.get(node);
                    if (value instanceof Gizmo) roles.put((Gizmo)value,role);
                }
            }
        }
        if (node instanceof Group) for (Gizmo child:children((Group)node)) discover(child,depth+1);
    }
    static String typeName(Object object) {
        Class<?> cls=object.getClass();
        while (cls.getSimpleName().isEmpty() && cls.getSuperclass()!=null) cls=cls.getSuperclass();
        return cls.getSimpleName();
    }
    static Object field(Object object,String name) {
        for (Class<?> cls=object.getClass();cls!=null;cls=cls.getSuperclass()) try {
            Field field=cls.getDeclaredField(name); field.setAccessible(true); return field.get(object);
        } catch (NoSuchFieldException ignored) {} catch (IllegalAccessException e) { return null; }
        return null;
    }
    private void walk(Gizmo node,Rect clip,boolean enabled,boolean window,boolean log,int depth) throws Exception {
        if (node==null || !node.visible || !node.exists || depth>40) return;
        if (!cameraVisible(node)) return;
        enabled &= node.active;
        log |= node instanceof GameLog;
        Rect camera=camera(node.camera());
        if (camera!=null) clip=clip.intersect(camera);
        if (clip.empty()) return;
        if (node instanceof ScrollPane) clip=clip.intersect(rect(node));
        if (node instanceof RenderedTextBlock) {
            RenderedTextBlock block=(RenderedTextBlock)node;
            Rect bounds=rect(block);
            if (bounds!=null && !bounds.intersect(clip).empty()) {
                if (clip.contains(bounds)) text(Policy.clean(block.text()),bounds,false,log);
                else {
                    // Read only fully visible rendered tokens, never the offscreen backing text.
                    List<String> visible=new ArrayList<>();
                    for (Gizmo word:children(block)) if (word instanceof RenderedText && word.visible && word.exists) {
                        Rect r=rect(word);
                        if (r!=null && clip.contains(r)) visible.add(((RenderedText)word).text());
                    }
                    String s=Policy.clean(String.join("",visible));
                    text(s,bounds.intersect(clip),true,log);
                    unresolved.add(Json.obj("kind","clipped_text","reason","只返回完全可见的文字片段；边缘文字需截图或正常滚动"));
                }
            }
            return;
        }
        if (node instanceof BitmapText || node instanceof RenderedText) {
            Rect bounds=rect(node);
            if (bounds!=null && clip.contains(bounds)) text(Policy.clean(node instanceof BitmapText
                ? ((BitmapText)node).text() : ((RenderedText)node).text()),bounds,false,log);
        }
        if (node instanceof Button) {
            Rect bounds=rect(node), visible=bounds==null ? null : bounds.intersect(clip);
            if (visible!=null && !visible.empty()) {
                List<String> labels=new ArrayList<>();
                visibleLabels((Group)node,clip,labels,0);
                String role=roles.get(node);
                Object item=node instanceof ItemSlot ? field(node,"item") : null;
                Object talent=node instanceof TalentButton ? field(node,"talent") : null;
                Object buff=node.getClass().getName().endsWith("BuffIndicator$BuffButton") ? field(node,"buff") : null;
                if (item instanceof Item) labels.add(0,Policy.clean(item.toString()));
                if (talent instanceof Talent) labels.add(0,Policy.clean(((Talent)talent).title()));
                if (buff instanceof Buff) labels.add(0,Policy.clean(((Buff)buff).name()));
                if (node instanceof QuickSlotButton) labels.add(0,"快捷栏"+field(node,"slotNum"));
                if (role!=null) labels.add(0,role);
                labels.removeIf(String::isBlank);
                String label=labels.isEmpty() ? "无文字图标" : String.join("/",new LinkedHashSet<>(labels));
                Map<String,Object> result=Json.obj("label",label,"screen",visible.center(),"bounds",visible.list(),
                    "enabled",enabled,"inWindow",window,"clipped",!clip.contains(bounds));
                if (node instanceof CheckBox) result.put("checked",((CheckBox)node).checked);
                if (isTab(node))
                    result.put("selected",field(node,"selected"));
                if ("选择职业".equals(role)) result.put("selected",true);
                if (talent instanceof Talent) result.put("points",field(node,"pointsInTalent"));
                if (item instanceof Item) result.put("item",Collector.item((Item)item));
                if (role!=null) result.put("role",role);
                if (labels.isEmpty()) unresolved.add(Json.obj("kind","icon","screen",visible.center(),
                    "reason","未确认图标含义；需截图"));
                buttons.add(result);
            }
        }
        if (node instanceof Group) for (Gizmo child:children((Group)node)) walk(child,clip,enabled,window,log,depth+1);
    }
    private static boolean isTab(Gizmo node) {
        for (Class<?> cls=node.getClass();cls!=null;cls=cls.getSuperclass())
            if (cls.getName().equals("com.shatteredpixel.shatteredpixeldungeon.windows.WndTabbed$Tab")) return true;
        return false;
    }
    private void text(String value,Rect bounds,boolean clipped,boolean log) {
        if (value.isEmpty()) return;
        if (log) logs.add(value);
        else { texts.add(value); blocks.add(Json.obj("text",value,"bounds",bounds.list(),"clipped",clipped)); }
    }
    private static void visibleLabels(Group group,Rect clip,List<String> result,int depth) throws Exception {
        if (depth>15) return;
        for (Gizmo child:children(group)) {
            if (child==null || !child.exists || !child.visible || !cameraVisible(child)) continue;
            Rect bounds=rect(child);
            if (child instanceof RenderedTextBlock) {
                if (bounds!=null && clip.contains(bounds)) result.add(Policy.clean(((RenderedTextBlock)child).text()));
            } else if (child instanceof BitmapText) {
                if (bounds!=null && clip.contains(bounds)) result.add(Policy.clean(((BitmapText)child).text()));
            } else if (child instanceof Group) visibleLabels((Group)child,clip,result,depth+1);
        }
    }
    private static boolean cameraVisible(Gizmo node) {
        Camera camera=node.camera();
        return camera==null || camera.visible && camera.exists;
    }
    private static Rect camera(Camera c) { return c==null ? null : new Rect(c.x,c.y,c.x+c.screenWidth(),c.y+c.screenHeight()); }
    static Rect rect(Gizmo node) {
        Camera camera=node.camera(); if (camera==null) return null;
        float left,top,right,bottom;
        if (node instanceof Component) { Component c=(Component)node; left=c.left(); top=c.top(); right=c.right(); bottom=c.bottom(); }
        else if (node instanceof Visual) { Visual v=(Visual)node; left=v.x; top=v.y; right=v.x+v.width(); bottom=v.y+v.height(); }
        else return null;
        Point a=camera.cameraToScreen(left,top),b=camera.cameraToScreen(right,bottom);
        return new Rect(a.x,a.y,b.x,b.y);
    }
    static final class Rect {
        final float left,top,right,bottom;
        Rect(float a,float b,float c,float d) { left=a;top=b;right=c;bottom=d; }
        Rect intersect(Rect other) { return other==null ? this : new Rect(Math.max(left,other.left),Math.max(top,other.top),Math.min(right,other.right),Math.min(bottom,other.bottom)); }
        boolean empty() { return right<=left || bottom<=top; }
        boolean contains(Rect r) { return r!=null && !r.empty() && r.left>=left && r.top>=top && r.right<=right && r.bottom<=bottom; }
        List<Integer> center() { return Arrays.asList((int)((left+right)/2),(int)((top+bottom)/2)); }
        List<Integer> list() { return Arrays.asList((int)left,(int)top,(int)right,(int)bottom); }
    }
}
