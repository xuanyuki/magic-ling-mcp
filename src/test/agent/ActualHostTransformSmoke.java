/** Defines the real 0.9.6.5 details class without initializing game services. */
public final class ActualHostTransformSmoke {
    public static void main(String[] args) throws Exception {
        Class.forName(
                "com.shatteredpixel.shatteredpixeldungeon.windows.WndUseItem",
                false,
                ActualHostTransformSmoke.class.getClassLoader());
        System.out.println("Original game details class transformed and loaded.");
    }
}
