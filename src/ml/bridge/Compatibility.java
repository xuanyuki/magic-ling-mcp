package ml.bridge;

import java.net.JarURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.jar.Attributes;

final class Compatibility {
    static final String VERSION = "0.9.6.5", BUILD = "2026100600";
    private static Map<String,Object> status = Json.obj("compatible",false,
        "expectedVersion",VERSION,"expectedBuild",BUILD,"reason","not_checked");

    static boolean accepts(String version, String build) {
        return VERSION.equals(version) && BUILD.equals(build);
    }

    static void check() {
        try {
            URL resource = Compatibility.class.getClassLoader().getResource(
                "com/shatteredpixel/shatteredpixeldungeon/Dungeon.class");
            if (resource == null || !(resource.openConnection() instanceof JarURLConnection))
                throw new IllegalStateException("Game classes must come from the supported game JAR");
            JarURLConnection connection = (JarURLConnection)resource.openConnection();
            connection.setUseCaches(false);
            Attributes attributes = connection.getManifest().getMainAttributes();
            String version = attributes.getValue("Specification-Version");
            String build = attributes.getValue("Implementation-Version");
            status = Json.obj("compatible",accepts(version,build),"version",version,"build",build,
                "expectedVersion",VERSION,"expectedBuild",BUILD,
                "reason",accepts(version,build) ? "" : "unsupported_game_version");
        } catch (Exception e) {
            status = Json.obj("compatible",false,"expectedVersion",VERSION,"expectedBuild",BUILD,
                "reason",e.toString());
        }
    }

    static boolean supported() { return Boolean.TRUE.equals(status.get("compatible")); }
    static Map<String,Object> status() { return status; }
}
