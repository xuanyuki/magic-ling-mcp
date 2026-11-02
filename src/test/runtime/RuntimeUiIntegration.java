import com.badlogic.gdx.Files;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Files;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Preferences;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.ScreenUtils;
import com.magicling.myriad.api.MyriadCodexModel;
import com.magicling.myriad.host.MyriadCodexService;
import com.magicling.myriad.host.ReflectiveBestiaryCatalogSource;
import com.magicling.myriad.host.ReflectiveStaffKillSource;
import com.magicling.myriad.host.ui.MyriadCodexButton;
import com.magicling.myriad.host.ui.WndMyriadCodex;
import com.shatteredpixel.shatteredpixeldungeon.Dungeon;
import com.shatteredpixel.shatteredpixeldungeon.Badges;
import com.shatteredpixel.shatteredpixeldungeon.PaswordBadges;
import com.shatteredpixel.shatteredpixeldungeon.GamesInProgress;
import com.shatteredpixel.shatteredpixeldungeon.SPDSettings;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroClass;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.Hero;
import com.shatteredpixel.shatteredpixeldungeon.actors.hero.HeroAction;
import com.shatteredpixel.shatteredpixeldungeon.actors.Actor;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Mob;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Rat;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Snake;
import com.shatteredpixel.shatteredpixeldungeon.actors.mobs.Gnoll;
import com.shatteredpixel.shatteredpixeldungeon.items.Item;
import com.shatteredpixel.shatteredpixeldungeon.desktop.DesktopPlatformSupport;
import com.shatteredpixel.shatteredpixeldungeon.items.weapon.melee.spdtomlpd.StaffofMyriadThings;
import com.shatteredpixel.shatteredpixeldungeon.journal.Bestiary;
import com.shatteredpixel.shatteredpixeldungeon.journal.Journal;
import com.shatteredpixel.shatteredpixeldungeon.journal.Notes;
import com.shatteredpixel.shatteredpixeldungeon.messages.Languages;
import com.shatteredpixel.shatteredpixeldungeon.messages.Messages;
import com.shatteredpixel.shatteredpixeldungeon.scenes.GameScene;
import com.shatteredpixel.shatteredpixeldungeon.scenes.InterlevelScene;
import com.shatteredpixel.shatteredpixeldungeon.levels.Level;
import com.shatteredpixel.shatteredpixeldungeon.ui.ScrollingGridPane;
import com.shatteredpixel.shatteredpixeldungeon.ui.Window;
import com.shatteredpixel.shatteredpixeldungeon.ui.RenderedTextBlock;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndBag;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndJournalItem;
import com.shatteredpixel.shatteredpixeldungeon.windows.WndUseItem;
import com.watabou.noosa.Game;
import com.watabou.noosa.Gizmo;
import com.watabou.noosa.Group;
import com.watabou.noosa.Image;
import com.watabou.noosa.audio.Music;
import com.watabou.noosa.audio.Sample;
import com.watabou.utils.Bundle;
import com.watabou.utils.FileUtils;
import com.watabou.utils.Bundlable;
import com.watabou.utils.PathFinder;

import java.io.ByteArrayOutputStream;
import java.io.ObjectOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs the original renderer and item window with the external agent in a
 * hidden OpenGL window. All fixtures and preferences are confined to output.
 * This is an in-process integration test, with no desktop input automation.
 */
public final class RuntimeUiIntegration {
    private static Path output;
    private static final List<String> results = new ArrayList<>();
    private static Throwable failure;
    private static boolean complete;
    private static boolean evidence;

