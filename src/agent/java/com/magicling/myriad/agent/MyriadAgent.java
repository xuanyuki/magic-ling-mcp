package com.magicling.myriad.agent;

import net.bytebuddy.agent.builder.AgentBuilder;
import net.bytebuddy.asm.Advice;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.utility.JavaModule;

import java.io.File;
import java.lang.instrument.Instrumentation;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.jar.JarFile;

import static net.bytebuddy.matcher.ElementMatchers.isConstructor;
import static net.bytebuddy.matcher.ElementMatchers.named;
import static net.bytebuddy.matcher.ElementMatchers.takesArgument;
import static net.bytebuddy.matcher.ElementMatchers.takesArguments;

/** External desktop entry point. The original game archive is only read. */
public final class MyriadAgent {
    private static final String USE_ITEM =
            "com.shatteredpixel.shatteredpixeldungeon.windows.WndUseItem";
    private static final String WINDOW =
            "com.shatteredpixel.shatteredpixeldungeon.ui.Window";
    private static final String ITEM =
            "com.shatteredpixel.shatteredpixeldungeon.items.Item";
    private static final AtomicBoolean INSTALLED = new AtomicBoolean();
    private static JarFile agentJarHandle;

    private MyriadAgent() {}

    public static void premain(String arguments, Instrumentation instrumentation) {
        if (install(instrumentation)) InjectionHooks.agentInstalled();
    }

    /** Entry point for the external Attach API helper. */
    public static void agentmain(String arguments, Instrumentation instrumentation) {
        if (install(instrumentation)) InjectionHooks.agentInstalled();
    }

    private static boolean install(Instrumentation instrumentation) {
        if (!INSTALLED.compareAndSet(false, true)) return true;
        try {
            File agentJar = new File(MyriadAgent.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            agentJarHandle = new JarFile(agentJar);
            instrumentation.appendToSystemClassLoaderSearch(agentJarHandle);
        } catch (Exception e) {
            INSTALLED.set(false);
            System.err.println("[Myriad Codex] Could not expose the external agent to the game: " + e);
            return false;
        }

        try {
            new AgentBuilder.Default()
                .with(new AgentBuilder.Listener.Adapter() {
                    @Override
                    public void onTransformation(
                            TypeDescription type,
                            ClassLoader classLoader,
                            JavaModule module,
                            boolean loaded,
                            DynamicType dynamicType) {
                        if (USE_ITEM.equals(type.getName())) {
                            System.out.println("[Myriad Codex] Hooked " + type.getName());
                        }
                    }

                    @Override
                    public void onError(
                            String typeName,
                            ClassLoader classLoader,
                            JavaModule module,
                            boolean loaded,
                            Throwable throwable) {
                        if (USE_ITEM.equals(typeName)) {
                            System.err.println("[Myriad Codex] Could not hook " + typeName);
                            throwable.printStackTrace(System.err);
                        }
                    }
                })
                .disableClassFormatChanges()
                .with(AgentBuilder.RedefinitionStrategy.RETRANSFORMATION)
                .type(named(USE_ITEM))
                .transform((builder, type, classLoader, module, protectionDomain) -> builder
                        .visit(Advice.to(UseItemAdvice.class).on(
                                isConstructor()
                                        .and(takesArguments(2))
                                        .and(takesArgument(0, named(WINDOW)))
                                        .and(takesArgument(1, named(ITEM))))))
                    .installOn(instrumentation);
        } catch (Throwable e) {
            INSTALLED.set(false);
            System.err.println("[Myriad Codex] Could not install the runtime hook: " + e);
            return false;
        }

        System.out.println("[Myriad Codex] Desktop hook registered for " + USE_ITEM);
        return true;
    }

    public static final class UseItemAdvice {
        private UseItemAdvice() {}

        @Advice.OnMethodExit(suppress = Throwable.class)
        public static void afterDetailsCreated(
                @Advice.This Object detailsWindow,
                @Advice.Argument(0) Object owner,
                @Advice.Argument(1) Object item) {
            InjectionHooks.addCodexButton(detailsWindow, owner, item);
            if (Boolean.getBoolean("myriad.agent.debug")) {
                System.out.println("[Myriad Codex] Intercepted WndUseItem construction.");
            }
        }
    }
}
