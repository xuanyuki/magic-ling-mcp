package ml.bridge;

import com.shatteredpixel.shatteredpixeldungeon.levels.Terrain;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;

/** Pure visibility rules, shared by live collection and boundary tests. */
final class Policy {
    static boolean visible(boolean[] fov, int pos) {
        return fov != null && pos >= 0 && pos < fov.length && fov[pos];
    }
    static int publicTerrain(int tile) {
        if (tile == Terrain.SECRET_DOOR) return Terrain.WALL;
        if (tile == Terrain.SECRET_TRAP) return Terrain.EMPTY;
        return tile;
    }
    static String symbol(int raw) {
        int t = publicTerrain(raw);
        if (t == Terrain.WALL || t == Terrain.WALL_DECO) return "#";
        if (t == Terrain.CHASM) return "O";
        if (t == Terrain.WATER || t == Terrain.SALT_WATER) return "~";
        if (t == Terrain.DOOR) return "+";
        if (t == Terrain.OPEN_DOOR) return "/";
        if (t == Terrain.LOCKED_DOOR || t == Terrain.CRYSTAL_DOOR
                || t == Terrain.GOLDEN_DOOR) return "L";
        if (t == Terrain.ENTRANCE || t == Terrain.ENTRANCE_SP) return "<";
        if (t == Terrain.EXIT || t == Terrain.UNLOCKED_EXIT) return ">";
        if (t == Terrain.LOCKED_EXIT) return "X";
        if (t == Terrain.HIGH_GRASS || t == Terrain.FURROWED_GRASS) return "\"";
        if (t == Terrain.GRASS) return ",";
        if (t == Terrain.TRAP) return "^";
        if (t == Terrain.INACTIVE_TRAP) return "_";
        if (t == Terrain.BOOKSHELF || t == Terrain.BARRICADE) return "B";
        if (t == Terrain.WELL || t == Terrain.ALTWELL) return "W";
        if (t == Terrain.ALCHEMY) return "A";
        if (t == Terrain.SIGN || t == Terrain.SIGN_SP) return "S";
        if (t == Terrain.LAVA) return "!";
        if (t == Terrain.STATUE || t == Terrain.STATUE_SP) return "s";
        if ((Terrain.flags[t] & Terrain.SOLID) != 0) return "#";
        return ".";
    }
    static String terrainName(int raw) {
        int tile=publicTerrain(raw);
        String key;
        switch (tile) {
            case Terrain.CHASM: key="chasm_name"; break;
            case Terrain.EMPTY: case Terrain.EMPTY_SP: case Terrain.EMPTY_DECO: key="floor_name"; break;
            case Terrain.EMBERS: key="embers_name"; break;
            case Terrain.PEDESTAL: key="pedestal_name"; break;
            case Terrain.GRASS: key="grass_name"; break;
            case Terrain.WATER: key="water_name"; break;
            case Terrain.SALT_WATER: key="salt_water_name"; break;
            case Terrain.WALL: case Terrain.WALL_DECO: key="wall_name"; break;
            case Terrain.DOOR: key="closed_door_name"; break;
            case Terrain.OPEN_DOOR: key="open_door_name"; break;
            case Terrain.ENTRANCE: case Terrain.ENTRANCE_SP: key="entrace_name"; break;
            case Terrain.EXIT: key="exit_name"; break;
            case Terrain.LOCKED_DOOR: key="locked_door_name"; break;
            case Terrain.CRYSTAL_DOOR: key="crystal_door_name"; break;
            case Terrain.GOLDEN_DOOR: key="golden_door_name"; break;
            case Terrain.BARRICADE: key="barricade_name"; break;
            case Terrain.HIGH_GRASS: key="high_grass_name"; break;
            case Terrain.FURROWED_GRASS: key="furrowed_grass_name"; break;
            case Terrain.LOCKED_EXIT: key="locked_exit_name"; break;
            case Terrain.UNLOCKED_EXIT: key="unlocked_exit_name"; break;
            case Terrain.WELL: case Terrain.ALTWELL: key="well_name"; break;
            case Terrain.EMPTY_WELL: key="empty_well_name"; break;
            case Terrain.STATUE: case Terrain.STATUE_SP: key="statue_name"; break;
            case Terrain.INACTIVE_TRAP: key="inactive_trap_name"; break;
            case Terrain.BOOKSHELF: key="bookshelf_name"; break;
            case Terrain.ALCHEMY: key="alchemy_name"; break;
            case Terrain.TRAP: return "已显露陷阱";
            case Terrain.SIGN: case Terrain.SIGN_SP: return "告示牌";
            case Terrain.LAVA: return "岩浆";
            default: return "特殊地形（需正常检视或截图）";
        }
        // Use audited localization keys; never dispatch Level subclass tileName/Desc overrides.
        return clean(Messages.get(Level.class,key));
    }
    static String clean(String text) {
        if (text == null) return "";
        // Only strip the game's leading GLog color marker; retain original wording.
        return text.replaceFirst("^(?:@@|\\+\\+|\\*\\*|!!|--|>>|##)\\s?", "")
            .replace('\r', ' ').replace('\n', ' ').trim();
    }
}