    public static void main(String[] args) throws Exception {
        output = Path.of(args[0]).toAbsolutePath();
        java.nio.file.Files.createDirectories(output.resolve("state"));
        Gdx.files = new Lwjgl3Files();
        FileUtils.setDefaultFileProperties(
                Files.FileType.Absolute, output.resolve("state").toString() + "/");
        SPDSettings.set(new Lwjgl3Preferences(
                new FileHandle(output.resolve("state/settings.xml").toFile())));
        SPDSettings.language(Languages.CHINESE);
        SPDSettings.interfaceSize(0);
        SPDSettings.scale(2);
        SPDSettings.music(false);
        SPDSettings.soundFx(false);
        SPDSettings.intro(false);
        Messages.setup(Languages.CHINESE);
        Game.version = "0.9.6.5";
        Game.versionCode = 2026100600;
        boolean portrait = args.length > 1 && args[1].equals("portrait");
        evidence = args.length > 2 && args[2].equals("evidence");
        boolean combat = evidence || args.length > 2 && args[2].equals("combat");
        results.add("Scenario: " + (evidence ? "evidence" : combat ? "combat" : "catalog"));
        results.add("Environment: " + System.getProperty("os.name") + " / Java "
                + System.getProperty("java.version"));
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Myriad Codex isolated integration test");
        config.setInitialVisible(false);
        config.disableAudio(true);
        config.setWindowedMode(portrait ? 540 : 960, portrait ? 900 : 640);
        config.setForegroundFPS(15);
        config.setIdleFPS(15);
        config.setPauseWhenLostFocus(false);
        config.setPauseWhenMinimized(false);
        config.useVsync(false);
        config.setPreferencesConfig(output.resolve("state").toString(), Files.FileType.Absolute);
        try {
            new Lwjgl3Application(new Game(
                    combat ? CombatScene.class : TestScene.class,
                    new DesktopPlatformSupport()), config);
            if (!complete && failure == null) failure = new AssertionError("Test stopped before completion");
        } catch (Throwable error) {
            failure = error;
        }
        if (failure != null) {
            failure.printStackTrace();
            results.add("FAIL: " + failure);
        }
        java.nio.file.Files.write(output.resolve("result.txt"), results, StandardCharsets.UTF_8);
        System.out.println(String.join("\n", results));
        System.exit(failure == null ? 0 : 1);
    }

    public static final class TestScene extends GameScene {
        private int frame;
        private StaffofMyriadThings staff;
        private Set<String> expectedKills;
        private String bestiaryBefore;
        private MyriadCodexModel model;
        private WndUseItem details;
        private MyriadCodexButton button;
        private WndMyriadCodex codex;
        private ScrollingGridPane grid;
        private List<ScrollingGridPane.GridItem> cells;
        private WndJournalItem entry;
        private String randomBefore;
        private String heroBefore;
        private String staffBefore;
        private Set<?> actorsBefore;

        @Override
        public void create() {
            try {
                results.add("OpenGL: " + Gdx.graphics.getGLVersion().getDebugVersionString());
                Music.INSTANCE.enable(false);
                Sample.INSTANCE.enable(false);
                Badges.loadGlobal();
                PaswordBadges.loadGlobal();
                Journal.loadGlobal();
                Notes.reset();
                GamesInProgress.selectedClass = HeroClass.WARRIOR;
                GamesInProgress.curSlot = 1;
                Dungeon.overrideSeed = 20261015001L;
                Dungeon.init();
                Dungeon.depth = 1;
                Level level = Dungeon.newLevel();
                Dungeon.switchLevel(level, level.entrance());
                InterlevelScene.mode = InterlevelScene.Mode.NONE;
                super.create();
                staff = new StaffofMyriadThings();
                staff.identify();
                MyriadCodexService service = new MyriadCodexService(
                        new ReflectiveStaffKillSource(), new ReflectiveBestiaryCatalogSource());
                String randomBeforeSnapshot = randomState();
                String heroBeforeSnapshot = stateOf(Dungeon.hero);
                Set<?> actorsBeforeSnapshot = actorState();
                MyriadCodexModel empty = service.snapshot(staff);
                check(randomState().equals(randomBeforeSnapshot),
                        "Reading the catalog preserves the gameplay random generator");
                check(stateOf(Dungeon.hero).equals(heroBeforeSnapshot)
                                && actorState().equals(actorsBeforeSnapshot),
                        "Reading the catalog preserves hero and active actors");
                check(empty.totalCount() > 100, "Real Bestiary exposes its full mob catalog");
                check(empty.killedCount() == 0, "A fresh staff has no kills");
                expectedKills = new HashSet<>();
                for (int i = 0; i < 12; i++) {
                    expectedKills.add(empty.rows().get(i).definition().className());
                }
                expectedKills.add("test.fixture.UnmappedMonster");
                Field kills = StaffofMyriadThings.class.getDeclaredField("killedTypes");
                kills.setAccessible(true);
                @SuppressWarnings("unchecked")
                Set<String> fixtureKills = (Set<String>) kills.get(staff);
                fixtureKills.addAll(expectedKills);
                bestiaryBefore = bestiaryState();
                model = service.snapshot(staff);
                check(model.killedCount() == 12 && model.unmappedCount() == 1,
                        "Twelve catalog kills and one unmapped record exceed the original ten-item limit");
                check(service.snapshot(new StaffofMyriadThings()).killedCount() == 0,
                        "Kill status belongs to this staff instance");
                WndUseItem ordinaryItem = new WndUseItem(null, new Item());
                check(descendants(ordinaryItem, MyriadCodexButton.class).isEmpty(),
                        "An ordinary item receives no codex button");
                ordinaryItem.destroy();
                details = new WndUseItem(null, staff);
                add(details);
                List<MyriadCodexButton> buttons = descendants(details, MyriadCodexButton.class);
                check(buttons.size() == 1, "The real item window has exactly one injected button");
                button = buttons.get(0);
            } catch (Throwable error) {
                fail(error);
            }
        }

