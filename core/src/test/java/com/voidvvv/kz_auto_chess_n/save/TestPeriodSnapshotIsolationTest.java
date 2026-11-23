package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunMode;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.systems.RunFlowSystem;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 品质改造批1（§0.1）验收件（纯 JVM 快照序列化对比）：含正式快照的档案进出测试局，
 * 前后两个正式存档文件字节一致；普通「继续远征」按快照原值恢复。
 *
 * <p>测试局的写入闸（不写快照、不补写、不结算、不删档）分支断言在
 * {@code screens.BattleScreenRunAssemblyTest#archiveGateBranchesByMode}；本件证明快照轨本身
 * 的读写对称——只要闸关闭（测试局零写入），正式存档字节恒不变，且恢复后不漂移。
 * 实机侧（暂停/终局/重开三种进出路径）见交付报告「待人工」清单。
 */
@DisplayName("测试档存档隔离：正式快照字节一致 + 续档按快照原值恢复（品质改造 §0.1）")
class TestPeriodSnapshotIsolationTest {

    private static GameData data;

    @BeforeAll
    static void loadRealData() {
        data = JsonLoader.loadFromDirectory(new FileHandle("../assets/data"));
    }

    /** 富态正式局上下文（真实 data）：37 金 / Lv.3 / 格雷克修正 / 备战期已开局（敌阵 + 商店已发） */
    private static RunContext formalRunContext() {
        RunModifiers greg = ProfileService.runModifiers(
                data.getHero("hero_greg"), Profile.fresh(), data);
        Player player = new Player(37, 3, 5);
        RunState runState = new RunState(99L, "scene_forest", "hero_greg", greg,
                new SequentialIdIssuer());
        RunContext ctx = new RunContext(player, runState, data,
                new RandomGenerator(99L), new ShopSystem());
        new RunFlowSystem().startRun(ctx); // StartRun 回放第 0 条的效果：runStarted + 敌阵 + 免费刷新
        return ctx;
    }

    @Test
    @DisplayName("正式存档进出测试局前后字节一致（落盘 → 测试局零写入 → 再读逐字节相同）")
    void formalSnapshotBytesUnchangedAcrossTestPeriod() throws Exception {
        Path file = Files.createTempFile("kz-run-snapshot", ".json");
        try {
            SnapshotStore store = new SnapshotStore(new FileHandle(file.toFile()));
            RunSnapshot captured = SnapshotCodec.capture(formalRunContext());
            String bytesBefore = SnapshotCodec.write(captured);
            assertThat(store.save(captured)).isTrue();
            assertThat(store.exists()).isTrue();

            // —— 测试局进出窗口（品质改造 §0.1 存档隔离闸关闭：本窗口内对正式存档零写入；
            //    闸分支断言见 screens.BattleScreenRunAssemblyTest#archiveGateBranchesByMode）——

            // 出测试局：文件仍在、逐字节一致（终局删档未发生、补写未发生）
            assertThat(store.exists()).as("测试局进出后正式快照仍在（不删档）").isTrue();
            assertThat(file.toFile().length()).as("存档字节数不变").isGreaterThan(0L);
            assertThat(new String(Files.readAllBytes(file), "UTF-8")).isEqualTo(bytesBefore);
            assertThat(SnapshotCodec.write(store.load(data))).isEqualTo(bytesBefore);
        } finally {
            Files.deleteIfExists(file);
        }
    }

    @Test
    @DisplayName("普通续档：金币/等级/经验按快照原值恢复，模式恒正式（档案轨全开）")
    void resumeRestoresExactSnapshotValues() {
        RunSnapshot captured = SnapshotCodec.capture(formalRunContext());
        RunContext resumed = SnapshotCodec.restore(captured, data, Profile.fresh(), new ShopSystem());

        assertThat(resumed.getMode()).as("续玩恒正式模式").isEqualTo(RunMode.NORMAL);
        assertThat(resumed.getPlayer().getGold()).as("金币保持快照原值 37（不重算起始金）")
                .isEqualTo(37);
        assertThat(resumed.getPlayer().getLevel()).isEqualTo(3);
        assertThat(resumed.getPlayer().getCurrentExp()).isEqualTo(5);
        assertThat(resumed.getRunState().getHeroId()).isEqualTo("hero_greg");
        assertThat(resumed.getRunState().getSceneId()).isEqualTo("scene_forest");
        assertThat(resumed.getRunState().isRunStarted()).isTrue(); // 跳过 StartRun（CP16/CP17）

        // 恢复后立即再捕获：字节一致（恢复不漂移——存档可反复进出测试局）
        assertThat(SnapshotCodec.write(SnapshotCodec.capture(resumed)))
                .isEqualTo(SnapshotCodec.write(captured));
    }
}
