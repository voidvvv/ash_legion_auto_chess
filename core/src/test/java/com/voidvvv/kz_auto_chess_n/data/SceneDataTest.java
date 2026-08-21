package com.voidvvv.kz_auto_chess_n.data;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * SceneData POJO 行为用例：Boss 轮查询与容器不可变（沿 SynergyData.Threshold 测试风格）。
 */
class SceneDataTest {

    private static SceneData newFixture() {
        List<SceneData.EnemyPoolEntry> pool = new ArrayList<SceneData.EnemyPoolEntry>();
        pool.add(new SceneData.EnemyPoolEntry("u1", 3, 1));
        pool.add(new SceneData.EnemyPoolEntry("u2", 2, 5));
        Map<Integer, String> bosses = new LinkedHashMap<Integer, String>();
        bosses.put(7, "b1");
        bosses.put(15, "b2");
        bosses.put(25, "b3");
        return new SceneData("sc1", "翡翠林地", null, pool, bosses);
    }

    @Test
    @DisplayName("getBossUnitId：7/15/25 命中，其余轮 null；池条目按构造序保序")
    void bossLookupAndPoolOrder() {
        SceneData scene = newFixture();
        assertThat(scene.getId()).isEqualTo("sc1");
        assertThat(scene.getName()).isEqualTo("翡翠林地");
        assertThat(scene.getUnlockAfter()).isNull();
        assertThat(scene.getBossUnitId(7)).isEqualTo("b1");
        assertThat(scene.getBossUnitId(15)).isEqualTo("b2");
        assertThat(scene.getBossUnitId(25)).isEqualTo("b3");
        assertThat(scene.getBossUnitId(2)).isNull();
        assertThat(scene.getBossUnitId(24)).isNull();
        assertThat(scene.getEnemyPool())
                .extracting(SceneData.EnemyPoolEntry::getUnitId)
                .containsExactly("u1", "u2");
        assertThat(scene.getEnemyPool().get(1).getWeight()).isEqualTo(2);
        assertThat(scene.getEnemyPool().get(1).getMinRound()).isEqualTo(5);
    }

    @Test
    @DisplayName("容器不可变：外部不可写入池列表与 Boss 映射")
    void containersUnmodifiable() {
        SceneData scene = newFixture();
        assertThatThrownBy(() -> scene.getEnemyPool().add(new SceneData.EnemyPoolEntry("u3", 1, 1)))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> scene.getEnemyPool().remove(0))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> scene.getBosses().put(1, "bx"))
                .isInstanceOf(UnsupportedOperationException.class);
        assertThatThrownBy(() -> scene.getBosses().remove(7))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
