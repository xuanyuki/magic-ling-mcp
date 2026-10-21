package ml.bridge;

import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import java.util.*;

/** Memory belongs only to the currently displayed run and floor. */
final class KnownMap {
    private static Level floor;
    private static Hero owner;
    private static int depth;
    private static int[] terrain;
    static void clear() { floor=null; owner=null; terrain=null; }

    static Map<String,Object> collect(Level level,int position,int radius,boolean displayed) {
        if (floor!=level || owner!=Dungeon.hero || depth!=Dungeon.depth || terrain.length!=level.length()) {
            floor=level; owner=Dungeon.hero; depth=Dungeon.depth;
            terrain=new int[level.length()]; Arrays.fill(terrain,-1);
        }
        List<String> rows=new ArrayList<>(), visibility=new ArrayList<>();
        List<Object> cells=new ArrayList<>();
        for (int y=0;y<level.height();y++) {
            StringBuilder row=new StringBuilder(), seen=new StringBuilder();
            for (int x=0;x<level.width();x++) {
                int cell=x+y*level.width();
                boolean visible=displayed && Policy.visible(level.heroFOV,cell);
                boolean known=visible || Policy.visible(level.visited,cell) || Policy.visible(level.mapped,cell);
                // Seed already explored cells on attach; subsequently only refresh in FOV
                // or when the game explicitly reveals a previously unknown cell.
                if (visible || (known && terrain[cell]<0)) terrain[cell]=Policy.publicTerrain(level.map[cell]);
                boolean remembered=terrain[cell]>=0;
                seen.append(visible ? 'v' : remembered ? 'r' : '?');
                row.append(!remembered ? "?" : cell==position ? "@" : Policy.symbol(terrain[cell]));
                if (remembered) cells.add(Json.obj("x",x,"y",y,"terrain",terrain[cell],"name",Policy.terrainName(terrain[cell]),
                    "visibility",visible ? "visible" : "remembered"));
            }
            rows.add(row.toString()); visibility.add(seen.toString());
        }
        Map<String,Object> result=Json.obj("origin",Arrays.asList(0,0),"width",level.width(),
            "height",level.height(),"scope","current_floor_known","rows",rows,
            "visibility",visibility,"cells",cells,"legend",Json.obj("v","当前可见","r","历史已知","?","未知"));
        if (displayed) {
            Map<String,Object> local=Collector.map(level,position,radius);
            for (String key:Arrays.asList("screenProjection","viewport"))
                if (local.containsKey(key)) result.put(key,local.get(key));
        }
        return result;
    }
}