        @Override
        public void update() {
            super.update();
            try {
                switch (++frame) {
                    case 4:
                        capture("01-item-details.png");
                        check(GameScene.scene == this, "The game scene is active");
                        randomBefore = randomState();
                        heroBefore = stateOf(Dungeon.hero);
                        staffBefore = stateOf(staff);
                        actorsBefore = actorState();
                        Method click = MyriadCodexButton.class.getDeclaredMethod("onClick");
                        click.setAccessible(true);
                        click.invoke(button);
                        List<WndMyriadCodex> windows = descendants(this, WndMyriadCodex.class);
                        if (windows.isEmpty()) {
                            // Expose a renderer failure that the production button
                            // may have converted into its compatibility dialog.
                            WndMyriadCodex probe = new WndMyriadCodex(model);
                            probe.destroy();
                        }
                        check(windows.size() == 1, "The injected button opens the codex window");
                        codex = windows.get(0);
                        grid = descendants(codex, ScrollingGridPane.class).get(0);
                        cells = descendants(grid, ScrollingGridPane.GridItem.class);
                        check(cells.size() == model.totalCount(),
                                "Every catalog and unmapped record has a grid cell (" + cells.size() + ")");
                        for (ScrollingGridPane.GridItem cell : cells) {
                            for (Image image : descendants(cell, Image.class)) {
                                checkFits(image.width() <= cell.width() && image.height() <= cell.height(),
                                        "Monster icons must fit inside their grid cells");
                            }
                        }
                        results.add("PASS: All monster icons fit inside their grid cells");
                        check(randomState().equals(randomBefore),
                                "Opening the codex preserves the gameplay random generator");
                        check(stateOf(Dungeon.hero).equals(heroBefore)
                                        && stateOf(staff).equals(staffBefore)
                                        && actorState().equals(actorsBefore),
                                "Opening the codex preserves hero, staff and active actors");
                        check(new ReflectiveStaffKillSource().killedClassNames(staff).equals(expectedKills),
                                "Opening the window does not alter staff kills");
                        check(bestiaryState().equals(bestiaryBefore),
                                "Opening the window does not alter global Bestiary data");
                        break;
                    case 8:
                        capture("02-full-codex.png");
                        grid.scrollTo(0, Math.max(0, grid.content().height() - grid.height()));
                        break;
                    case 12:
                        capture("03-codex-last-row.png");
                        ScrollingGridPane.GridItem last = cells.get(cells.size() - 1);
                        float scroll = grid.content().camera().scroll.y;
                        check(last.top() >= scroll && last.bottom() <= scroll + grid.height(),
                                "The complete final row is reachable by scrolling");
                        grid.scrollToCell(0);
                        ScrollingGridPane.GridItem first = cells.get(0);
                        check(first.onClick(first.centerX(), first.centerY()), "A codex entry accepts selection");
                        List<WndJournalItem> entries = descendants(this, WndJournalItem.class);
                        check(entries.size() == 1, "Selecting an entry opens its game detail window");
                        entry = entries.get(0);
                        break;
                    case 16:
                        capture("04-entry-details.png");
                        entry.hide();
                        break;
                    case 20:
                        capture("05-codex-after-detail.png");
                        check(!descendants(cells.get(0), Image.class).isEmpty(),
                                "Closing entry details keeps the original grid icon");
                        check(bestiaryState().equals(bestiaryBefore),
                                "Browsing and closing entry details preserves Bestiary data");
                        int unknownIndex = -1;
                        for (int i = 0; i < model.rows().size(); i++) {
                            if (!model.rows().get(i).definition().seenInBestiary()
                                    && !model.rows().get(i).killedByStaff()
                                    && model.rows().get(i).status()
                                        != com.magicling.myriad.api.StaffKillStatus.UNMAPPED_KILL) {
                                unknownIndex = i;
                                break;
                            }
                        }
                        check(unknownIndex >= 0, "The fixture includes an undiscovered monster");
                        ScrollingGridPane.GridItem unknown = cells.get(unknownIndex);
                        unknown.onClick(unknown.centerX(), unknown.centerY());
                        entry = descendants(this, WndJournalItem.class).get(0);
                        boolean masked = false;
                        for (RenderedTextBlock text : descendants(entry, RenderedTextBlock.class)) {
                            if ("???".equals(text.text())) masked = true;
                        }
                        check(masked, "An undiscovered, unkilled monster keeps its hidden name");
                        break;
                    case 24:
                        capture("06-undiscovered-entry.png");
                        entry.hide();
                        check(bestiaryState().equals(bestiaryBefore),
                                "Viewing an undiscovered monster does not discover it");
                        complete = true;
                        results.add("PASS: Runtime UI integration completed");
                        Gdx.app.exit();
                        break;
                    default:
                        break;
                }
            } catch (Throwable error) {
                fail(error);
            }
        }
    }

