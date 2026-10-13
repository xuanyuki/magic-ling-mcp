package com.magicling.myriad.host.ui;

import com.magicling.myriad.api.MonsterRow;
import com.magicling.myriad.api.MyriadCodexModel;
import com.magicling.myriad.api.StaffKillStatus;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.PixelScene;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollingGridPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndJournalItem;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.watabou.noosa.Image;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Optional host-side renderer. It lives outside the dependency-free plugin
 * source set and uses the game's existing grid/detail window components.
 */
public final class WndMyriadCodex extends Window {
    private static final int WIDTH_P = 160;
    private static final int HEIGHT_P = 220;
    private static final int WIDTH_L = 230;
    private static final int HEIGHT_L = 150;

    private final MyriadCodexModel model;
    private final ScrollingGridPane grid;

    public WndMyriadCodex(MyriadCodexModel model) {
        super(PixelScene.landscape() ? WIDTH_L : WIDTH_P,
                PixelScene.landscape() ? HEIGHT_L : HEIGHT_P);
        this.model = model;
        this.grid = new ScrollingGridPane();
        add(grid);
        grid.setRect(0, 0, width, height);
        rebuild();
    }

    private void rebuild() {
        grid.clear();
        int knownTotal = model.killedCount() + model.notKilledCount();
        String suffix = model.unmappedCount() == 0 ? ""
                : "，未映射 " + model.unmappedCount();
        grid.addHeader("万象之杖图鉴 (" + model.killedCount() + "/" + knownTotal + suffix + ")", 9, true);

        Map<String, Boolean> categorySeen = new LinkedHashMap<>();
        for (MonsterRow row : model.rows()) {
            String category = row.definition().category();
            if (!categorySeen.containsKey(category)) {
                categorySeen.put(category, Boolean.TRUE);
                grid.addHeader(category, 7, false);
            }
            addRow(row);
        }
        grid.setRect(0, 0, width, height);
    }

    private void addRow(MonsterRow row) {
        Image icon = makeIcon(row.definition().className());
        boolean visible = row.definition().seenInBestiary()
                || row.killedByStaff()
                || row.status() == StaffKillStatus.UNMAPPED_KILL;
        if (!visible) icon.lightness(0f);
        String title = visible ? row.definition().displayName() : "???";
        String desc = row.status() == StaffKillStatus.UNMAPPED_KILL
                ? "该记录来自法杖存档，但当前游戏百科没有对应条目。"
                : !visible
                ? "图鉴尚未发现；本杖也尚未击杀。"
                : (row.killedByStaff() ? "本杖已击杀" : "本杖尚未击杀");

        final Image detailIcon = icon;
        final String detailTitle = title;
        final String detailDesc = desc;
        ScrollingGridPane.GridItem item = new ScrollingGridPane.GridItem(icon) {
            @Override
            public boolean onClick(float x, float y) {
                if (!inside(x, y)) return false;
                GameScene.show(new WndJournalItem(detailIcon, detailTitle, detailDesc));
                return true;
            }
        };

        switch (row.status()) {
            case KILLED_BY_STAFF:
                item.hardLightBG(0.2f, 1f, 0.2f);
                break;
            case UNMAPPED_KILL:
                item.hardLightBG(1f, 0.8f, 0.1f);
                break;
            case NOT_KILLED_BY_STAFF:
            default:
                item.hardLightBG(0.45f, 0.45f, 0.45f);
                break;
        }
        grid.addItem(item);
    }

    private static Image makeIcon(String className) {
        try {
            Class<?> type = Class.forName(className);
            Constructor<?> ctor = type.getDeclaredConstructor();
            if (!ctor.isAccessible()) ctor.setAccessible(true);
            Object value = ctor.newInstance();
            if (!(value instanceof Mob)) return new Image();
            Mob mob = (Mob) value;
            Method spriteMethod = type.getMethod("sprite");
            Object sprite = spriteMethod.invoke(mob);
            if (sprite == null) return new Image();
            Method idle = sprite.getClass().getMethod("idle");
            idle.invoke(sprite);
            return new Image((com.watabou.noosa.Visual) sprite);
        } catch (ReflectiveOperationException | SecurityException ignored) {
            return new Image();
        }
    }
}

