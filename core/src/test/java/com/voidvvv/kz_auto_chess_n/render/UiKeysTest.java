package com.voidvvv.kz_auto_chess_n.render;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * UiKeys 测试（UI 抽屉化批0）：键 ↔ 文件名 ↔ 九宫格切分线，与采购单
 * {@code assets_src/ui_redesign/ASSET_REQUIREMENTS.md} §2.1（必需 12）/ §2.2（加分 9）逐字对账。
 * 纯常量表直测（零 Gdx）——任何改错 id / 切分线 / 源尺寸的提交在此红测。
 */
class UiKeysTest {

    @Test
    @DisplayName("必需 12 键：id 与采购单 §2.1 逐字一致、顺序一致")
    void requiredIdsMatchProcurementSheet() {
        assertThat(ids(UiKeys.REQUIRED)).containsExactly(
                "ui_panel_body_9p", "ui_inset_9p", "ui_slot_9p", "ui_slot_selected_9p",
                "ui_tab_active_9p", "ui_tab_inactive_9p", "ui_scroll_track_9p", "ui_scroll_knob_9p",
                "ui_handle", "ui_btn_close", "ui_divider_h", "ui_title_plate_9p");
    }

    @Test
    @DisplayName("加分 8 键：id 与采购单 §2.2（修订版：B02 不采购不计入）逐字一致、顺序一致")
    void bonusIdsMatchProcurementSheet() {
        assertThat(ids(UiKeys.BONUS)).containsExactly(
                "ui_corner_ornament", "ui_scroll_arrow", "ui_slot_hover_9p",
                "ui_preview_board_9p", "ui_btn_close_pressed",
                "ui_icon_equip_weapon", "ui_icon_equip_armor", "ui_icon_equip_trinket");
    }