    /**
     * Drives the game's HeroAction, animation callback and Actor scheduler.
     * Only test setup equips a staff and supplies adjacent monsters. Kill
     * records are produced exclusively by the original combat code.
     */
    public static final class CombatScene extends GameScene {
        private final MyriadCodexService service = new MyriadCodexService(
                new ReflectiveStaffKillSource(), new ReflectiveBestiaryCatalogSource());
        private StaffofMyriadThings staff;
        private StaffofMyriadThings otherStaff;
        private Mob target;
        private WndMyriadCodex codex;
        private WndJournalItem entry;
        private int stage;
        private int frame;
        private int stageFrame;
        private int targetCell;
        private int attacks;
        private int cycles;
        private String stableHero;
        private String stableStaff;
        private String stableBestiary;
        private Set<?> stableActors;
        private int stableWindowCount;
        private WndBag evidenceBag;
        private WndUseItem evidenceDetails;
        private int evidenceMinBefore;
        private int evidenceMaxBefore;

        @Override
        public void create() {
            try {
                results.add("OpenGL: " + Gdx.graphics.getGLVersion().getDebugVersionString());
                Music.INSTANCE.enable(false);
                Sample.INSTANCE.enable(false);
                Badges.loadGlobal();
                PaswordBadges.loadGlobal();
                Journal.loadGlobal();
                Notes.reset();
                GamesInProgress.selectedClass = HeroClass.WARRIOR;
                GamesInProgress.curSlot = 1;
                Dungeon.overrideSeed = 20261015001L;
                Dungeon.init();
                Dungeon.depth = 1;
                Level level = Dungeon.newLevel();
                Dungeon.switchLevel(level, level.entrance());
                for (Mob mob : new ArrayList<>(level.mobs)) {
                    Actor.remove(mob);
                }
                level.mobs.clear();
                staff = new StaffofMyriadThings();
                staff.identify();
                otherStaff = new StaffofMyriadThings();
                Dungeon.hero.belongings.weapon = staff;
                // A normal attainable strength avoids under-strength penalties.
                Dungeon.hero.STR = 18;
                targetCell = -1;
                for (int delta : PathFinder.NEIGHBOURS8) {
                    int cell = Dungeon.hero.pos + delta;
                    if (cell >= 0 && cell < level.length()
                            && level.passable[cell] && !level.pit[cell]) {
                        targetCell = cell;
                        break;
                    }
                }
                check(targetCell >= 0, "An adjacent floor cell is available for combat");
                InterlevelScene.mode = InterlevelScene.Mode.NONE;
                super.create();
                check(service.snapshot(staff).killedCount() == 0,
                        "Combat starts with a fresh staff and zero kill records");
                if (evidence) transition(17);
            } catch (Throwable error) {
                fail(error);
            }
        }

