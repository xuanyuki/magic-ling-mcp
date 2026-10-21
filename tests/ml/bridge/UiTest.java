package ml.bridge;

import com.shatteredpixel.shatteredpixeldungeon.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.npcs.Ghost;
import com.shatteredpixel.shatteredpixeldungeon.items.*;
import com.shatteredpixel.shatteredpixeldungeon.items.food.Food;
import com.shatteredpixel.shatteredpixeldungeon.scenes.*;
import com.shatteredpixel.shatteredpixeldungeon.ui.*;
import com.shatteredpixel.shatteredpixeldungeon.windows.*;
import com.watabou.noosa.*;
import com.watabou.noosa.ui.Component;
import java.lang.reflect.Field;
import java.util.*;

/** Uses actual game windows with disposable data, without any generated input. */
final class UiTest {
    static void run(Game game,Field sceneField) throws Exception {
        Object saved=Game.scene(); Camera savedCamera=Camera.main;
        Game.width=Game.dispWidth=320; Game.height=Game.dispHeight=240; Game.density=1;
        game.create();
        PixelScene setup=new PixelScene(); setup.create();
        GameScene scene=new GameScene(); sceneField.set(game,scene);
        com.shatteredpixel.shatteredpixeldungeon.journal.Notes.reset();
        Statistics.reset();
        Talent.initClassTalents(Dungeon.hero);
        try {
            window(scene,new WndBag(Dungeon.hero.belongings.backpack),"backpack");
            window(scene,new WndInfoItem(new Food()),"item inspection");
            window(scene,new WndHero(),"hero panel and tab selection");
            window(scene,new WndTradeItem(new Food(),null),"shop");
            window(scene,new WndQuest(new Ghost(),"隔离测试对话"),"NPC dialogue");
            window(scene,new WndJournal(),"journal");
            window(scene,new WndResurrect(new Ankh()),"death/resurrection");
            window(scene,new WndScoreBreakdown(),"score results");

            Window lower=new WndOptions("下层窗口","不应返回此文字","离开"); scene.add(lower);
            Window upper=new WndOptions("上层窗口","确认选项","允许","禁用") {
                @Override protected boolean enabled(int index) { return index==0; }
            }; scene.add(upper);
            String stacked=Json.encode(Collector.collect(3).get("ui"));
            BoundaryTest.check(stacked.contains("上层窗口") && !stacked.contains("不应返回此文字"),"lower modal text excluded");
            BoundaryTest.check(stacked.contains("\"enabled\":false"),"disabled option exposed");
            upper.visible=false; lower.visible=false;

            Window clipped=new Window(140,90); scene.add(clipped);
            Component content=new Component();
            RenderedTextBlock visible=PixelScene.renderTextBlock("可见段落",6); visible.setPos(0,0); content.add(visible);
            RenderedTextBlock hidden=PixelScene.renderTextBlock("不可见滚动内容",6); hidden.setPos(0,180); content.add(hidden);
            content.setSize(120,210);
            ScrollPane pane=new ScrollPane(content); clipped.add(pane); pane.setRect(0,0,120,60);
            IconButton icon=new IconButton(); icon.setRect(0,65,16,16); clipped.add(icon);
            CheckBox check=new CheckBox("测试开关"); check.setRect(20,65,100,16); check.checked=true; clipped.add(check);
            Map<String,Object> clipState=Collector.collect(3); String clipJson=Json.encode(clipState);
            BoundaryTest.check(clipJson.contains("可见段落") && !clipJson.contains("不可见滚动内容"),"scroll clipping respects actual camera");
            BoundaryTest.check(clipJson.contains("\"checked\":true"),"checkbox selection captured");
            BoundaryTest.check(clipJson.contains("未确认图标含义"),"unknown icons unresolved");
            BoundaryTest.check(clipJson.contains("\"interactionBlocked\":true"),"modal blocks map projection");
            Camera hiddenCamera=new Camera(0,0,320,240,1); hiddenCamera.visible=false;
            RenderedTextBlock hiddenCameraText=PixelScene.renderTextBlock("隐藏相机文字",6);
            hiddenCameraText.camera=hiddenCamera; hiddenCameraText.setPos(0,0); clipped.add(hiddenCameraText);
            BoundaryTest.check(!Json.encode(Collector.collect(3)).contains("隐藏相机文字"),"hidden camera text excluded");
            clipped.visible=false;

            Hero runHero=Dungeon.hero;
            for (PixelScene page:Arrays.asList(new TitleScene(),new HeroSelectScene(),new AlchemyScene(),new SurfaceScene())) {
                if (page instanceof AlchemyScene || page instanceof SurfaceScene) Dungeon.hero=runHero;
                sceneField.set(game,page);
                // Actual scene creation is isolated; the observer only reads its rendered result.
                int hp=runHero.HP;
                if (page instanceof AlchemyScene) {
                    // Skip scene-entry autosave for this deliberately incomplete test run.
                    runHero.HP=0; WndResurrect.instance=null;
                }
                try { page.create(); } finally { runHero.HP=hp; }
                Map<String,Object> state=Collector.collect(3);
                String name=page.getClass().getSimpleName();
                BoundaryTest.check(((Map<?,?>)state.get("ui")).containsKey("buttons"),"scene widgets: "+name);
                if (page instanceof AlchemyScene) {
                    BoundaryTest.check(state.containsKey("hero") && state.containsKey("inventory"),"alchemy retains run observation");
                    BoundaryTest.check(((List<?>)state.get("visibleActors")).isEmpty(),"alchemy does not publish stale map actors");
                    BoundaryTest.check(Json.encode(state).contains("炼金原料") && Json.encode(state).contains("炼金结果"),"alchemy slots have audited roles");
                    Object input=java.lang.reflect.Array.get(Ui.field(page,"inputs"),0);
                    java.lang.reflect.Method bind=input.getClass().getDeclaredMethod("item",Item.class); bind.setAccessible(true);
                    bind.invoke(input,new Food());
                    BoundaryTest.check(Json.encode(Collector.collect(3).get("ui")).contains(new Food().name()),"alchemy reads actual bound ingredient");
                } else BoundaryTest.check(!state.containsKey("hero"),"menu does not leak retained run: "+name);
                if (page instanceof HeroSelectScene) BoundaryTest.check(Json.encode(state.get("ui")).contains("\"selected\":true"),"selected hero class exposed");
                stable(name);
                // Do not call gameplay destroy hooks (AlchemyScene.destroy can return ingredients).
                System.out.println("PASS actual scene widgets: "+name);
            }
        } finally {
            sceneField.set(game,saved);
            if (savedCamera!=null) Camera.main=savedCamera;
            KnownMap.clear();
        }
    }
    private static void window(GameScene scene,Window window,String name) throws Exception {
        scene.add(window);
        Map<String,Object> state=Collector.collect(3);
        Map<?,?> ui=(Map<?,?>)state.get("ui");
        BoundaryTest.check(Boolean.TRUE.equals(ui.get("modal")),"window recognized: "+name);
        BoundaryTest.check(!((List<?>)ui.get("text")).isEmpty() || !((List<?>)ui.get("buttons")).isEmpty(),"window contents: "+name);
        if (window instanceof WndHero) BoundaryTest.check(Json.encode(ui).contains("\"selected\":true"),"selected tab exposed");
        stable(name);
        // hide() may run gameplay callbacks (shop reopening, resurrection, etc.).
        window.visible=false;
        System.out.println("PASS actual window widgets: "+name);
    }
    private static void stable(String name) throws Exception {
        Hero hero=Dungeon.hero;
        int hp=hero==null ? 0 : hero.HP,pos=hero==null ? 0 : hero.pos;
        String rng=BoundaryTest.randomState(), schedule=BoundaryTest.schedulerState(), before=Json.encode(Collector.collect(3));
        for (int i=0;i<5;i++) BoundaryTest.check(before.equals(Json.encode(Collector.collect(3))),"stable UI read: "+name);
        BoundaryTest.check((hero==null || hp==hero.HP && pos==hero.pos) && Dungeon.hero==hero
            && rng.equals(BoundaryTest.randomState()) && schedule.equals(BoundaryTest.schedulerState()),"pure UI read: "+name);
    }
}
