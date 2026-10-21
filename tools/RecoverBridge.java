import java.lang.instrument.*;
import java.nio.file.*;
import java.util.jar.*;

/** Update only this plugin's class method bodies, never game classes or save state. */
public final class RecoverBridge {
    private static JarFile retained;
    public static void agentmain(String jar, Instrumentation inst) throws Exception {
        retained=new JarFile(jar);
        inst.appendToSystemClassLoaderSearch(retained);
        try { Class.forName("ml.bridge.View"); }
        catch (ClassNotFoundException e) {
            java.lang.invoke.MethodHandles.privateLookupIn(Class.forName("ml.bridge.Bridge"),
                java.lang.invoke.MethodHandles.lookup()).defineClass(
                    retained.getInputStream(retained.getJarEntry("ml/bridge/View.class")).readAllBytes());
        }
        java.util.List<ClassDefinition> definitions=new java.util.ArrayList<>();
        for (String name:new String[]{"Collector","View","Bridge"}) {
            byte[] bytes=retained.getInputStream(retained.getJarEntry("ml/bridge/"+name+".class")).readAllBytes();
            definitions.add(new ClassDefinition(Class.forName("ml.bridge."+name),bytes));
        }
        inst.redefineClasses(definitions.toArray(new ClassDefinition[0]));
        System.out.println("[Game Text Bridge] Updated the external collector in memory.");
    }
}
