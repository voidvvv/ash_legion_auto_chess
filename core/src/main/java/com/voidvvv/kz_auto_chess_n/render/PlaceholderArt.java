package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import com.voidvvv.kz_auto_chess_n.data.UnitData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 运行时占位图集（render §7.5；GL 线程一次性生成，不 headless 测——验收走 lwjgl3:run）。
 *
 * <p>零素材文件全功能：Pixmap 逐 key 绘制 → Texture（全部 Nearest，render §八.1）→ TextureRegion。
 * 单位块 = 种族底色（PalettePick）+ 职业顶条 + 帧差异（idle 亮度 / walk y±1px / attack 前移2px /
 * cast 亮度脉冲 / death 透明度递减）；生成范围与 {@link PlaceholderKeys#enumerateFor} 对账（防漏）。
 */
public final class PlaceholderArt {
    private static final int UNIT_SIZE = 32;
    private static final int SKILL_FX_SIZE = 16;
    private static final int SKILL_BURST_SIZE = 24;
    private static final int STATUS_FX_SIZE = 8;
    private static final int DIGIT_SCALE = 2;

    private final Map<String, TextureRegion> regions = new HashMap<String, TextureRegion>();
    private final List<Texture> textures = new ArrayList<Texture>();

    public PlaceholderArt(GameData data) {
        for (UnitData tpl : data.getUnits().values()) {
            for (String anim : PlaceholderKeys.ANIMS) {
                for (int frame = 0; frame < PlaceholderKeys.frameCount(anim); frame++) {
                    put(PlaceholderKeys.unitFrame(tpl.getId(), anim, frame), paintUnit(tpl, anim, frame));
                }
            }
        }
        for (String skillId : data.getSkills().keySet()) {
            put(PlaceholderKeys.skillFx(skillId), paintSkillFx(skillId));
            put(PlaceholderKeys.skillFxBurst(skillId), paintSkillBurst(skillId));
        }
        for (StatusType type : StatusType.values()) {
            put(PlaceholderKeys.statusFx(type), paintStatusFx(type));
        }
        for (int digit = 0; digit <= 9; digit++) {
            put(PlaceholderKeys.digitFx(digit), paintDigit(digit));
        }
        put(PlaceholderKeys.CAST_DEFAULT, paintCastDefault());
        put(PlaceholderKeys.HIT_DEFAULT, paintHitDefault());
        put(PlaceholderKeys.PANEL_9SLICE, paintPanel());
        put(PlaceholderKeys.WHITE, paintWhite());
        verifyCoverage(data);
    }

    /** @return 占位 region；不存在返回 null（Assets 层负责断言） */
    public TextureRegion region(String key) {
        return regions.get(key);
    }

    /** 全部 Texture 生命周期释放（Main.dispose 经 Assets 调） */
    public void dispose() {
        for (Texture texture : textures) {
            texture.dispose();
        }
        textures.clear();
        regions.clear();
    }

    // —— 生成对账：enumerateFor 的每个 key 必须已生成（fail-fast） ——

    private void verifyCoverage(GameData data) {
        for (String key : PlaceholderKeys.enumerateFor(data)) {
            if (!regions.containsKey(key)) {
                throw new IllegalStateException("占位图集漏生成 key: " + key);
            }
        }
    }

    private void put(String key, Pixmap pixmap) {
        Texture texture = new Texture(pixmap);
        texture.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        textures.add(texture);
        regions.put(key, new TextureRegion(texture));
        pixmap.dispose();
    }

    // —— 单位块：种族底色 + 职业顶条 + 帧差异 ——

    private Pixmap paintUnit(UnitData tpl, String anim, int frame) {
        com.badlogic.gdx.graphics.Color race = PalettePick.pick(tpl.getRace());
        com.badlogic.gdx.graphics.Color clazz = PalettePick.pick(tpl.getUnitClass());
        float alpha = PlaceholderKeys.ANIM_DEATH.equals(anim) ? deathAlpha(frame) : 1f;
        int dx = PlaceholderKeys.ANIM_ATTACK.equals(anim) ? 2 : 0;  // 出手前移（朝向翻转归 UnitView）
        int dy = PlaceholderKeys.ANIM_WALK.equals(anim) ? (frame == 0 ? -1 : 1) : 0;
        float bright = frameBrightness(anim, frame);

        Pixmap pm = new Pixmap(UNIT_SIZE, UNIT_SIZE, Pixmap.Format.RGBA8888);
        // 身体（种族底色）+ 描边 + 职业顶条
        fillRect(pm, 4 + dx, 6 + dy, 24, 22, race.r * bright, race.g * bright, race.b * bright, alpha);
        fillRect(pm, 4 + dx, 6 + dy, 24, 5, clazz.r * bright, clazz.g * bright, clazz.b * bright, alpha);
        pm.setColor(0f, 0f, 0f, alpha * 0.5f);
        pm.drawRectangle(4 + dx, 6 + dy, 24, 22);
        return pm;
    }

    private static float deathAlpha(int frame) {
        if (frame <= 0) {
            return 1f;
        }
        return frame == 1 ? 0.6f : 0.25f;
    }

    private static float frameBrightness(String anim, int frame) {
        if (frame == 0) {
            return 1f;
        }
        if (PlaceholderKeys.ANIM_IDLE.equals(anim)) {
            return 1.05f; // 亮度 ±5%
        }
        if (PlaceholderKeys.ANIM_CAST.equals(anim)) {
            return 1.08f; // 施法亮度脉冲
        }
        return 1f;
    }

    // —— 技能 / 状态 / 通用件 ——

    private Pixmap paintSkillFx(String skillId) {
        com.badlogic.gdx.graphics.Color color = PalettePick.pick(skillId);
        Pixmap pm = new Pixmap(SKILL_FX_SIZE, SKILL_FX_SIZE, Pixmap.Format.RGBA8888);
        int c = SKILL_FX_SIZE / 2;
        fillRect(pm, c - 3, c - 3, 6, 6, color.r, color.g, color.b, 1f); // 中心核
        fillRect(pm, c - 1, 0, 2, SKILL_FX_SIZE, color.r, color.g, color.b, 0.8f); // 四向芒
        fillRect(pm, 0, c - 1, SKILL_FX_SIZE, 2, color.r, color.g, color.b, 0.8f);
        return pm;
    }

    private Pixmap paintSkillBurst(String skillId) {
        com.badlogic.gdx.graphics.Color color = PalettePick.pick(skillId);
        Pixmap pm = new Pixmap(SKILL_BURST_SIZE, SKILL_BURST_SIZE, Pixmap.Format.RGBA8888);
        pm.setColor(color.r, color.g, color.b, 0.9f);
        pm.drawRectangle(2, 2, SKILL_BURST_SIZE - 4, SKILL_BURST_SIZE - 4); // 落点环
        fillRect(pm, SKILL_BURST_SIZE / 2 - 2, SKILL_BURST_SIZE / 2 - 2, 4, 4,
                color.r, color.g, color.b, 1f);
        return pm;
    }

    private Pixmap paintStatusFx(StatusType type) {
        com.badlogic.gdx.graphics.Color color = PalettePick.pick(type.name());
        Pixmap pm = new Pixmap(STATUS_FX_SIZE, STATUS_FX_SIZE, Pixmap.Format.RGBA8888);
        fillRect(pm, 0, 0, STATUS_FX_SIZE, STATUS_FX_SIZE, color.r, color.g, color.b, 0.9f);
        pm.setColor(0f, 0f, 0f, 0.6f);
        pm.drawRectangle(0, 0, STATUS_FX_SIZE, STATUS_FX_SIZE);
        return pm;
    }

    private Pixmap paintDigit(int digit) {
        Pixmap pm = new Pixmap(DigitGlyph.COLS * DIGIT_SCALE, DigitGlyph.ROWS * DIGIT_SCALE,
                Pixmap.Format.RGBA8888);
        for (int row = 0; row < DigitGlyph.ROWS; row++) {
            for (int col = 0; col < DigitGlyph.COLS; col++) {
                if (DigitGlyph.pixel(digit, row, col)) {
                    fillRect(pm, col * DIGIT_SCALE, row * DIGIT_SCALE, DIGIT_SCALE, DIGIT_SCALE,
                            1f, 1f, 1f, 1f);
                }
            }
        }
        return pm;
    }

    private Pixmap paintCastDefault() {
        Pixmap pm = new Pixmap(SKILL_FX_SIZE, SKILL_FX_SIZE, Pixmap.Format.RGBA8888);
        fillRect(pm, 7, 2, 2, 12, 0.9f, 0.9f, 1f, 0.9f);
        fillRect(pm, 2, 7, 12, 2, 0.9f, 0.9f, 1f, 0.9f);
        fillRect(pm, 5, 5, 6, 6, 1f, 1f, 1f, 1f);
        return pm;
    }

    private Pixmap paintHitDefault() {
        Pixmap pm = new Pixmap(12, 12, Pixmap.Format.RGBA8888);
        fillRect(pm, 5, 0, 2, 12, 1f, 0.8f, 0.4f, 0.9f);
        fillRect(pm, 0, 5, 12, 2, 1f, 0.8f, 0.4f, 0.9f);
        fillRect(pm, 4, 4, 4, 4, 1f, 1f, 1f, 1f);
        return pm;
    }

    private Pixmap paintPanel() {
        Pixmap pm = new Pixmap(24, 24, Pixmap.Format.RGBA8888);
        fillRect(pm, 0, 0, 24, 24, 0.22f, 0.2f, 0.28f, 0.9f);
        pm.setColor(0.75f, 0.72f, 0.65f, 1f);
        pm.drawRectangle(0, 0, 24, 24);
        pm.drawRectangle(1, 1, 22, 22);
        return pm;
    }

    private Pixmap paintWhite() {
        Pixmap pm = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pm.setColor(1f, 1f, 1f, 1f);
        pm.fill();
        return pm;
    }

    private static void fillRect(Pixmap pm, int x, int y, int w, int h,
                                 float r, float g, float b, float a) {
        pm.setColor(Math.min(1f, r), Math.min(1f, g), Math.min(1f, b), Math.max(0f, Math.min(1f, a)));
        pm.fillRectangle(x, y, w, h);
    }
}
