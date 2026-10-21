package ml.bridge;

import com.shatteredpixel.shatteredpixeldungeon.actors.buffs.*;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.*;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import java.util.*;

/** Deliberately narrow allowlist; see docs/READ-AUDIT.md for bytecode review. */
final class Details {
    static void item(Item item,Map<String,Object> result) {
        result.put("levelKnown",item.levelKnown);
        result.put("curseKnown",item.cursedKnown);
        if (item.levelKnown) result.put("visibleUpgrade",item.visiblyUpgraded());
        if (item.cursedKnown) result.put("visibleCurse",item.visiblyCursed());
        try {
            if (item.isIdentified() && item.getClass().getMethod("desc").getDeclaringClass()==Item.class) {
                result.put("description",Policy.clean(item.desc()));
                result.put("detailStatus","text");
            } else result.put("detailStatus","open_page_required");
        } catch (ReflectiveOperationException e) { result.put("detailStatus","open_page_required"); }
    }
    static Map<String,Object> buff(Buff buff) {
        Map<String,Object> result=Json.obj("name",Policy.clean(buff.name()),
            "display",Policy.clean(buff.iconTextDisplay()),"detailStatus","open_page_required");
        Class<?> type=buff.getClass();
        if (type==Bless.class || type==Haste.class || type==Hunger.class) {
            result.put("description",Policy.clean(buff.desc())); result.put("detailStatus","text");
        }
        return result;
    }
    static Map<String,Object> hero(Hero hero) {
        List<Object> talents=new ArrayList<>();
        for (int tier=0;tier<hero.talents.size();tier++) {
            for (Map.Entry<Talent,Integer> entry:hero.talents.get(tier).entrySet()) {
                Talent talent=entry.getKey();
                talents.add(Json.obj("tier",tier+1,"name",Policy.clean(talent.title()),
                    "points",entry.getValue(),"maxPoints",talent.maxPoints(),"description",Policy.clean(talent.desc())));
            }
        }
        return Json.obj("class",Policy.clean(hero.heroClass.title()),"classDescription",Policy.clean(hero.heroClass.desc()),
            "subclass",Policy.clean(hero.subClass.title()),"subclassDescription",Policy.clean(hero.subClass.desc()),"talents",talents);
    }
}
