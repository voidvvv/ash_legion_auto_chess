package com.voidvvv.kz_auto_chess_n.save;

import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.SceneData;

import java.util.ArrayList;
import java.util.List;

/** 局末结算文案纯函数（RunEndPanel 逐行绘制；中文文案、英文标识符——沿 UnitInfoText 先例）。 */
public final class RunSettlementText {

    private RunSettlementText() {
    }

    public static List<String> lines(ProfileService.Settlement settlement, GameData data) {
        List<String> lines = new ArrayList<String>(4);
        lines.add("熟练度 +" + settlement.getExpGained());
        if (settlement.getLevelTo() > settlement.getLevelFrom()) {
            lines.add("英雄等级 Lv." + settlement.getLevelFrom() + " → Lv." + settlement.getLevelTo());
        }
        if (settlement.getExpToNextLevel() > 0) {
            lines.add("当前 Lv." + settlement.getLevelTo() + "（经验 "
                    + settlement.getExpIntoLevel() + "/" + settlement.getExpToNextLevel() + "）");
        } else {
            lines.add("英雄等级已满（Lv." + settlement.getLevelTo() + "）");
        }
        for (String sceneId : settlement.getNewlyUnlockedSceneIds()) {
            SceneData scene = data.getScene(sceneId);
            lines.add("解锁场景：" + (scene == null ? sceneId : scene.getName()));
        }
        return lines;
    }
}