        @Override
        public void update() {
            super.update();
            try {
                frame++;
                if (frame > 1050) throw new AssertionError("Combat lifecycle timed out at stage " + stage);
                switch (stage) {
                    case 0:
                        if (heroWaiting()) {
                            spawn(new Rat());
                            transition(1);
                        }
                        break;
                    case 1:
                        if (finishCombat()) {
                            check(staff.CengShu() == 1 && killed(Rat.class),
                                    "A real HeroAction attack records the first Rat kill");
                            openCodex(1);
                            transition(2);
                        }
                        break;
                    case 2:
                        if (frame - stageFrame >= 3) {
                            capture("01-first-combat-kill.png");
                            codex.hide();
                            transition(3);
                        }
                        break;
                    case 3:
                        if (heroWaiting()) {
                            spawn(new Rat());
                            transition(4);
                        }
                        break;
                    case 4:
                        if (finishCombat()) {
                            check(staff.CengShu() == 1 && service.snapshot(staff).killedCount() == 1,
                                    "A second actual Rat kill does not duplicate its species");
                            spawn(new Gnoll());
                            transition(5);
                        }
                        break;
                    case 5:
                        if (finishCombat()) {
                            check(staff.CengShu() == 2 && killed(Rat.class) && killed(Gnoll.class),
                                    "A real Gnoll kill adds the second catalog species");
                            check(service.snapshot(otherStaff).killedCount() == 0,
                                    "Actual combat leaves a different staff's records empty");
                            Bundle stored = new Bundle();
                            staff.storeInBundle(stored);
                            StaffofMyriadThings restored = new StaffofMyriadThings();
                            restored.restoreFromBundle(stored);
                            check(new ReflectiveStaffKillSource().killedClassNames(restored)
                                            .equals(new ReflectiveStaffKillSource().killedClassNames(staff)),
                                    "Original staff save/restore preserves combat-earned kill records");
                            openCodex(2);
                            transition(6);
                        }
                        break;
                    case 6:
                        if (frame - stageFrame >= 3) {
                            capture("02-second-species-kill.png");
                            codex.hide();
                            transition(14);
                        }
                        break;
                    case 7:
                        // Let combat sprites/effects finish before lifecycle checks.
                        if (frame - stageFrame >= 30 && heroWaiting()) {
                            stableHero = stateOf(Dungeon.hero);
                            stableStaff = stateOf(staff);
                            stableBestiary = bestiaryState();
                            stableActors = actorState();
                            stableWindowCount = descendants(this, Window.class).size();
                            transition(8);
                        }
                        break;
                    case 8:
                        openCodex(2, 1);
                        transition(9);
                        break;
                    case 9:
                        ScrollingGridPane grid = descendants(codex, ScrollingGridPane.class).get(0);
                        List<ScrollingGridPane.GridItem> cells =
                                descendants(grid, ScrollingGridPane.GridItem.class);
                        MyriadCodexModel displayed = displayedModel(codex);
                        int selected = -1;
                        for (int i = 0; i < displayed.rows().size(); i++) {
                            if (displayed.rows().get(i).definition().className().equals(Rat.class.getName())) {
                                selected = i;
                                break;
                            }
                        }
                        checkFits(selected >= 0, "Killed Rat must remain in the displayed model");
                        grid.scrollToCell(selected);
                        ScrollingGridPane.GridItem cell = cells.get(selected);
                        checkFits(cell.onClick(cell.centerX(), cell.centerY()), "Killed row accepts selection");
                        List<WndJournalItem> entries = descendants(this, WndJournalItem.class);
                        checkFits(entries.size() == 1, "Exactly one entry detail window is open");
                        entry = entries.get(0);
                        boolean killedLabel = false;
                        for (RenderedTextBlock text : descendants(entry, RenderedTextBlock.class)) {
                            if (text.text().contains("本杖已击杀")) killedLabel = true;
                        }
                        checkFits(killedLabel, "Combat-earned Rat entry must show the killed label");
                        transition(10);
                        break;
                    case 10:
                        if (cycles == 0) capture("04-combat-earned-entry-detail.png");
                        entry.hide();
                        transition(11);
                        break;
                    case 11:
                        ScrollingGridPane scrollGrid =
                                descendants(codex, ScrollingGridPane.class).get(0);
                        List<ScrollingGridPane.GridItem> scrollCells =
                                descendants(scrollGrid, ScrollingGridPane.GridItem.class);
                        scrollGrid.scrollTo(0,
                                Math.max(0, scrollGrid.content().height() - scrollGrid.height()));
                        ScrollingGridPane.GridItem last = scrollCells.get(scrollCells.size() - 1);
                        float scroll = scrollGrid.content().camera().scroll.y;
                        checkFits(last.top() >= scroll && last.bottom() <= scroll + scrollGrid.height(),
                                "Final row remains reachable after repeated opening");
                        transition(12);
                        break;
                    case 12:
                        if (cycles == 19) capture("05-after-twenty-open-close-cycles.png");
                        codex.hide();
                        transition(13);
                        break;
                    case 13:
                        checkFits(descendants(this, Window.class).size() == stableWindowCount,
                                "Closing the codex must restore the original window count");
                        checkFits(stateOf(Dungeon.hero).equals(stableHero)
                                        && stateOf(staff).equals(stableStaff)
                                        && bestiaryState().equals(stableBestiary)
                                        && actorState().equals(stableActors),
                                "Repeated browsing must preserve combat state and discovery");
                        if (++cycles < 20) {
                            transition(8);
                        } else {
                            check(true, "Twenty full open/detail/scroll/close cycles preserve game state");
                            complete = true;
                            results.add("PASS: Combat and lifecycle integration completed");
                            Gdx.app.exit();
                        }
                        break;
                    case 14:
                        if (heroWaiting()) {
                            spawn(new Snake());
                            transition(15);
                        }
                        break;
                    case 15:
                        if (finishCombat()) {
                            check(staff.CengShu() == 3 && killed(Snake.class),
                                    "Actual combat records Snake even though the game catalog omits it");
                            MyriadCodexModel updated = service.snapshot(staff);
                            check(updated.killedCount() == 2 && updated.unmappedCount() == 1,
                                    "The actual non-catalog Snake kill is preserved as an unmapped row");
                            openCodex(2, 1);
                            transition(16);
                        }
                        break;
                    case 16:
                        if (frame - stageFrame >= 3) {
                            capture("03-combat-earned-unmapped-kill.png");
                            codex.hide();
                            transition(7);
                        }
                        break;
                    case 17:
                        if (heroWaiting()) {
                            check(Dungeon.hero.belongings.weapon == staff
                                            && staff.isEquipped(Dungeon.hero),
                                    "Screenshot sequence uses the same equipped staff throughout");
                            evidenceBag = new WndBag(Dungeon.hero.belongings.backpack);
                            add(evidenceBag);
                            transition(18);
                        }
                        break;
                    case 18:
                        if (frame - stageFrame >= 4) {
                            capture("01-equipped-staff.png");
                            evidenceBag.hide();
                            evidenceMinBefore = staff.min(staff.buffedLvl());
                            evidenceMaxBefore = staff.max(staff.buffedLvl());
                            evidenceDetails = new WndUseItem(null, staff);
                            add(evidenceDetails);
                            transition(19);
                        }
                        break;
                    case 19:
                        if (frame - stageFrame >= 4) {
                            captureStaffEvidence("02-before-kill-data", 0);
                            evidenceDetails.hide();
                            spawn(new Rat());
                            transition(20);
                        }
                        break;
                    case 20:
                        if (finishCombat()) {
                            check(staff.CengShu() == 1 && killed(Rat.class),
                                    "The original combat code adds the first Rat kill");
                            check(staff.min(staff.buffedLvl()) == evidenceMinBefore
                                            && staff.max(staff.buffedLvl()) == evidenceMaxBefore + 1,
                                    "Original weapon damage changes only by its normal first-kill bonus");
                            evidenceDetails = new WndUseItem(null, staff);
                            add(evidenceDetails);
                            transition(21);
                        }
                        break;
                    case 21:
                        if (frame - stageFrame >= 4) {
                            captureStaffEvidence("03-after-kill-data", 1);
                            evidenceDetails.hide();
                            openCodex(1);
                            transition(22);
                        }
                        break;
                    case 22:
                        if (frame - stageFrame >= 4) {
                            capture("04-added-codex-window.png");
                            ScrollingGridPane evidenceGrid =
                                    descendants(codex, ScrollingGridPane.class).get(0);
                            MyriadCodexModel evidenceModel = displayedModel(codex);
                            int ratIndex = -1;
                            for (int i = 0; i < evidenceModel.rows().size(); i++) {
                                if (evidenceModel.rows().get(i).definition().className()
                                        .equals(Rat.class.getName())) {
                                    ratIndex = i;
                                    break;
                                }
                            }
                            checkFits(ratIndex >= 0, "The earned Rat kill has a codex row");
                            ScrollingGridPane.GridItem ratCell = descendants(evidenceGrid,
                                    ScrollingGridPane.GridItem.class).get(ratIndex);
                            evidenceGrid.scrollTo(0, Math.max(0, Math.min(
                                    evidenceGrid.content().height() - evidenceGrid.height(),
                                    ratCell.top() - evidenceGrid.height() / 2)));
                            transition(23);
                        }
                        break;
                    case 23:
                        if (frame - stageFrame >= 4) {
                            ScrollingGridPane evidenceGrid =
                                    descendants(codex, ScrollingGridPane.class).get(0);
                            List<ScrollingGridPane.GridItem> evidenceCells =
                                    descendants(evidenceGrid, ScrollingGridPane.GridItem.class);
                            MyriadCodexModel evidenceModel = displayedModel(codex);
                            for (int i = 0; i < evidenceModel.rows().size(); i++) {
                                if (evidenceModel.rows().get(i).definition().className()
                                        .equals(Rat.class.getName())) {
                                    ScrollingGridPane.GridItem ratCell = evidenceCells.get(i);
                                    float evidenceScroll = evidenceGrid.content().camera().scroll.y;
                                    checkFits(ratCell.top() >= evidenceScroll
                                                    && ratCell.bottom() <= evidenceScroll + evidenceGrid.height(),
                                            "The earned Rat row is visible in its screenshot");
                                    capture("05-earned-kill-in-codex.png");
                                    checkFits(ratCell.onClick(ratCell.centerX(), ratCell.centerY()),
                                            "The earned Rat row opens its details");
                                    break;
                                }
                            }
                            entry = descendants(this, WndJournalItem.class).get(0);
                            boolean evidenceKilledLabel = false;
                            for (RenderedTextBlock text : descendants(entry, RenderedTextBlock.class)) {
                                if (text.text().contains("本杖已击杀")) evidenceKilledLabel = true;
                            }
                            check(evidenceKilledLabel, "The new popup identifies the combat-earned Rat kill");
                            transition(24);
                        }
                        break;
                    case 24:
                        if (frame - stageFrame >= 4) {
                            capture("06-earned-kill-details.png");
                            entry.hide();
                            codex.hide();
                            check(staff.CengShu() == 1
                                            && service.snapshot(otherStaff).killedCount() == 0,
                                    "Browsing preserves earned kills and keeps the other staff empty");
                            complete = true;
                            results.add("PASS: Equipment, before/after combat data and popup screenshots completed");
                            Gdx.app.exit();
                        }
                        break;
                    default:
                        throw new AssertionError("Unexpected stage " + stage);
                }
            } catch (Throwable error) {
                fail(error);
            }
        }

