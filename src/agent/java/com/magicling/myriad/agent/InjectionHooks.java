package com.magicling.myriad.agent;

import com.magicling.myriad.host.MyriadCodexService;
import com.magicling.myriad.host.PluginCompatibilityException;
import com.magicling.myriad.host.ReflectiveBestiaryCatalogSource;
import com.magicling.myriad.host.ReflectiveStaffKillSource;
import com.magicling.myriad.host.ui.MyriadCodexButton;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndUseItem;

import java.util.Collections;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/** Small bridge called by the injected constructor advice. */
public final class InjectionHooks {
    private static final String STAFF = ReflectiveStaffKillSource.EXPECTED_CLASS;
    private static final Set<WndUseItem> INSTALLED =
            Collections.newSetFromMap(new WeakHashMap<WndUseItem, Boolean>());
    private static final AtomicBoolean VERSION_WARNING = new AtomicBoolean();

    private InjectionHooks() {}

    public static void agentInstalled() {
        System.out.println("[Myriad Codex] Runtime agent active. Reopen item details to use the hook.");
    }

    public static void addCodexButton(Object detailsWindow, Object owner, Object item) {
        if (!(detailsWindow instanceof WndUseItem)
                || !(owner == null || owner instanceof Window)
                || !(item instanceof Item)
                || !STAFF.equals(item.getClass().getName())) {
            return;
        }

        if (!isSupportedBuild()) return;

        WndUseItem details = (WndUseItem) detailsWindow;
        if (!INSTALLED.add(details)) return;

        details.add(new MyriadCodexButton(
                (Item) item,
                details,
                (Window) owner,
                new MyriadCodexService(
                        new ReflectiveStaffKillSource(),
                        new ReflectiveBestiaryCatalogSource())));
    }

    public static PluginCompatibilityException compatibilityFailure(Throwable cause) {
        return new PluginCompatibilityException("读取万象之杖图鉴失败", cause);
    }

    private static boolean isSupportedBuild() {
        try {
            Class<?> game = Class.forName("com.watabou.noosa.Game");
            int versionCode = game.getField("versionCode").getInt(null);
            if (versionCode == 2026100600) return true;
            if (VERSION_WARNING.compareAndSet(false, true)) {
                System.err.println("[Myriad Codex] Unsupported game build " + versionCode
                        + "; expected 2026100600. No UI hook was added.");
            }
            return false;
        } catch (ReflectiveOperationException | LinkageError e) {
            if (VERSION_WARNING.compareAndSet(false, true)) {
                System.err.println("[Myriad Codex] Could not verify the game build; no UI hook was added.");
            }
            return false;
        }
    }
}
