package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * 抽屉文本容量硬验收（UI 抽屉化批3·仿 UnitInfoTextSeedDataTest）：描述框改 scene2d ScrollPane 后，
 * <b>最长真实文案必须能滚到底</b>——介绍页 24 可购棋子 × 3 星共 72 组（折 12 列后最多 23 行）、
 * 装备描述全部模板（折 10 列）。headless：ScrollPane 的布局/滚动数学零 GL（用与 DrawerTextBlock 同一高度公式
 * 撑高一个占位内容 Actor，不构造 Assets）。
 */
class DrawerTextCapacityTest {

    private static GameData data;
    private static com.badlogic.gdx.Application savedApp;

    @BeforeAll
    static void loadSeedData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
        // ScrollPane 构造会建 ActorGestureListener → GestureDetector → Timer.Task（要求 Gdx.app 非空）——
        // headless 用代理桩（沿 RealArtTest 桩 Gdx.files 的先例）；布局 / 滚动数学本身零 GL
        savedApp = Gdx.app;
        Gdx.app = (com.badlogic.gdx.Application) java.lang.reflect.Proxy.newProxyInstance(
                com.badlogic.gdx.Application.class.getClassLoader(),
                new Class<?>[]{com.badlogic.gdx.Application.class},
                (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (type == int.class) {
                        return 0;
                    }
                    if (type == long.class) {
                        return 0L;
                    }
                    if (type == boolean.class) {
                        return Boolean.FALSE;
                    }
                    return null;
                });
    }

    @AfterAll
    static void restoreApp() {
        Gdx.app = savedApp;
    }

    /** 占位内容 Actor（高度 = 行数 × 行高 + 留白，与 DrawerTextBlock 同式）——ScrollPane 只读它的宽高 */
    private static Actor content(int lineCount, float lineHeight, float width) {
        Actor actor = new Actor();
        actor.setSize(width, DrawerTextBlock.heightFor(lineCount, lineHeight));
        return actor;
    }

    /** 断言：滚动到底后视口底缘恰见内容最后一行（scrollY == maxY，且末行在视口内） */
    private static void assertScrollsToEnd(ScrollPane pane, int lineCount, float lineHeight) {
        pane.layout();
        float contentHeight = DrawerTextBlock.heightFor(lineCount, lineHeight);
        float maxScroll = pane.getMaxY();
        assertThat(maxScroll).isCloseTo(Math.max(0f, contentHeight - pane.getHeight()), within(1e-3f));
        assertThat(pane.getMaxY() > 0f).as("需要纵向滚动 ⇔ maxY > 0（1.14.0 无 isScrollYRequired）").isEqualTo(maxScroll > 0f);
        pane.setScrollY(Float.MAX_VALUE); // 滚到底（ScrollPane 钳到 maxY）
        pane.layout();
        assertThat(pane.getScrollY()).isCloseTo(maxScroll, within(1e-3f));
        if (lineCount > 0) {
            // 视口顶在内容坐标里的位置 = scrollY + 视口高；末行行顶 = 上留白 + (行数−1)×行高，须 ≤ 视口顶
            float viewportTop = pane.getScrollY() + pane.getHeight();
            float lastLineTop = DrawerTextBlock.PAD_TOP + (lineCount - 1) * lineHeight;
            assertThat(viewportTop).as("末行行顶 %.1f ≤ 滚到底后的视口顶 %.1f", lastLineTop, viewportTop)
                    .isGreaterThanOrEqualTo(lastLineTop);
            assertThat(pane.getScrollY()).as("首行（行顶 = 上留白）滚到底后已在视口上方或恰在视口内")
                    .isLessThanOrEqualTo(DrawerTextBlock.PAD_TOP + lineCount * lineHeight);
        }
    }

    @Test
    @DisplayName("硬验收·介绍页：24 可购棋子 × 1~3 星 = 72 组全量行集（不截断）在 112×60 视口里都能滚到底；最长 23 行")
    void introScrollsToEndForAllSeedUnits() {
        DrawerGeometry.Rect viewport = DrawerGeometry.descriptionViewport();
        int checked = 0;
        int overflow = 0;
        int maxLines = 0;
        String maxUnit = "";
        for (UnitData template : data.getUnits().values()) {
            if (template.isBoss()) {
                continue;
            }
            for (int star = 1; star <= 3; star++) {
                List<String> lines = DrawerInfoText.introLines(new Unit(11, template, star), data);
                assertThat(lines).as("%s %d星 介绍非空", template.getId(), star).isNotEmpty();
                assertThat(lines).allSatisfy(line ->
                        assertThat(UnitInfoText.columns(line)).isLessThanOrEqualTo(DrawerInfoText.DESC_MAX_COLUMNS));
                assertThat(lines).as("%s %d星 全量不截断", template.getId(), star)
                        .doesNotContain("…");
                ScrollPane pane = new ScrollPane(content(lines.size(), DrawerSkin.SMALL_LINE_HEIGHT, viewport.width()));
                pane.setSmoothScrolling(false);
                pane.setSize(viewport.width(), viewport.height());
                assertScrollsToEnd(pane, lines.size(), DrawerSkin.SMALL_LINE_HEIGHT);
                if (lines.size() > DrawerInfoText.DESC_VISIBLE_LINES) {
                    overflow++;
                }
                if (lines.size() > maxLines) {
                    maxLines = lines.size();
                    maxUnit = template.getId() + " " + star + "星";
                }
                checked++;
            }
        }
        assertThat(checked).isEqualTo(72);
        assertThat(overflow).as("全部组合都超不滚动可见 6 行——Scrollable 是必需而非可选").isEqualTo(72);
        assertThat(maxLines).as("最长真实文案行数锚（数据变更时同步；最坏 = %s）", maxUnit).isEqualTo(23);
        // 对照：批2 过渡态的截断版只能显示 6 行 → 末行 …；现已无截断路径
        assertThat(UnitInfoText.clipLines(new java.util.ArrayList<String>(java.util.Collections.nCopies(23, "x")),
                DrawerInfoText.DESC_VISIBLE_LINES).size()).isEqualTo(DrawerInfoText.DESC_VISIBLE_LINES);
    }

    @Test
    @DisplayName("装备描述：全部装备模板（折 10 列，不截断）在 98×28 视口里都能滚到底；空列表不滚")
    void equipmentDescriptionScrollsToEndForAllTemplates() {
        DrawerGeometry.Rect viewport = DrawerGeometry.equipDescViewport();
        int checked = 0;
        int scrollable = 0;
        for (EquipmentData template : data.getEquipments().values()) {
            List<String> lines = DrawerEquipText.descriptionLines(template, DrawerEquipText.DESC_MAX_COLUMNS);
            assertThat(lines).as("%s 描述非空", template.getId()).isNotEmpty();
            ScrollPane pane = new ScrollPane(content(lines.size(), DrawerSkin.SMALL_LINE_HEIGHT, viewport.width()));
            pane.setSmoothScrolling(false);
            pane.setSize(viewport.width(), viewport.height());
            assertScrollsToEnd(pane, lines.size(), DrawerSkin.SMALL_LINE_HEIGHT);
            if (pane.getMaxY() > 0f) {
                scrollable++;
            }
            checked++;
        }
        assertThat(checked).isGreaterThan(0);
        assertThat(scrollable).as("至少有模板的描述超 3 行、需要滚动（龙心 5 行）").isGreaterThan(0);
        ScrollPane empty = new ScrollPane(content(0, DrawerSkin.SMALL_LINE_HEIGHT, viewport.width()));
        empty.setSmoothScrolling(false);
        empty.setSize(viewport.width(), viewport.height());
        assertScrollsToEnd(empty, 0, DrawerSkin.SMALL_LINE_HEIGHT);
        assertThat(empty.getMaxY()).isZero();
    }

    @Test
    @DisplayName("内容高度公式：上留白 + 行数 × 行高 + 下留白（0 行 = 8；23 行 × 9 = 215；列宽 ≤ 视口净宽不横向滚动）")
    void textBlockHeightFormula() {
        assertThat(DrawerTextBlock.heightFor(0, 9f)).isEqualTo(8f);
        assertThat(DrawerTextBlock.heightFor(6, 9f)).isEqualTo(62f);
        assertThat(DrawerTextBlock.heightFor(23, 9f)).isEqualTo(215f);
        DrawerGeometry.Rect intro = DrawerGeometry.descriptionViewport();
        ScrollPane pane = new ScrollPane(content(23, DrawerSkin.SMALL_LINE_HEIGHT, intro.width()));
        pane.setSize(intro.width(), intro.height());
        pane.layout();
        assertThat(pane.getMaxX()).as("只纵向滚动（maxX 恒 0）").isZero();
        // 12 列全角行 = 108px，自左留白 4 起至视口右缘 112 恰好收口（右缘留白由描述框内缩的 4px 承担）
        assertThat(DrawerTextBlock.PAD_X + DrawerInfoText.DESC_MAX_COLUMNS * 9f).isLessThanOrEqualTo(intro.width());
    }

    @Test
    @DisplayName("回归（探针实证）：内容后改高度必须作废 ScrollPane 布局缓存——否则沿用首布局的旧内容高度、滚轮被消费却滚不动")
    void contentResizeRequiresInvalidateLayout() {
        DrawerGeometry.Rect viewport = DrawerGeometry.descriptionViewport();
        Actor block = content(1, DrawerSkin.SMALL_LINE_HEIGHT, viewport.width()); // 先是占位 1 行
        ScrollPane pane = new ScrollPane(block);
        pane.setSmoothScrolling(false);
        pane.setSize(viewport.width(), viewport.height());
        pane.validate(); // 首帧真实路径：validate 清 needsLayout 并布局（直接调 layout() 不清标志——非真实路径）
        assertThat(pane.getMaxY()).as("占位 1 行无需滚动").isZero();

        block.setSize(viewport.width(), DrawerTextBlock.heightFor(23, DrawerSkin.SMALL_LINE_HEIGHT)); // 选中棋子 → 23 行
        pane.validate(); // 真实路径（act/draw → validate）：仅 needsLayout 时才重算——内容自行改高不会触发
        assertThat(pane.getMaxY()).as("未作废缓存：ScrollPane 仍以为内容只有 17px——这正是探针抓到的「滚不动」").isZero();

        pane.invalidate(); // DrawerSkin.setContent 的做法
        pane.validate();
        assertThat(pane.getMaxY()).isEqualTo(215f - viewport.height());
        pane.setScrollY(999f);
        pane.layout();
        assertThat(pane.getScrollY()).as("作废后可滚到底").isEqualTo(215f - viewport.height());
    }
}
