package com.voidvvv.kz_auto_chess_n.systems.support;

import com.badlogic.gdx.files.FileHandle;
import com.voidvvv.kz_auto_chess_n.command.BuyExpCommand;
import com.voidvvv.kz_auto_chess_n.command.BuyUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.CommandManager;
import com.voidvvv.kz_auto_chess_n.command.MoveUnitCommand;
import com.voidvvv.kz_auto_chess_n.command.PickChestCommand;
import com.voidvvv.kz_auto_chess_n.command.PlacementTarget;
import com.voidvvv.kz_auto_chess_n.command.RefreshShopCommand;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.command.StartBattleCommand;
import com.voidvvv.kz_auto_chess_n.command.StartRunCommand;
import com.voidvvv.kz_auto_chess_n.config.JsonLoader;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.entities.BattleOutcome;
import com.voidvvv.kz_auto_chess_n.entities.BattleState;
import com.voidvvv.kz_auto_chess_n.entities.ChestOption;
import com.voidvvv.kz_auto_chess_n.entities.ChestOffer;
import com.voidvvv.kz_auto_chess_n.screens.RunAssembler;
import com.voidvvv.kz_auto_chess_n.systems.BattleSystem;
import com.voidvvv.kz_auto_chess_n.systems.PaiTestScript;
import com.voidvvv.kz_auto_chess_n.systems.RosterSystem;
import com.voidvvv.kz_auto_chess_n.systems.RunFlowSystem;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * 对拍无头驱动器（品质改造批2 验收件，纯 JVM）：按命令序推进真实 RunFlowSystem / ShopSystem /
 * RosterSystem / BattleSystem 链路。题表 / 商店 / 种子公式全部取自 {@link PaiTestScript}
 * （§0.2 单一来源——测试不得复制敌阵、商店与种子公式）；商店槽位由补货分派单点写入。
 *
 * <p>两种开战路径：{@link Session#battleToEnd()}（真实交战，确定性事件流）与
 * {@link Session#injectWin()}（注入胜局，§0.3 账本独立验证——不依赖交战强度）。
 */
public final class PaiDuelDriver {

    /** runToEnd 上限：60s 超时 = 3600 tick，留余量（沿 BattleConsoleMain 口径） */
    private static final int MAX_TICKS = 4000;

    private PaiDuelDriver() {
    }

    /** 真实 data/ 目录（沿 BattleConsoleMain 的 FileHandle 纯 JVM 构造；单例共享保证模板引用同源） */
    public static GameData realData() {
        return JsonLoader.loadFromDirectory(new FileHandle(new File("../assets/data")));
    }

    /** §1.1 探测表站位（依次为 W / M / S）：题 A 聚拢→分散、题 B 祭司在后→战士在后 */
    public enum Layout {
        /** A 聚拢：W(2,4) / M(1,4) / S(2,5) */
        A_CLUSTER(PaiTestScript.Topic.A, new int[][]{{2, 4}, {1, 4}, {2, 5}}),
        /** A 分散：W(2,4) / M(0,4) / S(0,6) */
        A_SCATTER(PaiTestScript.Topic.A, new int[][]{{2, 4}, {0, 4}, {0, 6}}),
        /** B 祭司在后：W(2,5) / M(2,6) / S(2,4) */
        B_PRIEST_BACK(PaiTestScript.Topic.B, new int[][]{{2, 5}, {2, 6}, {2, 4}}),
        /** B 战士在后：W(2,6) / M(2,5) / S(2,4) */
        B_WARRIOR_BACK(PaiTestScript.Topic.B, new int[][]{{2, 6}, {2, 5}, {2, 4}});

        public final PaiTestScript.Topic topic;
        public final int[][] cells;

        Layout(PaiTestScript.Topic topic, int[][] cells) {
            this.topic = topic;
            this.cells = cells;
        }
    }

    /** 按布局开一局（§1.1 对拍购买固定：首屏 W/M/S、余 7 金，按 W、M、S 顺序买入并部署） */
    public static Session openLayout(GameData data, Layout layout, long seed) {
        Session session = Session.start(data, layout.topic, seed);
        session.buyTemplate(PaiTestScript.W);
        session.buyTemplate(PaiTestScript.M);
        session.buyTemplate(PaiTestScript.S);
        for (int[] cell : layout.cells) {
            session.deployNextBench(cell[0], cell[1]);
        }
        return session;
    }

    /** 一次无头对拍会话（同题同种子可重复构建；重开 = 重新 start，商店随上下文重建） */
    public static final class Session {
        private final RunFlowSystem flow = new RunFlowSystem();
        private final BattleSystem battleSystem = new BattleSystem();
        private final ShopSystem shop = new ShopSystem();
        private final RosterSystem roster = new RosterSystem();
        private final CommandManager commands = new CommandManager();
        private final RunContext ctx;
        private BattleState lastBattle;

        private Session(GameData data, PaiTestScript.Topic topic, long seed) {
            this.ctx = RunAssembler.assembleSampleDuel(data, shop, seed, topic);
            flow.registerHandlers(commands);
            shop.registerHandlers(commands);
            roster.registerHandlers(commands);
            commands.addCommand(new StartRunCommand(seed, "scene_forest", null));
            commands.executeAll(ctx); // 回放第 0 条：StartRun → 固定敌阵 + 免费五槽（0 RNG）
        }

        public static Session start(GameData data, PaiTestScript.Topic topic, long seed) {
            return new Session(data, topic, seed);
        }

        public RunContext ctx() {
            return ctx;
        }

        // —— 命令面（与屏幕输入同链路：命令入队 → executeAll） ——

        public void buy(int slotIndex) {
            commands.addCommand(new BuyUnitCommand(slotIndex));
            commands.executeAll(ctx);
        }

        /** 当前商店中指定模板的槽位序（找不到返回 -1）——对拍购买按模板寻位（§1.1 按 W、M、S 顺序操作） */
        public int slotOf(String unitId) {
            List<com.voidvvv.kz_auto_chess_n.data.UnitData> slots = shop.getSlots();
            for (int i = 0; i < slots.size(); i++) {
                if (slots.get(i) != null && slots.get(i).getId().equals(unitId)) {
                    return i;
                }
            }
            return -1;
        }

        /** 按模板买入（对拍固定槽位存在该模板——装配错误 fail-fast） */
        public void buyTemplate(String unitId) {
            int slot = slotOf(unitId);
            if (slot < 0) {
                throw new IllegalStateException("对拍商店无该模板可买: " + unitId + " / 槽序=" + slotIds());
            }
            buy(slot);
        }

        /** 主动认输（确定性败局——败箱/同轮重试路径验证不依赖交战强度） */
        public void surrender() {
            startBattle();
            commands.addCommand(com.voidvvv.kz_auto_chess_n.command.SurrenderCommand.INSTANCE);
            commands.executeAll(ctx);
            flow.onBattleOver(ctx); // isOver 观察-委托（屏幕渲染循环同款时序）
        }

        public void refreshPaid() {
            commands.addCommand(RefreshShopCommand.INSTANCE);
            commands.executeAll(ctx);
        }

        public void buyExp() {
            commands.addCommand(BuyExpCommand.INSTANCE);
            commands.executeAll(ctx);
        }

        /** 备战席第 {@code benchIndex} 枚部署到玩家区格（MoveUnit bench→board 同链路；
         *  注意：部署会把该棋子移出备战席、后续条目前移——连续布阵请用 {@link #deployNextBench}） */
        public void deploy(int benchIndex, int gridX, int gridY) {
            int unitId = ctx.getPlayer().getBench().get(benchIndex).getId();
            commands.addCommand(new MoveUnitCommand(unitId, new PlacementTarget.Cell(gridX, gridY)));
            commands.executeAll(ctx);
        }

        /** 备战席首位部署到玩家区格（连续布阵的按序取用——Player.deploy 会移除席上条目） */
        public void deployNextBench(int gridX, int gridY) {
            deploy(0, gridX, gridY);
        }

        /** 开战并打完（确定性事件流）+ isOver 观察-委托（对拍每战独立 RNG 起点——§0.2） */
        public void battleToEnd() {
            startBattle();
            battleSystem.runToEnd(lastBattle, MAX_TICKS);
            flow.onBattleOver(ctx);
        }

        /** 开战但不推进（§1.1 预判对齐观察面：开局索敌 targetId 在 step 前已定） */
        public void startBattleOnly() {
            startBattle();
        }

        /** 注入胜局（§0.3 账本独立验证）：开战后直接判胜并走观察-委托，不消耗交战随机 */
        public void injectWin() {
            startBattle();
            lastBattle.finish(BattleOutcome.PLAYER_WIN); // 幂等（finish 首个结局生效）
            flow.onBattleOver(ctx);
        }

        private void startBattle() {
            commands.addCommand(StartBattleCommand.INSTANCE);
            commands.executeAll(ctx);
            lastBattle = ctx.getBattleState();
        }

        /** 领取胜箱/败箱指定序选项（胜箱：0 金币 / 1 经验书 / 2 装备；败箱：0 金币 / 1 经验书） */
        public void pickChest(int optionIndex) {
            commands.addCommand(new PickChestCommand(optionIndex));
            commands.executeAll(ctx);
        }

        /** 按种类领箱（0 = 金币 / 1 = 经验书；种类序是 ChestSystem 固定槽位契约） */
        public void pickChestByKind(ChestOption.Kind kind) {
            ChestOffer offer = ctx.getRunState().getPendingChest();
            for (int i = 0; i < offer.getOptions().size(); i++) {
                if (offer.getOptions().get(i).getKind() == kind) {
                    pickChest(i);
                    return;
                }
            }
            throw new IllegalStateException("宝箱缺少选项种类: " + kind);
        }

        public BattleState lastBattle() {
            return lastBattle;
        }

        // —— 观测面 ——

        /** 商店槽序（模板 id，空槽 = null——确定性对比基准） */
        public List<String> slotIds() {
            List<String> ids = new ArrayList<String>();
            for (com.voidvvv.kz_auto_chess_n.data.UnitData template : shop.getSlots()) {
                ids.add(template == null ? null : template.getId());
            }
            return ids;
        }

        /** 敌阵（WaveSpec 按模板引用 + 坐标/倍率判等） */
        public List<com.voidvvv.kz_auto_chess_n.entities.WaveSpec> wave() {
            return ctx.getRunState().getEnemyWave();
        }

        /** 原始事件流（CombatEvent.equals 逐位判等——§0.2「相同操作比较原始事件」） */
        public List<com.voidvvv.kz_auto_chess_n.entities.CombatEvent> events() {
            return lastBattle.getEvents();
        }
    }
}
