package ml.bridge;

import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;
import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import static net.bytebuddy.matcher.ElementMatchers.*;

/** Independent, read-only hooks; no input, return values or game fields are changed. */
public final class Agent {
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    public static void premain(String args, Instrumentation inst) { install(args, inst); }
    public static void agentmain(String args, Instrumentation inst) { install(args, inst); }

    private static void install(String args, Instrumentation inst) {
        if (!INSTALLED.compareAndSet(false, true)) return;
        try {
            Compatibility.check();
            Bridge.start(args);
            if (!Compatibility.supported()) return;
            new AgentBuilder.Default().disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override public void onError(String name, ClassLoader loader,
                        net.bytebuddy.utility.JavaModule module, boolean loaded, Throwable error) {
                        Bridge.error("hook " + name, error);
                    }
                })
                .type(named("com.watabou.noosa.Game"))
                .transform((b,t,c,m,p) -> b.visit(Advice.to(Frame.class)
                    .on(named("render").and(takesArguments(0))))
                    .visit(Advice.to(Dispose.class).on(named("dispose").and(takesArguments(0)))))
                .type(named("com.shatteredpixel.shatteredpixeldungeon.ui.GameLog"))
                .transform((b,t,c,m,p) -> b.visit(Advice.to(Log.class)
                    .on(named("onSignal").and(takesArguments(String.class)))))
                .installOn(inst);
            Bridge.installed = true;
        } catch (Throwable e) {
            Bridge.error("install", e);
            throw new IllegalStateException("Text bridge installation failed", e);
        }
    }
    public static class Frame {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void exit() { Bridge.frame(); }
    }
    public static class Log {
        @Advice.OnMethodEnter(suppress = Throwable.class)
        public static void enter(@Advice.Argument(0) String text) { Bridge.log(text); }
    }
    public static class Dispose {
        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void exit() { Bridge.close(); }
    }
}
