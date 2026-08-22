package com.voidvvv.kz_auto_chess_n.render.ui;

import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.EquipmentEffect;
import com.voidvvv.kz_auto_chess_n.data.EquipmentPassive;
import com.voidvvv.kz_auto_chess_n.data.EquipmentRarity;
import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.data.EffectOp;
import com.voidvvv.kz_auto_chess_n.data.StatKey;
import com.voidvvv.kz_auto_chess_n.data.StatusType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 装备文案格式化纯函数测试（feedback07）：详情弹窗装备行 / 背包格悬停卡 / 宝箱选项行
 * 三展示点共用同一文案源（用户裁决：禁止三处各写一份格式化）。零 Gdx 依赖，headless 直测。
 * 夹具沿 InventoryPanelTest.sword / ChestDialogTest.eq 手搓 EquipmentData 先例 +
 * 龙心镜像（equipments.json：HP ADD 400 + REGEN 0.02/5）。
 */
class EquipmentInfoTextTest {

    /** 龙心镜像（equipments.json eq_dragon_heart：传说盔甲，HP+400 + 每 5 秒回 2% 最大生命） */
    private static EquipmentData dragonHeart() {
        return new EquipmentData("eq_dragon_heart", "龙心", EquipmentSlot.ARMOR, EquipmentRarity.LEGENDARY,
                Arrays.asList(new EquipmentEffect(StatKey.HP, EffectOp.ADD, 400f)),
                new EquipmentPassive(StatusType.REGEN, 0.02f, 5f));
    }

    /** 铁剑镜像（equipments.json eq_iron_sword：白武，攻击 PCT 20） */
    private static EquipmentData ironSword() {
        return new EquipmentData("eq_iron_sword", "铁剑", EquipmentSlot.WEAPON, EquipmentRarity.WHITE,
                Arrays.asList(new EquipmentEffect(StatKey.ATTACK, EffectOp.PCT, 20f)), null);
    }

    // —— 单条效果文案（与 UnitInfoText.effectText 同词汇） ——

    @Test
    @DisplayName("效果文案：PCT / ADD / 百分比刻度 ADD 附 % —— 与 UnitInfoText 同词汇")
    void effectTextCoversChannels() {
        assertThat(EquipmentInfoText.effectText(new EquipmentEffect(StatKey.ATTACK, EffectOp.PCT, 35f)))
                .isEqualTo("攻击+35%");
        assertThat(EquipmentInfoText.effectText(new EquipmentEffect(StatKey.HP, EffectOp.ADD, 400f)))
                .isEqualTo("生命+400");
        assertThat(EquipmentInfoText.effectText(new EquipmentEffect(StatKey.ARMOR, EffectOp.ADD, 20f)))
                .isEqualTo("护甲+20");
        assertThat(EquipmentInfoText.effectText(new EquipmentEffect(StatKey.LIFESTEAL, EffectOp.ADD, 10f)))
                .isEqualTo("吸血+10%"); // 百分比刻度键：ADD 也附 %
        assertThat(EquipmentInfoText.effectText(new EquipmentEffect(StatKey.ENERGY_GAIN_RATE, EffectOp.PCT, 15f)))
                .isEqualTo("回能+15%");
    }

    // —— 被动文案（GDD §5.2 龙心行原文 + 「被动：」前缀） ——

    @Test
    @DisplayName("被动文案：REGEN 0.02/5 → 每 5 秒 回复 2% 最大生命；非 REGEN 回退词表名（防御）")
    void passiveTextRegenAndFallback() {
        assertThat(EquipmentInfoText.passiveText(new EquipmentPassive(StatusType.REGEN, 0.02f, 5f)))
                .isEqualTo("被动：每 5 秒 回复 2% 最大生命");
        assertThat(EquipmentInfoText.passiveText(new EquipmentPassive(StatusType.STUN, 1f, 1f)))
                .isEqualTo("被动：STUN"); // 加载期限 REGEN，纯防御不炸
    }

    // —— 稀有度 / 槽位词表（GDD §5.2 白/成/传、武器/盔甲/饰品） ——

    @Test
    @DisplayName("稀有度词：白装/成装/传说；槽位词：武器/盔甲/饰品")
    void rarityAndSlotLabels() {
        assertThat(EquipmentInfoText.rarityLabel(EquipmentRarity.WHITE)).isEqualTo("白装");
        assertThat(EquipmentInfoText.rarityLabel(EquipmentRarity.RARE)).isEqualTo("成装");
        assertThat(EquipmentInfoText.rarityLabel(EquipmentRarity.LEGENDARY)).isEqualTo("传说");
        assertThat(EquipmentInfoText.slotLabel(EquipmentSlot.WEAPON)).isEqualTo("武器");
        assertThat(EquipmentInfoText.slotLabel(EquipmentSlot.ARMOR)).isEqualTo("盔甲");
        assertThat(EquipmentInfoText.slotLabel(EquipmentSlot.TRINKET)).isEqualTo("饰品");
    }

    // —— 三种出口形态（条目行集 / 摘要单串 / 悬停卡行集） ——

    @Test
    @DisplayName("效果条目行集：龙心 = 属性行 + 被动行；无效果无被动 = 空列表")
    void effectEntriesDragonHeartAndEmpty() {
        assertThat(EquipmentInfoText.effectEntries(dragonHeart()))
                .containsExactly("生命+400", "被动：每 5 秒 回复 2% 最大生命");
        assertThat(EquipmentInfoText.effectEntries(new EquipmentData("eq_x", "空", EquipmentSlot.WEAPON,
                EquipmentRarity.WHITE, Collections.<EquipmentEffect>emptyList(), null)))
                .isEmpty();
    }

    @Test
    @DisplayName("效果摘要单串：条目 \" · \" 连接；空 = 空串")
    void effectSummaryJoinAndEmpty() {
        assertThat(EquipmentInfoText.effectSummary(dragonHeart()))
                .isEqualTo("生命+400 · 被动：每 5 秒 回复 2% 最大生命");
        assertThat(EquipmentInfoText.effectSummary(new EquipmentData("eq_x", "空", EquipmentSlot.WEAPON,
                EquipmentRarity.WHITE, Collections.<EquipmentEffect>emptyList(), null)))
                .isEmpty();
    }

    @Test
    @DisplayName("悬停卡行集：名 → 稀有度·槽位 → 效果条目（铁剑三行原样）")
    void linesIronSword() {
        List<String> lines = EquipmentInfoText.lines(ironSword());
        assertThat(lines).containsExactly("铁剑", "白装·武器", "攻击+20%");
    }
}
