import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndUseItem;

/** Exercises the exact constructor hook against a tiny non-game fixture. */
public final class MyriadAgentLoadSmoke {
    public static void main(String[] args) {
        new WndUseItem(new Window(), new Item());
        System.out.println("Instrumented constructor executed.");
    }
}
