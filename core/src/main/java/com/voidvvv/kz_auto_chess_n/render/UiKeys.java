package com.voidvvv.kz_auto_chess_n.render;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * UI 框架素材键集（UI 抽屉化批0；准绳 = 采购单 {@code assets_src/ui_redesign/ASSET_REQUIREMENTS.md}
 * §2.1 必需 / §2.2 加分，1:1 列）。纯 Java 常量表——headless 可测、零 Gdx。
 *
 * <p>每条 = id（= 文件名主干，{@code art/ui/{id}.png}）+ 1:1 源尺寸 + 九宫格切分线（左/右/上/下；0 = 该边不固定）。
 * 切分线是 {@link Assets#uiPatch} 包 NinePatch 的唯一事实源；源尺寸既是 {@link PlaceholderArt}
 * 生成占位的画布尺寸，也是真图对账（{@code UiArtInventoryTest}）的预期值。
 *
 * <p>键集口径：必需 12（已交付）+ 加分 8（暂未交付——缺件走占位兜底，不影响功能）；
 * B02 {@code ui_btn_sell} 已裁定不采购（Q3：保留拖拽出售区），不入键集。旧通用件
 * {@link PlaceholderKeys#PANEL_9SLICE} 仍归既有面板，不在本表。
 *
 * <p>类加载即自检（fail-fast）：切分线非负且左+右 ≤ 源宽、上+下 ≤ 源高，id 互异——常量表写错在启动期即死，
 * 不留到绘制期才在 NinePatch 里出负宽。
 */
public final class UiKeys {

    /** 不可变条目：id / 源尺寸 / 九宫格切分线（左/右/上/下，像素） */
    public static final class Entry {
        private final String id;
        private final int width;
        private final int height;
        private final int left;
        private final int right;
        private final int top;
        private final int bottom;

        Entry(String id, int width, int height, int left, int right, int top, int bottom) {
            this.id = id;
            this.width = width;
            this.height = height;
            this.left = left;
            this.right = right;
            this.top = top;
            this.bottom = bottom;
        }

        public String id() {
            return id;
        }

        /** 文件名 = {id}.png（采购单交付口径） */
        public String fileName() {
            return id + ".png";
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        public int left() {
            return left;
        }

        public int right() {
            return right;
        }

        public int top() {
            return top;
        }

        public int bottom() {
            return bottom;
        }

        /** 是否九宫格件（任一切分线 &gt; 0） */
        public boolean isNinePatch() {
            return left + right + top + bottom > 0;
        }

        @Override
        public String toString() {
            return id + "(" + width + "x" + height + ", " + left + "/" + right + "/" + top + "/" + bottom + ")";
        }
    }

    // —— §2.1 必需（12；序号 = 采购单 M01~M12） ——

    /** M01 抽屉面板底（自带外边框） */
    public static final Entry PANEL_BODY = new Entry("ui_panel_body_9p", 64, 64, 12, 12, 12, 12);
    /** M02 内凹底板（描述框 / 装备大图预览的底） */
    public static final Entry INSET = new Entry("ui_inset_9p", 24, 24, 6, 6, 6, 6);
    /** M03 槽格（头像槽 / 装备槽共用，空槽态） */
    public static final Entry SLOT = new Entry("ui_slot_9p", 36, 36, 8, 8, 8, 8);
    /** M04 选中 / 待定高亮框（中心透明，叠在 SLOT 上） */
    public static final Entry SLOT_SELECTED = new Entry("ui_slot_selected_9p", 36, 36, 8, 8, 8, 8);
    /** M05 标签页·选中态 */
    public static final Entry TAB_ACTIVE = new Entry("ui_tab_active_9p", 32, 18, 6, 6, 6, 6);
    /** M06 标签页·未选中态 */
    public static final Entry TAB_INACTIVE = new Entry("ui_tab_inactive_9p", 32, 18, 6, 6, 6, 6);
    /** M07 滚动条轨（横竖共用） */
    public static final Entry SCROLL_TRACK = new Entry("ui_scroll_track_9p", 16, 16, 4, 4, 4, 4);
    /** M08 滚动条滑块（横竖共用） */
    public static final Entry SCROLL_KNOB = new Entry("ui_scroll_knob_9p", 12, 12, 4, 4, 4, 4);
    /** M09 左缘把手（收起态唯一可见件；纵向可拉伸 0/0/12/12） */
    public static final Entry HANDLE = new Entry("ui_handle", 14, 72, 0, 0, 12, 12);
    /** M10 关闭钮（不拉伸） */
    public static final Entry BTN_CLOSE = new Entry("ui_btn_close", 14, 14, 0, 0, 0, 0);
    /** M11 横向分隔条（左右各 4，上下不拉伸） */
    public static final Entry DIVIDER_H = new Entry("ui_divider_h", 24, 4, 4, 4, 0, 0);
    /** M12 标题板（棋子名字） */
    public static final Entry TITLE_PLATE = new Entry("ui_title_plate_9p", 48, 18, 8, 8, 6, 6);

    // —— §2.2 加分（8；B02 ui_btn_sell 不采购、不计入） ——

    /** B01 装饰角花 */
    public static final Entry CORNER_ORNAMENT = new Entry("ui_corner_ornament", 16, 16, 0, 0, 0, 0);
    /** B03 滚动条两端箭头（一张，靠翻转复用） */
    public static final Entry SCROLL_ARROW = new Entry("ui_scroll_arrow", 8, 8, 0, 0, 0, 0);
    /** B04 槽格悬停态边框 */
    public static final Entry SLOT_HOVER = new Entry("ui_slot_hover_9p", 36, 36, 8, 8, 8, 8);
    /** B05 装备大图预览底板 */
    public static final Entry PREVIEW_BOARD = new Entry("ui_preview_board_9p", 80, 56, 8, 8, 8, 8);
    /** B06 关闭钮按下态 */
    public static final Entry BTN_CLOSE_PRESSED = new Entry("ui_btn_close_pressed", 14, 14, 0, 0, 0, 0);
    /** B07 武器类占位图标 */
    public static final Entry ICON_EQUIP_WEAPON = new Entry("ui_icon_equip_weapon", 16, 16, 0, 0, 0, 0);
    /** B08 护甲类占位图标 */
    public static final Entry ICON_EQUIP_ARMOR = new Entry("ui_icon_equip_armor", 16, 16, 0, 0, 0, 0);
    /** B09 饰品类占位图标 */
    public static final Entry ICON_EQUIP_TRINKET = new Entry("ui_icon_equip_trinket", 16, 16, 0, 0, 0, 0);

    /** 必需 12 键（采购单 M01~M12 顺序；不可变） */
    public static final List<Entry> REQUIRED = Collections.unmodifiableList(Arrays.asList(
            PANEL_BODY, INSET, SLOT, SLOT_SELECTED, TAB_ACTIVE, TAB_INACTIVE,
            SCROLL_TRACK, SCROLL_KNOB, HANDLE, BTN_CLOSE, DIVIDER_H, TITLE_PLATE));

    /** 加分 8 键（采购单 §2.2 修订版；不可变） */
    public static final List<Entry> BONUS = Collections.unmodifiableList(Arrays.asList(
            CORNER_ORNAMENT, SCROLL_ARROW, SLOT_HOVER, PREVIEW_BOARD, BTN_CLOSE_PRESSED,
            ICON_EQUIP_WEAPON, ICON_EQUIP_ARMOR, ICON_EQUIP_TRINKET));

    /** 全键集 = 必需 + 加分（20；不可变） */
    public static final List<Entry> ALL;

    /** 全部 id（与 {@link #ALL} 同序；不可变——供 {@link PlaceholderKeys#enumerateFor} 逐 key 对账） */
    public static final List<String> IDS;

    private static final Map<String, Entry> BY_ID;

    static {
        List<Entry> all = new ArrayList<Entry>(REQUIRED);
        all.addAll(BONUS);
        ALL = Collections.unmodifiableList(all);
        List<String> ids = new ArrayList<String>(all.size());
        Map<String, Entry> byId = new HashMap<String, Entry>();
        for (Entry entry : all) {
            verify(entry);
            if (byId.put(entry.id(), entry) != null) {
                throw new IllegalStateException("UiKeys id 重复: " + entry.id());
            }
            ids.add(entry.id());
        }
        IDS = Collections.unmodifiableList(ids);
        BY_ID = Collections.unmodifiableMap(byId);
    }

    private UiKeys() {
    }

    /** @return 全部 id（不可变、与 {@link #ALL} 同序） */
    public static List<String> allIds() {
        return IDS;
    }

    /** @return id 对应条目；未知 id（含旧通用件 ui_panel_9slice）返回 null */
    public static Entry find(String id) {
        return id == null ? null : BY_ID.get(id);
    }

    /** 常量表自检（类加载即跑）：尺寸为正、切分线非负、左+右 ≤ 宽、上+下 ≤ 高 */
    private static void verify(Entry entry) {
        if (entry.width() <= 0 || entry.height() <= 0) {
            throw new IllegalStateException("UiKeys 源尺寸非正: " + entry);
        }
        if (entry.left() < 0 || entry.right() < 0 || entry.top() < 0 || entry.bottom() < 0) {
            throw new IllegalStateException("UiKeys 切分线为负: " + entry);
        }
        if (entry.left() + entry.right() > entry.width() || entry.top() + entry.bottom() > entry.height()) {
            throw new IllegalStateException("UiKeys 切分线越界（左+右 ≤ 宽、上+下 ≤ 高）: " + entry);
        }
    }
}
