package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.voidvvv.kz_auto_chess_n.render.Assets;

/**
 * 抽屉装备区（UI 抽屉化批3 批前重构：自 DrawerPanel 抽出，纯搬迁、行为不变）：
 * 装备网格 3 列 × 2 行可见（Q1）+ 装备大图预览底。批前重构阶段仍是空槽 + 占位画法
 * （图标 ×3 居上、装备描述占位行居下）；背包库存绑定 / 两段式穿戴 / 预览滚动随批3 功能提交接入。
 */
final class DrawerEquipSection extends Group {

    /** 装备大图预览图标位（批3 前的占位画法）：占位图标放大 3 倍（16 → 48px），居上；描述占位行居下 */
    private static final float EQUIP_ICON_SCALE = 3f;
    private static final float EQUIP_DESC_BASELINE = 6f;
    private static final String EQUIP_DESC_PLACEHOLDER = "（装备描述）";

    private final Assets assets;

    DrawerEquipSection(Assets assets, NinePatch slotPatch, NinePatch insetPatch, TextureRegion iconRegion) {
        this.assets = assets;
        setTouchable(Touchable.childrenOnly);
        setSize(DrawerGeometry.DRAWER_W, DrawerGeometry.DRAWER_H);
        for (int index = 0; index < DrawerGeometry.EQUIP_GRID_COLS * DrawerGeometry.EQUIP_GRID_ROWS; index++) {
            addActor(new DrawerSkin.PatchActor(slotPatch, DrawerGeometry.equipSlot(index, 0)));
        }
        addActor(new EquipPreview(insetPatch, iconRegion));
    }

    /** 装备大图预览底 + 图标位（占位图标 ×3，居上）+ 装备描述占位行（居下） */
    private final class EquipPreview extends DrawerSkin.PatchActor {
        private final TextureRegion iconRegion;

        EquipPreview(NinePatch patch, TextureRegion iconRegion) {
            super(patch, DrawerGeometry.equipPreview());
            this.iconRegion = iconRegion;
        }

        @Override
        public void draw(Batch batch, float parentAlpha) {
            super.draw(batch, parentAlpha);
            float alpha = DrawerSkin.alphaOf(this, parentAlpha);
            float icon = iconRegion.getRegionWidth() * EQUIP_ICON_SCALE;
            DrawerSkin.drawRegion(batch, iconRegion, rect.x() + (rect.width() - icon) / 2f,
                    rect.y() + rect.height() - icon - 2f, icon, icon, alpha);
            BitmapFont font = assets.font();
            font.getData().setScale(DrawerSkin.SMALL_TEXT_SCALE); // 用后即还（共用字体纪律）
            float textWidth = UnitInfoText.columns(EQUIP_DESC_PLACEHOLDER) * DrawerSkin.SMALL_LINE_HEIGHT;
            DrawerSkin.drawText(batch, font, EQUIP_DESC_PLACEHOLDER, rect.x() + (rect.width() - textWidth) / 2f,
                    rect.y() + EQUIP_DESC_BASELINE + DrawerSkin.SMALL_LINE_HEIGHT, alpha); // 行顶 = 底留白 + 行高
            font.getData().setScale(1f);
        }
    }
}
