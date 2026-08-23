package com.voidvvv.kz_auto_chess_n.data;

/**
 * 英雄被动词表（heroes.json "passive.type"；GDD §8.1 首发三英雄草案）。
 * 词表即代码铁律（data_schema §三）：新增被动类型 = 引擎改动，先在此登记再进 JSON（裁决 D17）。
 */
public enum HeroPassiveType implements Vocab {
    /** 开局金币加成（value = 金币数；「老兵补给」格雷克） */
    START_GOLD("START_GOLD"),
    /** 指定羁绊效果增幅（value = 百分比 25 → ×1.25；synergyIds 指定羁绊；「荆语」薇拉） */
    SYNERGY_AMP("SYNERGY_AMP"),
    /** 全队回能加成（value = 百分点 15 → ×1.15；「战歌」奥兰多） */
    ENERGY_GAIN("ENERGY_GAIN");

    private final String jsonName;

    HeroPassiveType(String jsonName) {
        this.jsonName = jsonName;
    }

    @Override
    public String jsonName() {
        return jsonName;
    }
}
