import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndUseItem;

public final class AttachSmokeTarget {
    public static void main(String[] args) throws Exception {
        System.out.println("ATTACH_READY " + ProcessHandle.current().pid());
        System.out.flush();
        new WndUseItem(new Window(), new Item());
        Thread.sleep(4000);
        System.out.println("SECOND_CONSTRUCTION");
        new WndUseItem(new Window(), new Item());
        Thread.sleep(1000);
    }
}