    @Test
    @DisplayName("原创按钮态 6 键：generated_v2 新增（四态通用按钮 + 关闭钮悬停/禁用），尺寸/切分线逐字锁定")
    void buttonStateIdsMatchGeneratedManifest() {
        assertThat(ids(UiKeys.BUTTONS)).containsExactly(
                "ui_button_normal", "ui_button_hover", "ui_button_pressed", "ui_button_disabled",
                "ui_btn_close_hover", "ui_btn_close_disabled");
        assertEntry("ui_button_normal", 48, 24, 8, 8, 8, 8);
        assertEntry("ui_button_hover", 48, 24, 8, 8, 8, 8);
        assertEntry("ui_button_pressed", 48, 24, 8, 8, 8, 8);
        assertEntry("ui_button_disabled", 48, 24, 8, 8, 8, 8);
        assertEntry("ui_btn_close_hover", 14, 14, 0, 0, 0, 0);
        assertEntry("ui_btn_close_disabled", 14, 14, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("全键集 = 必需 + 加分 + 按钮态（26 键、互异）；文件名 = {id}.png")
    void allKeysAreRequiredPlusBonusWithStableFileNames() {
        List<String> expected = new java.util.ArrayList<String>(ids(UiKeys.REQUIRED));
        expected.addAll(ids(UiKeys.BONUS));
        expected.addAll(ids(UiKeys.BUTTONS));
        assertThat(UiKeys.ALL).hasSize(26);
        assertThat(ids(UiKeys.ALL)).containsExactlyElementsOf(expected);
        assertThat(UiKeys.allIds()).isEqualTo(expected);
        Set<String> ids = new HashSet<String>();
        for (UiKeys.Entry entry : UiKeys.ALL) {
            assertThat(ids.add(entry.id())).as("id 互异: %s", entry.id()).isTrue();
            assertThat(entry.fileName()).isEqualTo(entry.id() + ".png");
        }
    }

    @Test
    @DisplayName("源尺寸与九宫格切分线（左/右/上/下）：20 键逐一对照采购单（1:1 列）")
    void sizesAndSplitsMatchProcurementSheet() {
        // id, 源宽, 源高, 左, 右, 上, 下（采购单「1:1 列」；「不拉伸」= 四边 0）。
        // 必需 12 张已由 deepseek 交付：尺寸/切分线经「采购单 ↔ manifest ↔ 实际 PNG IHDR/像素」三方对账一致
        // （逐张核验记录见 UiArtInventoryTest 与批0 交付报告）。
        assertEntry("ui_panel_body_9p", 64, 64, 12, 12, 12, 12);
        assertEntry("ui_inset_9p", 24, 24, 6, 6, 6, 6);
        assertEntry("ui_slot_9p", 36, 36, 8, 8, 8, 8);
        assertEntry("ui_slot_selected_9p", 36, 36, 8, 8, 8, 8);
        assertEntry("ui_tab_active_9p", 32, 18, 6, 6, 6, 6);
        assertEntry("ui_tab_inactive_9p", 32, 18, 6, 6, 6, 6);
        assertEntry("ui_scroll_track_9p", 16, 16, 4, 4, 4, 4);
        assertEntry("ui_scroll_knob_9p", 12, 12, 4, 4, 4, 4);
        assertEntry("ui_handle", 14, 72, 0, 0, 12, 12);            // 纵向可拉伸 0/0/12/12
        assertEntry("ui_btn_close", 14, 14, 0, 0, 0, 0);           // 不拉伸
        assertEntry("ui_divider_h", 24, 4, 4, 4, 0, 0);            // 左右各 4，上下不拉伸
        assertEntry("ui_title_plate_9p", 48, 18, 8, 8, 6, 6);
        assertEntry("ui_corner_ornament", 16, 16, 0, 0, 0, 0);
        assertEntry("ui_scroll_arrow", 8, 8, 0, 0, 0, 0);
        assertEntry("ui_slot_hover_9p", 36, 36, 8, 8, 8, 8);
        assertEntry("ui_preview_board_9p", 80, 56, 8, 8, 8, 8);
        assertEntry("ui_btn_close_pressed", 14, 14, 0, 0, 0, 0);
        assertEntry("ui_icon_equip_weapon", 16, 16, 0, 0, 0, 0);
        assertEntry("ui_icon_equip_armor", 16, 16, 0, 0, 0, 0);
        assertEntry("ui_icon_equip_trinket", 16, 16, 0, 0, 0, 0);
    }

    @Test
    @DisplayName("切分线自洽：非负、左+右 ≤ 源宽、上+下 ≤ 源高（NinePatch 中心区不为负）；find 未知 id 返回 null")
    void splitsFitInsideSourceSize() {
        for (UiKeys.Entry entry : UiKeys.ALL) {
            assertThat(entry.left() + entry.right()).as("%s 左+右 ≤ 宽", entry.id()).isLessThanOrEqualTo(entry.width());
            assertThat(entry.top() + entry.bottom()).as("%s 上+下 ≤ 高", entry.id()).isLessThanOrEqualTo(entry.height());
            assertThat(entry.width()).as("%s 源宽 > 0", entry.id()).isPositive();
            assertThat(entry.height()).as("%s 源高 > 0", entry.id()).isPositive();
            assertThat(UiKeys.find(entry.id())).isSameAs(entry);
        }
        assertThat(UiKeys.find("ui_not_a_key")).isNull();
        assertThat(UiKeys.find("ui_panel_9slice")).as("旧通用件键不属于 UI 素材键集").isNull();
        assertThat(UiKeys.find("ui_btn_sell")).as("B02 已裁定不采购（Q3）——不入键集").isNull();
    }

    @Test
    @DisplayName("不拉伸件四边切分线恒 0：关闭钮三态 / 角花 / 滚动箭头 / 三件装备占位图标")
    void nonStretchKeysHaveZeroSplits() {
        List<UiKeys.Entry> fixed = java.util.Arrays.asList(
                UiKeys.BTN_CLOSE, UiKeys.BTN_CLOSE_PRESSED, UiKeys.BTN_CLOSE_HOVER, UiKeys.BTN_CLOSE_DISABLED,
                UiKeys.CORNER_ORNAMENT,
                UiKeys.SCROLL_ARROW, UiKeys.ICON_EQUIP_WEAPON, UiKeys.ICON_EQUIP_ARMOR, UiKeys.ICON_EQUIP_TRINKET);
        for (UiKeys.Entry entry : fixed) {
            assertThat(entry.left() + entry.right() + entry.top() + entry.bottom())
                    .as("%s 不拉伸", entry.id()).isZero();
        }
    }

    private static List<String> ids(List<UiKeys.Entry> entries) {
        List<String> ids = new java.util.ArrayList<String>();
        for (UiKeys.Entry entry : entries) {
            ids.add(entry.id());
        }
        return ids;
    }

    private static void assertEntry(String id, int w, int h, int left, int right, int top, int bottom) {
        UiKeys.Entry entry = UiKeys.find(id);
        assertThat(entry).as("键存在: %s", id).isNotNull();
        assertThat(entry.width()).as("%s 源宽", id).isEqualTo(w);
        assertThat(entry.height()).as("%s 源高", id).isEqualTo(h);
        assertThat(entry.left()).as("%s 左切分", id).isEqualTo(left);
        assertThat(entry.right()).as("%s 右切分", id).isEqualTo(right);
        assertThat(entry.top()).as("%s 上切分", id).isEqualTo(top);
        assertThat(entry.bottom()).as("%s 下切分", id).isEqualTo(bottom);
    }
}
