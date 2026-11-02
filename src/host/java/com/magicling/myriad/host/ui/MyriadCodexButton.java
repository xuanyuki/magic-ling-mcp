package com.magicling.myriad.host.ui;

import com.magicling.myriad.host.MyriadCodexService;
import com.magicling.myriad.host.PluginCompatibilityException;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.RedButton;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndOptions;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndUseItem;

import java.lang.reflect.Field;

/** Button added to the normal item-details window by the external agent. */
public final class MyriadCodexButton extends RedButton {
    private final Item staff;
    private final WndUseItem details;
    private final Window owner;
    private final MyriadCodexService service;

    public MyriadCodexButton(
            Item staff,
            WndUseItem details,
            Window owner,
            MyriadCodexService service) {
        super("击杀图鉴", 8);
        this.staff = staff;
        this.details = details;
        this.owner = owner;
        this.service = service;
        int width = dimension(details, "width");
        int height = dimension(details, "height");
        setSize(reqWidth(), 16);
        setRect(0, height, width, 16);
        details.add(this);
        details.resize(width, height + 18);
    }

    @Override
    protected void onClick() {
        try {
            com.magicling.myriad.api.MyriadCodexModel snapshot = service.snapshot(staff);
            details.hide();
            if (owner != null && owner.parent != null) owner.hide();
            GameScene.show(new WndMyriadCodex(snapshot));
        } catch (PluginCompatibilityException | RuntimeException e) {
            GameScene.show(new WndOptions(
                    "图鉴插件不兼容",
                    "无法读取当前游戏中的万象之杖击杀记录。\n" + e.getMessage(),
                    "关闭") {
                @Override
                protected void onSelect(int index) {}
            });
        }
    }

    private static int dimension(WndUseItem window, String fieldName) {
        try {
            Field field = Window.class.getDeclaredField(fieldName);
            field.setAccessible(true);
            return field.getInt(window);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("读取游戏窗口尺寸失败", e);
        }
    }
}