        private void transition(int next) {
            stage = next;
            stageFrame = frame;
        }

        private boolean heroWaiting() {
            return Dungeon.hero.ready && Actor.getCurrentActorClass() == Hero.class;
        }

        private void spawn(Mob mob) {
            target = mob;
            target.pos = targetCell;
            GameScene.add(target);
            attacks = 0;
            attackTarget();
        }

        private void attackTarget() {
            if (++attacks > 30) throw new AssertionError("Too many attacks against " + target.getClass());
            Dungeon.hero.curAction = new HeroAction.Attack(target);
            Dungeon.hero.ready = false;
            Dungeon.hero.next();
        }

        private boolean finishCombat() throws Exception {
            if (!heroWaiting()) return false;
            if (target.isAlive()) {
                attackTarget();
                return false;
            }
            return killed(target.getClass());
        }

        private boolean killed(Class<?> type) throws Exception {
            return new ReflectiveStaffKillSource().killedClassNames(staff).contains(type.getName());
        }

        private void captureStaffEvidence(String name, int expectedCount) throws Exception {
            Set<String> kills = new ReflectiveStaffKillSource().killedClassNames(staff);
            check(staff.CengShu() == expectedCount && kills.size() == expectedCount,
                    name + " matches the original staff kill count " + expectedCount);
            String details = "Staff identity: " + System.identityHashCode(staff)
                    + "\nEquipped: " + staff.isEquipped(Dungeon.hero)
                    + "\nHero strength: " + Dungeon.hero.STR
                    + "\nWeapon level: " + staff.buffedLvl()
                    + "\nUnique killed species: " + staff.CengShu()
                    + "\nDamage range: " + staff.min(staff.buffedLvl())
                    + "-" + staff.max(staff.buffedLvl())
                    + "\nKilled classes: " + new java.util.TreeSet<>(kills)
                    + "\n\nOriginal weapon description:\n" + staff.desc()
                    + "\n\nOriginal serialized staff:\n" + stateOf(staff) + "\n";
            java.nio.file.Files.writeString(output.resolve(name + ".txt"), details, StandardCharsets.UTF_8);
            capture(name + ".png");
        }

