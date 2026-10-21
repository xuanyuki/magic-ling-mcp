package ml.bridge;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.shatteredpixel.shatteredpixeldungeon.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.Char;
import com.shatteredpixel.shatteredpixeldungeon.items.*;
import com.shatteredpixel.shatteredpixeldungeon.levels.*;
import com.shatteredpixel.shatteredpixeldungeon.messages.*;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.sprites.CharSprite;
import com.shatteredpixel.shatteredpixeldungeon.levels.traps.Trap;
import com.watabou.noosa.*;
import java.lang.reflect.*;
import java.util.*;
import java.nio.file.*;

/** Entirely isolated fixture. Never loads a player save or operates the desktop. */
public final class BoundaryTest {
    public static class TestLevel extends Level {
        protected boolean build() { return true; }
        protected void createMobs() {}
        protected void createItems() {}
        @Override public String waterTex() { return "environment/water0.png"; }
    }
    public static class UnknownItem extends Item {
        @Override public String toString() { return "靛紫药剂"; }
        @Override public String name() { return "靛紫药剂"; }
        @Override public boolean isIdentified() { return false; }
        @Override public String desc() { throw new AssertionError("Unreviewed description invoked"); }
        @Override public String info() { throw new AssertionError("Unreviewed info invoked"); }
    }
    public static class SecretLoot extends Item {
        @Override public String toString() { throw new AssertionError("Chest loot leaked"); }
    }
    public static class HiddenTrap extends Trap {
        @Override public void activate() { throw new AssertionError("Trap activated"); }
        @Override public String name() { throw new AssertionError("Hidden trap inspected"); }
    }
    static Throwable failure;
    public static void main(String[] args) throws Exception {
        if (args.length>2 && args[2].equals("wait-attach")) {
            // Preload hook targets to exercise real retransformation, not just future class loading.
            Class.forName("com.watabou.noosa.Game");
            Class.forName("com.shatteredpixel.shatteredpixeldungeon.ui.GameLog");
            Path root=Path.of(args[0]); Files.createDirectories(root);
            Files.writeString(root.resolve("ready"),Long.toString(ProcessHandle.current().pid()));
            awaitLong(() -> Files.exists(root.resolve("proceed")));
        }
        Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();
        cfg.setTitle("Text bridge isolated boundary test");
        cfg.setInitialVisible(false); cfg.disableAudio(true); cfg.setWindowedMode(320,240);
        new Lwjgl3Application(new com.badlogic.gdx.ApplicationAdapter() {
            @Override public void create() {
                try { run(args); } catch(Throwable e) { failure=e; }
                finally { if (args.length>1) Bridge.close(); }
                Gdx.app.exit();
            }
        },cfg);
        if (failure!=null) { failure.printStackTrace(); System.exit(1); }
    }
    static void run(String[] args) throws Exception {
        Path root=Path.of(args[0]); Files.createDirectories(root);
        Gdx.files=new Lwjgl3Files();
        com.watabou.utils.FileUtils.setDefaultFileProperties(com.badlogic.gdx.Files.FileType.Absolute,
            root.toAbsolutePath()+"/");
        SPDSettings.set(new Lwjgl3Preferences(new FileHandle(root.resolve("settings.xml").toFile())));
        Badges.loadGlobal();
        PaswordBadges.loadGlobal();
        Messages.setup(Languages.CHINESE);
        Game.version="0.9.6.5";
        Game.platform=new com.shatteredpixel.shatteredpixeldungeon.desktop.DesktopPlatformSupport();
        Game game=new Game(GameScene.class,Game.platform) {
            @Override protected void step() {}
            @Override protected void draw() {}
        };
        Field scene=Game.class.getDeclaredField("scene"); scene.setAccessible(true);
        scene.set(game,new GameScene()); Game.instance=game;
        Hero hero=new Hero(); hero.heroClass=HeroClass.WARRIOR;
        hero.pos=12; hero.ready=true; Dungeon.hero=hero; Dungeon.depth=4;
        TestLevel level=new TestLevel(); level.setSize(5,5); Dungeon.level=level;
        level.heaps=new com.watabou.utils.SparseArray<>();
        level.traps=new com.watabou.utils.SparseArray<>();
        level.plants=new com.watabou.utils.SparseArray<>();
        level.mobs=new HashSet<>(); level.blobs=new HashMap<>();
        Arrays.fill(level.map,Terrain.EMPTY);
        level.heroFOV[12]=true; level.heroFOV[13]=true; level.heroFOV[11]=true;
        level.map[11]=Terrain.SECRET_DOOR; level.map[13]=Terrain.SECRET_TRAP;
        level.map[0]=Terrain.EXIT;
        UnknownItem unknown=new UnknownItem(); hero.belongings.backpack.items.add(unknown);
        unknown.cursed=true; unknown.cursedKnown=false; unknown.levelKnown=false;
        Heap chest=new Heap(); chest.pos=13; chest.type=Heap.Type.CHEST;
        chest.items.add(new SecretLoot()); level.heaps.put(13,chest);
        HiddenTrap trap=new HiddenTrap(); trap.pos=13; trap.visible=false; level.traps.put(13,trap);
        Rat hidden=new Rat(); hidden.pos=0; hidden.sprite=new CharSprite(); hidden.sprite.visible=true;
        level.mobs.add(hidden);
        Rat seen=new Rat(); seen.pos=11; seen.sprite=new CharSprite(); seen.sprite.visible=true;
        level.mobs.add(seen);
        Mimic mimic=new Mimic(); mimic.pos=13; mimic.sprite=new CharSprite();
        mimic.sprite.visible=true; mimic.alignment=Char.Alignment.NEUTRAL; level.mobs.add(mimic);
        Field current=com.shatteredpixel.shatteredpixeldungeon.actors.Actor.class.getDeclaredField("current");
        current.setAccessible(true); current.set(null,hero);
        check(!Collector.busy(),"waiting Hero actor is a stable observation point");
        hero.ready=false; check(Collector.busy(),"running Hero actor must be skipped"); hero.ready=true;
        current.set(null,seen); check(Collector.busy(),"running enemy actor must be skipped");
        current.set(null,hero);
        String before=Json.encode(Collector.collect(3));
        check(!before.contains("SecretLoot") && !before.contains("UnknownItem"),"no true item type names");
        check(!before.contains("毒镖") && !before.contains("PoisonTrap"),"hidden trap filtered");
        Map<String,Object> snapshot=Collector.collect(3);
        check(((List<?>)snapshot.get("visibleActors")).size()==1,"hidden mob and disguised mimic filtered");
        String map=Json.encode(snapshot.get("map"));
        check(!map.contains(">") && !map.contains("^") && map.contains("#@."),"hidden exit/door/trap masked");
        check(before.contains("靛紫药剂") && before.contains("\"identified\":false"),"unknown appearance retained");
        check(!before.contains("\"visibleCurse\"") && !before.contains("\"visibleUpgrade\""),"hidden curse and upgrade omitted");
        check(Policy.terrainName(Terrain.SECRET_DOOR).equals(Policy.terrainName(Terrain.WALL))
            && Policy.terrainName(Terrain.SECRET_TRAP).equals(Policy.terrainName(Terrain.EMPTY)),"terrain names preserve discovery boundaries");
        int hp=hero.HP, pos=hero.pos, qty=unknown.quantity();
        String rng=randomState(), schedule=schedulerState();
        for (int i=0;i<100;i++) check(before.equals(Json.encode(Collector.collect(3))),"stable repeated collection");
        check(hp==hero.HP && pos==hero.pos && qty==unknown.quantity() && rng.equals(randomState())
            && schedule.equals(schedulerState()),"100 observations preserve HP, position, quantity, scheduling and RNG");
        List<com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff> approved=Arrays.asList(
            new com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Bless(),
            new com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Haste(),
            new com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Hunger());
        for (com.shatteredpixel.shatteredpixeldungeon.actors.buffs.Buff buff:approved) {
            buff.target=hero;
            String detail=Json.encode(Details.buff(buff)); float cooldown=buff.cooldown();
            for (int i=0;i<100;i++) check(detail.equals(Json.encode(Details.buff(buff))),"approved buff details stable");
            check(buff.cooldown()==cooldown,"approved details preserve buff schedule");
        }
        check(schedule.equals(schedulerState()) && rng.equals(randomState()),"approved details preserve scheduler and RNG");
        Map<String,Object> known=(Map<String,Object>)snapshot.get("knownMap");
        check(((List<?>)known.get("rows")).size()==5,"known map covers whole floor");
        level.visited[11]=true; level.heroFOV[11]=false; level.map[11]=Terrain.EXIT;
        String remembered=Json.encode(Collector.collect(3).get("knownMap"));
        check(remembered.contains("?r") && !remembered.contains(">"),"unseen terrain retains last observation");
        level.mapped[0]=true;
        check(Json.encode(Collector.collect(3).get("knownMap")).contains(">"),"explicit map reveal is known");
        level.heroFOV[11]=true; level.map[11]=Terrain.SECRET_DOOR; level.mapped[0]=false;
        KnownMap.clear();
        level.heroFOV[0]=false;
        TestLevel other=new TestLevel(); other.setSize(5,5);
        check(((List<?>)KnownMap.collect(other,12,3,true).get("cells")).isEmpty(),"new floor resets map memory");
        KnownMap.clear();
        check(Compatibility.accepts("0.9.6.5","2026100600") && !Compatibility.accepts("0.9.6.5","2026100601")
            && !Compatibility.accepts("0.9.6.6","2026100600"),"version and build both must match");
        check(View.diff(Json.obj("hero",1,"ui",2),Json.obj("ui",3)).equals(Json.obj("hero",null,"ui",3)),
            "delta removes stale sections on scene change");
        check(Json.encode(Json.obj("text","换行\n引号\"\\😀")).contains("\\n"),"JSON control escaping");
        check(Policy.clean("** 你着火了！").equals("你着火了！"),"only color marker removed");
        Camera savedCamera=Camera.main;
        Camera.main=new Camera(0,0,320,240,2);
        Camera.main.scroll.x=13.125f; Camera.main.scroll.y=17.25f;
        Map<String,Object> located=Collector.location(12,5,12);
        com.watabou.utils.Point expected=Camera.main.cameraToScreen(40,40);
        check(located.get("screen").equals(Arrays.asList(expected.x,expected.y)),
            "text actor coordinate agrees with game camera");
        Map<String,Object> projected=Collector.map(level,12,3);
        Map<?,?> projection=(Map<?,?>)projected.get("screenProjection");
        List<?> zero=(List<?>)projection.get("worldZero");
        double step=((Number)projection.get("cellPixels")).doubleValue();
        check((int)(((Number)zero.get(0)).doubleValue()+2.5*step)==expected.x
            && (int)(((Number)zero.get(1)).doubleValue()+2.5*step)==expected.y,
            "fractional map projection agrees with original camera");
        Camera.main=savedCamera;
        if (args.length>1) {
            Path observer=Path.of(args[1]);
            check(Bridge.installed,"agent installed");
            game.render(); // The original Game.render method is instrumented, not called via Bridge.frame.
            await(() -> Files.exists(observer.resolve("snapshot.json")));
            String first=Files.readString(observer.resolve("snapshot.json"));
            check(first.contains("\"seq\":1"),"real render hook publishes first snapshot");
            Thread.sleep(300);
            game.render(); Thread.sleep(100);
            check(first.equals(Files.readString(observer.resolve("snapshot.json"))),
                "idle frame does not rewrite snapshot");
            com.shatteredpixel.shatteredpixeldungeon.ui.GameLog sink=
                new com.shatteredpixel.shatteredpixeldungeon.ui.GameLog();
            sink.onSignal("++ 重复消息"); sink.onSignal("++ 重复消息");
            Thread.sleep(300); game.render();
            await(() -> Files.readString(observer.resolve("snapshot.json")).contains("\"seq\":2"));
            String change=Files.readString(observer.resolve("latest-change.json"));
            check(change.contains("\"newLog\":[\"重复消息\",\"重复消息\"]"),
                "final UI log sink captured once per real event, including legitimate repeats");
            check(change.contains("\"set\":{}"),"log-only event omits unchanged state");
            String connection=Files.readString(observer.resolve("connection.json"));
            String url=connection.split("\"url\":\"")[1].split("\"")[0];
            java.net.http.HttpClient client=java.net.http.HttpClient.newHttpClient();
            String empty=get(client,url+"/changes?since=2");
            check(empty.contains("\"events\":[]") && empty.contains("\"resync\":false"),"cursor skips consumed events");
            check(get(client,url+"/changes?since=99").contains("\"resync\":true"),"invalid cursor requires resync");
            check(get(client,url+"/changes?since=1&session=old").contains("\"resync\":true"),"old session requires resync");
            java.net.http.HttpRequest post=java.net.http.HttpRequest.newBuilder(java.net.URI.create(url+"/snapshot"))
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
            check(client.send(post,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()==405,
                "HTTP never accepts game actions");
            check(get(client,url+"/health").contains("\"compatible\":true"),"health checks exact game manifest");
            java.net.http.HttpRequest origin=java.net.http.HttpRequest.newBuilder(java.net.URI.create(url+"/snapshot"))
                .header("Origin","https://example.invalid").GET().build();
            check(client.send(origin,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()==403,"cross-origin reads rejected");
            java.net.http.HttpRequest wrongSession=java.net.http.HttpRequest.newBuilder(java.net.URI.create(url+"/capture?session=old"))
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
            check(client.send(wrongSession,java.net.http.HttpResponse.BodyHandlers.ofString()).statusCode()==409,"old screenshot session rejected");
            java.net.http.HttpRequest capture=java.net.http.HttpRequest.newBuilder(java.net.URI.create(url+"/capture?session="+
                connection.split("\"session\":\"")[1].split("\"")[0]))
                .POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build();
            java.util.concurrent.CompletableFuture<java.net.http.HttpResponse<String>> image=client.sendAsync(capture,
                java.net.http.HttpResponse.BodyHandlers.ofString());
            Gdx.gl.glClearColor(0.8f,0.2f,0.1f,1); Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
            for (int i=0;i<100 && !image.isDone();i++) { game.render(); Thread.sleep(10); }
            java.net.http.HttpResponse<String> captured=image.get(2,java.util.concurrent.TimeUnit.SECONDS);
            check(captured.statusCode()==200,"render thread screenshot succeeds");
            check(captured.body().contains("\"seq\":3") && captured.body().contains("\"snapshot\""),"screenshot revision bound to snapshot");
            Path png;
            try (java.util.stream.Stream<Path> images=Files.list(observer.resolve("screenshots"))) { png=images.filter(p -> p.toString().endsWith(".png")).findFirst().orElseThrow(); }
            java.awt.image.BufferedImage pixels=javax.imageio.ImageIO.read(png.toFile());
            check(pixels.getWidth()==320 && pixels.getHeight()==240 && ((pixels.getRGB(10,10)>>16)&255)>150,
                "PNG contains actual hidden framebuffer pixels");
            check(captured.body().contains(png.getFileName().toString()),"capture response names existing PNG");
            java.net.http.HttpResponse<String> timeout=client.send(capture,java.net.http.HttpResponse.BodyHandlers.ofString());
            check(timeout.statusCode()==504 && timeout.body().contains("capture_timeout"),"no render point produces explicit capture timeout");
            game.render(); Thread.sleep(100);
            check(get(client,url+"/health").contains("\"lastError\":\"\""),"timed-out request does not poison writer");
            check(hp==hero.HP && pos==hero.pos && qty==unknown.quantity() && rng.equals(randomState())
                && schedule.equals(schedulerState()),"screenshots and publication preserve game and scheduler state");
            if (args.length>2 && args[2].equals("wait-attach")) {
                Camera.main=new Camera(0,0,320,240,1);
                Thread.sleep(300); game.render();
                await(() -> Files.readString(observer.resolve("snapshot.json")).contains("screenProjection"));
                Files.writeString(root.resolve("mcp-ready"),observer.toAbsolutePath().toString());
                long deadline=System.currentTimeMillis()+30000;
                while (!Files.exists(root.resolve("mcp-done"))) {
                    check(System.currentTimeMillis()<deadline,"real MCP client completed before timeout");
                    game.render(); Thread.sleep(20);
                }
                Camera.main=savedCamera;
                check(hp==hero.HP && pos==hero.pos && qty==unknown.quantity() && rng.equals(randomState())
                    && schedule.equals(schedulerState()),"real MCP tools preserve game and scheduler state");
            }
            System.out.println("PASS real render/log hooks, stable files, repeated logs, cursors, HTTP read-only methods");
        }
        System.out.println("PASS visibility, chest boundary, unknown items, delta deletion, RNG and repeated reads");
        UiTest.run(game,scene);
    }
    interface Condition { boolean ready() throws Exception; }
    static void await(Condition condition) throws Exception {
        long end=System.currentTimeMillis()+3000;
        while(!condition.ready()) {
            if (System.currentTimeMillis()>end) throw new AssertionError("Observer timeout");
            Thread.sleep(10);
        }
    }
    static void awaitLong(Condition condition) throws Exception {
        long end=System.currentTimeMillis()+20000;
        while(!condition.ready()) {
            if (System.currentTimeMillis()>end) throw new AssertionError("Attach timeout");
            Thread.sleep(20);
        }
    }
    static String get(java.net.http.HttpClient c,String url) throws Exception {
        return c.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).GET().build(),
            java.net.http.HttpResponse.BodyHandlers.ofString()).body();
    }
    static String randomState() throws Exception {
        Field f=com.watabou.utils.Random.class.getDeclaredField("generators"); f.setAccessible(true);
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();
        try(java.io.ObjectOutputStream out=new java.io.ObjectOutputStream(bytes)){out.writeObject(f.get(null));}
        return Base64.getEncoder().encodeToString(bytes.toByteArray());
    }
    static String schedulerState() throws Exception {
        Class<?> actor=com.shatteredpixel.shatteredpixeldungeon.actors.Actor.class;
        List<String> result=new ArrayList<>();
        for (String name:Arrays.asList("current","nextID","now","all","chars","ids")) {
            Field f=actor.getDeclaredField(name); f.setAccessible(true);
            Object value=f.get(null);
            if (value instanceof Collection) {
                List<String> members=new ArrayList<>();
                for (Object entry:(Collection<?>)value) members.add(Integer.toString(System.identityHashCode(entry)));
                Collections.sort(members); result.add(name+members);
            } else result.add(name+String.valueOf(value));
        }
        Set<com.shatteredpixel.shatteredpixeldungeon.actors.Actor> actors=
            new HashSet<>(com.shatteredpixel.shatteredpixeldungeon.actors.Actor.all());
        if (Dungeon.hero!=null) { actors.add(Dungeon.hero); actors.addAll(Dungeon.hero.buffs()); }
        if (Dungeon.level!=null) actors.addAll(Dungeon.level.mobs);
        List<String> times=new ArrayList<>();
        for (Object entry:actors) {
            StringBuilder row=new StringBuilder().append(System.identityHashCode(entry));
            for (String name:Arrays.asList("time","id","actPriority")) {
                Field f=actor.getDeclaredField(name); f.setAccessible(true); row.append(':').append(f.get(entry));
            }
            times.add(row.toString());
        }
        Collections.sort(times); result.addAll(times);
        return result.toString();
    }
    static void check(boolean b,String s) { if(!b) throw new AssertionError(s); }
}
