package ml.bridge;

import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.AccessDeniedException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.io.*;

public final class Bridge {
    public static volatile boolean installed;
    private static final String SESSION = UUID.randomUUID().toString();
    private static Path output;
    private static int radius = 7, port = 17321;
    private static long interval = 500, lastAttempt;
    private static volatile long observedAt, lastWrittenAt, sequence;
    private static volatile boolean closed;
    private static volatile String lastError = "", snapshot = "{}", compact = "尚未采集\n";
    private static final AtomicLong dropped = new AtomicLong();
    private static final ArrayDeque<String> LOGS = new ArrayDeque<>();
    private static final ArrayDeque<Map<String,Object>> CHANGES = new ArrayDeque<>();
    private static final BlockingQueue<Candidate> QUEUE = new ArrayBlockingQueue<>(16);
    private static final BlockingQueue<CompletableFuture<Map<String,Object>>> CAPTURES = new ArrayBlockingQueue<>(4);
    private static Map<String,Object> previous = new LinkedHashMap<>();
    private static HttpServer server;
    private static Thread writer;
    private static ExecutorService http;
    private Bridge() {}

    static void start(String args) throws Exception {
        output = Path.of(args == null || args.isBlank() ? "game-text-output" : args).toAbsolutePath();
        Files.createDirectories(output);
        Properties config = new Properties();
        Path configPath = output.resolve("bridge.properties");
        if (Files.isRegularFile(configPath)) try (Reader r = Files.newBufferedReader(configPath)) { config.load(r); }
        port = Integer.parseInt(config.getProperty("port","17321"));
        radius = Math.max(3, Math.min(16,Integer.parseInt(config.getProperty("radius","7"))));
        interval = Math.max(250, Long.parseLong(config.getProperty("intervalMs","500")));
        server = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"),port),0);
        port = server.getAddress().getPort();
        http = Executors.newFixedThreadPool(6, r -> {
            Thread t = new Thread(r,"game-text-http"); t.setDaemon(true); return t;
        });
        server.setExecutor(http);
        server.createContext("/", Bridge::serve);
        server.start();
        writer = new Thread(Bridge::writeLoop, "game-text-writer");
        writer.setDaemon(true); writer.start();
        Files.writeString(output.resolve("connection.json"), Json.encode(Json.obj(
            "url","http://127.0.0.1:"+port, "session", SESSION, "pid",ProcessHandle.current().pid())));
        System.err.println("[Game Text Bridge] Read-only observer: " + output + " port " + port);
    }
    public static void log(String text) {
        String line = Policy.clean(text);
        if (line.isEmpty()) return;
        synchronized (LOGS) {
            if (LOGS.size() == 256) { LOGS.removeFirst(); dropped.incrementAndGet(); }
            LOGS.addLast(line);
        }
    }
    public static void frame() {
        if (closed || !Compatibility.supported()) return;
        long now = System.currentTimeMillis();
        if ((now-lastAttempt < interval && CAPTURES.isEmpty()) || QUEUE.remainingCapacity() == 0) return;
        lastAttempt = now;
        try {
            if (Collector.busy()) return;
            Map<String,Object> state = Collector.collect(radius);
            if (Collector.busy()) return; // Do not publish a snapshot during an Actor step.
            List<String> logs;
            synchronized (LOGS) { logs = new ArrayList<>(LOGS); LOGS.clear(); }
            CompletableFuture<Map<String,Object>> capture=CAPTURES.poll();
            Map<String,Object> image=null;
            if (capture!=null && capture.isDone()) capture=null;
            if (capture!=null) {
                try { image=captureImage(); }
                catch (Throwable e) { capture.completeExceptionally(e); capture=null; }
            }
            QUEUE.add(new Candidate(state,logs,now,capture,image));
        } catch (Throwable e) { error("collect",e); }
    }
    private static void writeLoop() {
        Candidate pending=null;
        while (!closed) {
            try {
                if (Files.exists(output.resolve("stop"))) { close(); return; }
                if (pending==null) pending=QUEUE.poll(1,TimeUnit.SECONDS);
                if (pending != null) { publish(pending); pending=null; }
                long now = System.currentTimeMillis();
                if (now-lastWrittenAt >= 5000) {
                    atomic(output.resolve("health.json"),Json.encode(health()));
                    lastWrittenAt = now;
                }
            } catch (InterruptedException e) { Thread.currentThread().interrupt(); return; }
              catch (Throwable e) {
                  error("writer",e);
                  try { Thread.sleep(250); }
                  catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); return; }
              }
        }
    }
    private static void publish(Candidate c) throws Exception {
        Map<String,Object> patch = View.diff(previous,c.state);
        if (patch.isEmpty() && c.logs.isEmpty() && c.capture==null) {
            observedAt=c.time;
            lastError="";
            return;
        }
        long base = sequence, next = base+1;
        Map<String,Object> event = Json.obj("session",SESSION,"base",base,"seq",next,
            "observedAt",Instant.ofEpochMilli(c.time).toString(),"set",patch,"newLog",c.logs);
        Map<String,Object> full = Json.obj("schema",1,"session",SESSION,"seq",next,
            "observedAt",Instant.ofEpochMilli(c.time).toString(),"state",c.state);
        String json = Json.encode(full);
        String text = "seq="+next+" session="+SESSION+"\n"+View.text(c.state);
        atomic(output.resolve("snapshot.json"),json);
        atomic(output.resolve("compact.txt"),text);
        atomic(output.resolve("latest-change.json"),Json.encode(event));
        // Events are a durable journal; the HTTP replay ring holds the last 256 revisions.
        Files.writeString(output.resolve("changes.ndjson"),Json.encode(event)+"\n",
            StandardCharsets.UTF_8,StandardOpenOption.CREATE,StandardOpenOption.APPEND);
        synchronized (CHANGES) {
            CHANGES.addLast(event);
            if (CHANGES.size()>256) CHANGES.removeFirst();
            snapshot=json; compact=text; sequence=next;
        }
        previous=c.state;
        observedAt=c.time;
        lastError="";
        if (c.capture!=null) {
            c.image.put("session",SESSION); c.image.put("seq",next);
            c.image.put("observedAt",full.get("observedAt"));
            c.capture.complete(Json.obj("snapshot",full,"screenshot",c.image));
        }
    }
    private static Map<String,Object> captureImage() throws IOException {
        // Called only from Game.render exit: no GL work runs on HTTP/writer threads.
        Path directory=output.resolve("screenshots"); Files.createDirectories(directory);
        Path path=directory.resolve(UUID.randomUUID()+".png");
        com.badlogic.gdx.graphics.Pixmap pixels=com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(
            0,0,com.badlogic.gdx.Gdx.graphics.getBackBufferWidth(),com.badlogic.gdx.Gdx.graphics.getBackBufferHeight());
        try {
            com.badlogic.gdx.graphics.PixmapIO.writePNG(new com.badlogic.gdx.files.FileHandle(path.toFile()),pixels,-1,true);
            return Json.obj("path",path.toAbsolutePath().toString(),"width",pixels.getWidth(),
                "height",pixels.getHeight(),"coordinateSpace","logical_screen",
                "screenWidth",com.badlogic.gdx.Gdx.graphics.getWidth(),"screenHeight",com.badlogic.gdx.Gdx.graphics.getHeight());
        } finally { pixels.dispose(); }
    }
    private static void atomic(Path path, String value) throws IOException {
        Path temp=path.resolveSibling(path.getFileName()+".tmp");
        Files.writeString(temp,value,StandardCharsets.UTF_8);
        try { Files.move(temp,path,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
        catch (AccessDeniedException e) { Files.move(temp,path,StandardCopyOption.REPLACE_EXISTING); }
    }
    private static Map<String,Object> health() {
        long age = observedAt==0 ? -1 : System.currentTimeMillis()-observedAt;
        return Json.obj("installed",installed,"session",SESSION,"seq",sequence,
            "pid",ProcessHandle.current().pid(),"observationAgeMs",age,
            "stale",age<0 || age>10000,"droppedLog",dropped.get(),"lastError",lastError,
            "readOnly",true,"connected",!closed,"compatibility",Compatibility.status());
    }
    public static void error(String where, Throwable error) {
        String message=where+": "+error;
        if (!message.equals(lastError)) System.err.println("[Game Text Bridge] "+message);
        lastError=message;
    }
    public static void close() {
        closed=true;
        for (CompletableFuture<Map<String,Object>> future:CAPTURES)
            future.completeExceptionally(new IOException("Observer closed"));
        CAPTURES.clear();
        if (server!=null) server.stop(0);
        if (http!=null) http.shutdownNow();
        if (writer!=null) writer.interrupt();
    }
    private static void serve(HttpExchange exchange) throws IOException {
        try {
            // A local AI client needs no browser cross-origin access.
            if (exchange.getRequestHeaders().containsKey("Origin")) { reply(exchange,403,"Origin not allowed","text/plain"); return; }
            String path=exchange.getRequestURI().getPath();
            String method=exchange.getRequestMethod();
            if (!(method.equals("GET") && !path.equals("/capture")) && !(method.equals("POST") && path.equals("/capture"))) {
                reply(exchange,405,"GET observations; POST /capture only","text/plain"); return;
            }
            if (path.equals("/capture")) {
                Map<String,String> query=query(exchange);
                if (!SESSION.equals(query.get("session"))) { failure(exchange,409,"session_mismatch","游戏会话已改变"); return; }
                if (!Compatibility.supported() || !installed || observedAt==0 || System.currentTimeMillis()-observedAt>10000) {
                    failure(exchange,503,"observation_unavailable","采集未就绪、版本不兼容或数据陈旧"); return;
                }
                CompletableFuture<Map<String,Object>> future=new CompletableFuture<>();
                if (!CAPTURES.offer(future)) { failure(exchange,429,"capture_busy","截图队列已满"); return; }
                try { reply(exchange,200,Json.encode(future.get(5,TimeUnit.SECONDS)),"application/json"); }
                catch (TimeoutException e) { future.cancel(false); CAPTURES.remove(future); failure(exchange,504,"capture_timeout","游戏未在稳定渲染点完成截图"); }
                catch (Exception e) { future.cancel(false); CAPTURES.remove(future); failure(exchange,503,"capture_failed","截图失败："+e); }
                return;
            }
            if (path.equals("/health")) reply(exchange,200,Json.encode(health()),"application/json");
            else if (path.equals("/snapshot")) reply(exchange,200,snapshot,"application/json");
            else if (path.equals("/compact")) reply(exchange,200,compact,"text/plain");
            else if (path.equals("/changes")) {
                Map<String,String> query = query(exchange);
                long since=Long.parseLong(query.getOrDefault("since","0"));
                synchronized (CHANGES) {
                    boolean gap = since<0 || since>sequence
                        || (!CHANGES.isEmpty() && since<(Long)CHANGES.getFirst().get("base"))
                        || !SESSION.equals(query.getOrDefault("session",SESSION));
                    List<Object> events=new ArrayList<>();
                    if (!gap) for (Map<String,Object> e:CHANGES) if ((Long)e.get("seq")>since) events.add(e);
                    reply(exchange,200,Json.encode(Json.obj("session",SESSION,"seq",sequence,
                        "resync",gap,"events",events)), "application/json");
                }
            } else reply(exchange,404,"/health /snapshot /compact /changes?since=0","text/plain");
        } catch (IllegalArgumentException e) { reply(exchange,400,"Invalid query","text/plain"); }
          catch (Throwable e) { error("http",e); reply(exchange,500,"Observer error","text/plain"); }
        finally { exchange.close(); }
    }
    private static Map<String,String> query(HttpExchange exchange) {
        Map<String,String> query=new HashMap<>();
        String raw=exchange.getRequestURI().getRawQuery();
        if (raw!=null) for (String part:raw.split("&")) {
            String[] kv=part.split("=",2);
            if (kv.length==2) query.put(kv[0],URLDecoder.decode(kv[1],StandardCharsets.UTF_8));
        }
        return query;
    }
    private static void failure(HttpExchange exchange,int status,String code,String message) throws IOException {
        reply(exchange,status,Json.encode(Json.obj("error",code,"message",message)),"application/json");
    }
    private static void reply(HttpExchange x,int code,String body,String type) throws IOException {
        byte[] bytes=body.getBytes(StandardCharsets.UTF_8);
        x.getResponseHeaders().set("Content-Type",type+"; charset=utf-8");
        x.getResponseHeaders().set("Cache-Control","no-store");
        x.getResponseHeaders().set("X-Content-Type-Options","nosniff");
        x.sendResponseHeaders(code,bytes.length);
        x.getResponseBody().write(bytes);
    }
    private static final class Candidate {
        final Map<String,Object> state,image; final List<String> logs; final long time;
        final CompletableFuture<Map<String,Object>> capture;
        Candidate(Map<String,Object> s,List<String> l,long t,CompletableFuture<Map<String,Object>> c,Map<String,Object> i) {
            state=s; logs=l; time=t; capture=c; image=i;
        }
    }
}