        private void openCodex(int expectedCount) throws Exception {
            openCodex(expectedCount, 0);
        }

        private void openCodex(int expectedCount, int expectedUnmapped) throws Exception {
            String beforeRandom = randomState();
            String beforeHero = stateOf(Dungeon.hero);
            String beforeStaff = stateOf(staff);
            Set<?> beforeActors = actorState();
            String beforeBestiary = bestiaryState();
            WndUseItem details = new WndUseItem(null, staff);
            add(details);
            List<MyriadCodexButton> buttons = descendants(details, MyriadCodexButton.class);
            checkFits(buttons.size() == 1, "A reopened staff window must contain exactly one button");
            Method click = MyriadCodexButton.class.getDeclaredMethod("onClick");
            click.setAccessible(true);
            click.invoke(buttons.get(0));
            List<WndMyriadCodex> codices = descendants(this, WndMyriadCodex.class);
            checkFits(codices.size() == 1, "Exactly one codex window must open");
            codex = codices.get(0);
            MyriadCodexModel displayed = displayedModel(codex);
            checkFits(displayed.killedCount() == expectedCount
                            && displayed.unmappedCount() == expectedUnmapped,
                    "Reopened UI records differ: expected " + expectedCount + "/" + expectedUnmapped
                            + ", got " + displayed.killedCount() + "/" + displayed.unmappedCount());
            ScrollingGridPane grid = descendants(codex, ScrollingGridPane.class).get(0);
            checkFits(descendants(grid, ScrollingGridPane.GridItem.class).size() == displayed.totalCount(),
                    "The reopened UI must show the complete bestiary");
            checkFits(beforeRandom.equals(randomState()) && beforeHero.equals(stateOf(Dungeon.hero))
                            && beforeStaff.equals(stateOf(staff)) && beforeActors.equals(actorState())
                            && beforeBestiary.equals(bestiaryState()),
                    "Opening after combat must preserve RNG, hero, staff, actors and Bestiary");
            if (cycles == 0) {
                results.add("PASS: Reopened UI shows " + expectedCount
                        + " combat-earned catalog species, " + expectedUnmapped
                        + " unmapped kills and all " + displayed.totalCount() + " rows");
            }
        }
    }

    private static MyriadCodexModel displayedModel(WndMyriadCodex window) throws Exception {
        Field field = WndMyriadCodex.class.getDeclaredField("model");
        field.setAccessible(true);
        return (MyriadCodexModel) field.get(window);
    }

    private static String bestiaryState() {
        Bundle bundle = new Bundle();
        Bestiary.store(bundle);
        return bundle.toString();
    }

    private static String stateOf(Bundlable value) {
        Bundle bundle = new Bundle();
        value.storeInBundle(bundle);
        return bundle.toString();
    }

    private static Set<?> actorState() throws Exception {
        Field actors = Actor.class.getDeclaredField("all");
        actors.setAccessible(true);
        return new HashSet<>((Set<?>) actors.get(null));
    }

    private static void capture(String name) {
        Pixmap pixmap = ScreenUtils.getFrameBufferPixmap(
                0, 0, Gdx.graphics.getBackBufferWidth(), Gdx.graphics.getBackBufferHeight());
        try {
            PixmapIO.writePNG(new FileHandle(output.resolve(name).toFile()), pixmap, -1, true);
        } finally {
            pixmap.dispose();
        }
    }

    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
        results.add("PASS: " + message);
    }

    private static void checkFits(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    private static String randomState() throws Exception {
        Class<?> randomType = Class.forName("com.watabou.utils.Random");
        synchronized (randomType) {
            Field generators = randomType.getDeclaredField("generators");
            generators.setAccessible(true);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (ObjectOutputStream stream = new ObjectOutputStream(bytes)) {
                stream.writeObject(generators.get(null));
            }
            return Base64.getEncoder().encodeToString(bytes.toByteArray());
        }
    }

    private static void fail(Throwable error) {
        if (failure == null) failure = error;
        Gdx.app.exit();
    }

    private static <T> List<T> descendants(Group group, Class<T> type) throws Exception {
        List<T> found = new ArrayList<>();
        Field membersField = Group.class.getDeclaredField("members");
        membersField.setAccessible(true);
        @SuppressWarnings("unchecked")
        List<Gizmo> members = (List<Gizmo>) membersField.get(group);
        for (Gizmo member : new ArrayList<>(members)) {
            if (type.isInstance(member)) found.add(type.cast(member));
            if (member instanceof Group) found.addAll(descendants((Group) member, type));
        }
        return found;
    }
}
