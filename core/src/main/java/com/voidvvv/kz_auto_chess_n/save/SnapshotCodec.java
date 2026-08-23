package com.voidvvv.kz_auto_chess_n.save;

import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.voidvvv.kz_auto_chess_n.command.RunContext;
import com.voidvvv.kz_auto_chess_n.config.DataValidationException;
import com.voidvvv.kz_auto_chess_n.config.GameBalance;
import com.voidvvv.kz_auto_chess_n.data.EquipmentData;
import com.voidvvv.kz_auto_chess_n.data.GameData;
import com.voidvvv.kz_auto_chess_n.data.UnitData;
import com.voidvvv.kz_auto_chess_n.entities.Equipment;
import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.entities.Player;
import com.voidvvv.kz_auto_chess_n.entities.RunModifiers;
import com.voidvvv.kz_auto_chess_n.entities.RunState;
import com.voidvvv.kz_auto_chess_n.entities.SequentialIdIssuer;
import com.voidvvv.kz_auto_chess_n.entities.Unit;
import com.voidvvv.kz_auto_chess_n.entities.WaveSpec;
import com.voidvvv.kz_auto_chess_n.systems.ShopSystem;
import com.voidvvv.kz_auto_chess_n.utils.RandomGenerator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * 快照编解码 + 捕获/复原（纯函数，零 FileHandle——JUnit 直测）。
 * 捕获仅在 SHOPPING 期合法（battleState 恒 null——存档点决策 2026-08-20）；复原产物 = 完整
 * RunContext（runStarted=true / phase=SHOPPING / 敌阵/商店/名单/装备/RNG 流/发号器全复原）。
 * 引用悬空（数据改版）抛 DataValidationException——由 Store 决定删档（裁决 D20）。
 *
 * <p>装备池口径（spec CP16 TODO 定稿）：equipments 池 = 已穿装备（名单序：备战席入席序在前、
 * 部署扫描序在后）+ 背包序；UnitSnapshot.equippedItemIndex 直存池下标（捕获期一次折算，
 * write/read 对称直存）。
 */
public final class SnapshotCodec {

    private static final JsonReader READER = new JsonReader();

    private SnapshotCodec() {
    }

    /** 捕获（BattleScreen 经 MetaService 调用；要求 phase == SHOPPING 且 runStarted） */
    public static RunSnapshot capture(RunContext ctx) {
        Objects.requireNonNull(ctx, "ctx 不能为 null");
        RunState runState = ctx.getRunState();
        if (!runState.isRunStarted() || runState.getPhase() != GamePhase.SHOPPING) {
            throw new IllegalStateException("快照捕获仅限备战期（存档点决策 2026-08-20）");
        }
        Player player = ctx.getPlayer();
        List<Unit> benchUnits = new ArrayList<Unit>(player.getBench());

        // 装备池：已穿（名单序 = 备战席 + 部署扫描序）+ 背包序；equippedItemIndex 直存池下标
        List<RunSnapshot.EquipmentSnapshot> pool = new ArrayList<RunSnapshot.EquipmentSnapshot>();
        List<List<Integer>> equippedIndicesPerUnit = new ArrayList<List<Integer>>();
        for (Unit unit : rosterOf(player)) {
            List<Integer> indices = new ArrayList<Integer>();
            for (Equipment item : unit.getEquipped()) {
                indices.add(pool.size());
                pool.add(new RunSnapshot.EquipmentSnapshot(item.getId(), item.getTemplate().getId()));
            }
            equippedIndicesPerUnit.add(indices);
        }
        List<RunSnapshot.EquipmentSnapshot> inventory = new ArrayList<RunSnapshot.EquipmentSnapshot>();
        for (Equipment item : player.getInventory()) {
            inventory.add(new RunSnapshot.EquipmentSnapshot(item.getId(), item.getTemplate().getId()));
        }
        pool.addAll(inventory);

        // units 池：备战席入席序在前；部署表 18 格扫描序（y↑x↑）在后，格位 = units 下标
        List<RunSnapshot.UnitSnapshot> units = new ArrayList<RunSnapshot.UnitSnapshot>();
        List<Integer> benchIndex = new ArrayList<Integer>();
        for (int i = 0; i < benchUnits.size(); i++) {
            benchIndex.add(units.size());
            units.add(unitSnapshot(benchUnits.get(i), equippedIndicesPerUnit.get(i)));
        }
        List<Integer> grid = new ArrayList<Integer>(
                Arrays.asList(new Integer[GameBalance.BOARD_COLS * 3]));
        for (int i = 0; i < grid.size(); i++) {
            grid.set(i, -1);
        }
        int deployedCursor = 0;
        for (int y = 4; y <= 6; y++) {
            for (int x = 0; x < GameBalance.BOARD_COLS; x++) {
                Unit unit = player.deployedAt(x, y);
                if (unit == null) {
                    continue;
                }
                grid.set((y - 4) * GameBalance.BOARD_COLS + x, units.size());
                units.add(unitSnapshot(unit, equippedIndicesPerUnit.get(benchUnits.size() + deployedCursor)));
                deployedCursor++;
            }
        }

        List<String> shopSlots = new ArrayList<String>();
        for (UnitData template : ctx.getShop().getSlots()) {
            shopSlots.add(template == null ? null : template.getId());
        }
        List<RunSnapshot.WaveEntrySnapshot> wave = new ArrayList<RunSnapshot.WaveEntrySnapshot>();
        for (WaveSpec spec : runState.getEnemyWave()) {
            wave.add(new RunSnapshot.WaveEntrySnapshot(spec.getTemplate().getId(), spec.getStar(),
                    spec.getScale(), spec.getGridX(), spec.getGridY()));
        }
        return new RunSnapshot(RunSnapshot.CURRENT_VERSION, runState.getSeed(),
                ctx.getRng().getConsumedCount(), runState.getSceneId(), runState.getHeroId(),
                runState.getRound(), runState.getMercyLossCount(), runState.getMercyGoldThisRound(),
                runState.getIdIssuer().peekNext(), player.getGold(),
                player.getLevel(), player.getCurrentExp(),
                units, benchIndex, grid, inventory, pool, shopSlots, wave);
    }

    /** 名单序（与 BattleSystem 派生序一致）：备战席入席序 + 部署扫描序 y↑x↑ */
    private static List<Unit> rosterOf(Player player) {
        List<Unit> roster = new ArrayList<Unit>(player.getBench());
        roster.addAll(player.getDeployedUnits());
        return roster;
    }

    private static RunSnapshot.UnitSnapshot unitSnapshot(Unit unit, List<Integer> equippedItemIndex) {
        return new RunSnapshot.UnitSnapshot(unit.getId(), unit.getTemplate().getId(),
                unit.getStar(), unit.getSpend(), equippedItemIndex);
    }

    /** 复原：RunContext（runStarted=true / phase=SHOPPING）；引用悬空抛 DataValidationException */
    public static RunContext restore(RunSnapshot s, GameData data, Profile profile, ShopSystem shop) {
        Objects.requireNonNull(s, "snapshot 不能为 null");
        Objects.requireNonNull(data, "data 不能为 null");
        Objects.requireNonNull(profile, "profile 不能为 null");
        Objects.requireNonNull(shop, "shop 不能为 null");
        if (s.getVersion() != RunSnapshot.CURRENT_VERSION) {
            throw new DataValidationException("run_snapshot.json: 不支持的快照版本 " + s.getVersion());
        }
        if (data.getScene(s.getSceneId()) == null) {
            throw new DataValidationException("run_snapshot.json/sceneId: 场景不存在: " + s.getSceneId());
        }
        if (s.getHeroId() != null && data.getHero(s.getHeroId()) == null) {
            throw new DataValidationException("run_snapshot.json/heroId: 英雄不存在: " + s.getHeroId());
        }
        // 装备实例池
        List<Equipment> equipmentPool = new ArrayList<Equipment>();
        for (RunSnapshot.EquipmentSnapshot es : s.getEquipments()) {
            EquipmentData template = data.getEquipment(es.getTemplateId());
            if (template == null) {
                throw new DataValidationException("run_snapshot.json/equipments: 装备模板不存在: "
                        + es.getTemplateId());
            }
            equipmentPool.add(new Equipment(es.getId(), template));
        }
        // 名单
        List<Unit> unitPool = new ArrayList<Unit>();
        for (RunSnapshot.UnitSnapshot us : s.getUnits()) {
            UnitData template = data.getUnit(us.getUnitId());
            if (template == null) {
                throw new DataValidationException("run_snapshot.json/units: 单位模板不存在: "
                        + us.getUnitId());
            }
            Unit unit = new Unit(us.getId(), template, us.getStar(), us.getSpend());
            for (int index : us.getEquippedItemIndex()) {
                if (index < 0 || index >= equipmentPool.size()) {
                    throw new DataValidationException("run_snapshot.json/units: 装备池下标越限: "
                            + us.getUnitId() + " → " + index);
                }
                unit.equip(equipmentPool.get(index)); // 槽位唯一性由捕获端保证，冲突即抛（防坏档）
            }
            unitPool.add(unit);
        }
        Player player = new Player(s.getPlayerGold(), s.getPlayerLevel(), s.getPlayerExp());
        List<Unit> bench = new ArrayList<Unit>();
        for (int index : s.getBenchUnitIndex()) {
            bench.add(unitPool.get(index));
        }
        Unit[] deployment = new Unit[GameBalance.BOARD_COLS * 3];
        for (int i = 0; i < deployment.length; i++) {
            int index = s.getDeploymentUnitIndex().get(i);
            deployment[i] = index < 0 ? null : unitPool.get(index);
        }
        player.restoreRoster(bench, deployment);
        for (int i = 0; i < s.getInventory().size(); i++) {
            // 池尾 inventory 段（capture 序）：equipments 池 = 已穿 + 背包
            player.addToInventory(equipmentPool.get(equipmentPool.size()
                    - s.getInventory().size() + i));
        }
        // 商店槽
        List<UnitData> slots = new ArrayList<UnitData>();
        for (String unitId : s.getShopSlotUnitIds()) {
            if (unitId == null) {
                slots.add(null);
                continue;
            }
            UnitData template = data.getUnit(unitId);
            if (template == null) {
                throw new DataValidationException("run_snapshot.json/shopSlots: 单位模板不存在: " + unitId);
            }
            slots.add(template);
        }
        shop.restoreSlots(slots);
        // RunState（modifiers 按当前档案 + heroId 重算——档案局中不可能变更，裁决 D10 补充）
        RunModifiers modifiers = ProfileService.runModifiers(
                s.getHeroId() == null ? null : data.getHero(s.getHeroId()), profile, data);
        RunState runState = new RunState(s.getSeed(), s.getSceneId(), s.getHeroId(), modifiers,
                new SequentialIdIssuer(s.getIdIssuerNext()));
        runState.setRound(s.getRound());
        runState.setMercyLossCount(s.getMercyLossCount());
        runState.setMercyGoldThisRound(s.getMercyGoldThisRound());
        runState.markRunStarted();
        runState.setPhase(GamePhase.SHOPPING);
        List<WaveSpec> wave = new ArrayList<WaveSpec>();
        for (RunSnapshot.WaveEntrySnapshot we : s.getEnemyWave()) {
            UnitData template = data.getUnit(we.getUnitId());
            if (template == null) {
                throw new DataValidationException("run_snapshot.json/enemyWave: 单位模板不存在: "
                        + we.getUnitId());
            }
            wave.add(new WaveSpec(template, we.getStar(), we.getScale(), we.getGridX(), we.getGridY()));
        }
        runState.setEnemyWave(wave);
        return new RunContext(player, runState, data,
                new RandomGenerator(s.getSeed(), s.getRngConsumedCount()), shop);
    }

    // —— 序列化（StringBuilder 手拼，字段序固定确定性输出——沿 ProfileCodec 口径）与反序列化 ——

    public static String write(RunSnapshot s) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{\"version\":").append(s.getVersion());
        sb.append(",\"seed\":").append(s.getSeed());
        sb.append(",\"rngConsumedCount\":").append(s.getRngConsumedCount());
        sb.append(",\"sceneId\":\"").append(s.getSceneId()).append('"');
        if (s.getHeroId() != null) {
            sb.append(",\"heroId\":\"").append(s.getHeroId()).append('"');
        } else {
            sb.append(",\"heroId\":null");
        }
        sb.append(",\"round\":").append(s.getRound());
        sb.append(",\"mercyLossCount\":").append(s.getMercyLossCount());
        sb.append(",\"mercyGoldThisRound\":").append(s.getMercyGoldThisRound());
        sb.append(",\"idIssuerNext\":").append(s.getIdIssuerNext());
        sb.append(",\"playerGold\":").append(s.getPlayerGold());
        sb.append(",\"playerLevel\":").append(s.getPlayerLevel());
        sb.append(",\"playerExp\":").append(s.getPlayerExp());
        sb.append(",\"units\":[");
        boolean first = true;
        for (RunSnapshot.UnitSnapshot u : s.getUnits()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"id\":").append(u.getId())
                    .append(",\"unitId\":\"").append(u.getUnitId()).append('"')
                    .append(",\"star\":").append(u.getStar())
                    .append(",\"spend\":").append(u.getSpend())
                    .append(",\"equippedItemIndex\":");
            appendIntArray(sb, u.getEquippedItemIndex());
            sb.append('}');
        }
        sb.append("],\"benchUnitIndex\":");
        appendIntArray(sb, s.getBenchUnitIndex());
        sb.append(",\"deploymentUnitIndex\":");
        appendIntArray(sb, s.getDeploymentUnitIndex());
        sb.append(",\"inventory\":");
        appendEquipmentArray(sb, s.getInventory());
        sb.append(",\"equipments\":");
        appendEquipmentArray(sb, s.getEquipments());
        sb.append(",\"shopSlotUnitIds\":[");
        first = true;
        for (String unitId : s.getShopSlotUnitIds()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            if (unitId == null) {
                sb.append("null");
            } else {
                sb.append('"').append(unitId).append('"');
            }
        }
        sb.append("],\"enemyWave\":[");
        first = true;
        for (RunSnapshot.WaveEntrySnapshot we : s.getEnemyWave()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"unitId\":\"").append(we.getUnitId()).append('"')
                    .append(",\"star\":").append(we.getStar())
                    .append(",\"scale\":").append(we.getScale())
                    .append(",\"gridX\":").append(we.getGridX())
                    .append(",\"gridY\":").append(we.getGridY())
                    .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    private static void appendIntArray(StringBuilder sb, List<Integer> values) {
        sb.append('[');
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(values.get(i));
        }
        sb.append(']');
    }

    private static void appendEquipmentArray(StringBuilder sb,
                                             List<RunSnapshot.EquipmentSnapshot> list) {
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            RunSnapshot.EquipmentSnapshot es = list.get(i);
            sb.append("{\"id\":").append(es.getId())
                    .append(",\"templateId\":\"").append(es.getTemplateId()).append("\"}");
        }
        sb.append(']');
    }

    public static RunSnapshot read(String json) {
        JsonValue root = READER.parse(json);
        if (!root.isObject()) {
            throw new DataValidationException("run_snapshot.json: 根节点必须是对象");
        }
        checkUnknownKeys(root, "version", "seed", "rngConsumedCount", "sceneId", "heroId", "round",
                "mercyLossCount", "mercyGoldThisRound", "idIssuerNext", "playerGold", "playerLevel",
                "playerExp", "units", "benchUnitIndex", "deploymentUnitIndex", "inventory",
                "equipments", "shopSlotUnitIds", "enemyWave");
        int version = requireInt(root, "version");
        if (version != RunSnapshot.CURRENT_VERSION) {
            throw new DataValidationException("run_snapshot.json/version: 不支持的快照版本 " + version);
        }
        long seed = requireLong(root, "seed");
        int rngConsumedCount = requireNonNegativeInt(root, "rngConsumedCount");
        int round = requireInt(root, "round");
        if (round < 1 || round > GameBalance.TOTAL_ROUNDS) {
            throw new DataValidationException(
                    "run_snapshot.json/round: 必须在 1~" + GameBalance.TOTAL_ROUNDS + "，实际=" + round);
        }
        int mercyLossCount = requireNonNegativeInt(root, "mercyLossCount");
        int mercyGoldThisRound = requireNonNegativeInt(root, "mercyGoldThisRound");
        int idIssuerNext = requireInt(root, "idIssuerNext");
        if (idIssuerNext < 1) {
            throw new DataValidationException("run_snapshot.json/idIssuerNext: 必须 ≥ 1，实际=" + idIssuerNext);
        }
        int playerGold = requireNonNegativeInt(root, "playerGold");
        int playerLevel = requireInt(root, "playerLevel");
        int playerExp = requireNonNegativeInt(root, "playerExp");

        List<RunSnapshot.UnitSnapshot> units = new ArrayList<RunSnapshot.UnitSnapshot>();
        JsonValue unitsNode = requireArray(root, "units");
        for (JsonValue u = unitsNode.child; u != null; u = u.next) {
            requireObject(u, "run_snapshot.json/units[]");
            checkUnknownKeys(u, "id", "unitId", "star", "spend", "equippedItemIndex");
            units.add(new RunSnapshot.UnitSnapshot(requireInt(u, "id"),
                    requireString(u, "unitId"), requireInt(u, "star"), requireInt(u, "spend"),
                    readIntArray(requireArray(u, "equippedItemIndex"))));
        }
        List<Integer> benchUnitIndex = readIntArray(requireArray(root, "benchUnitIndex"));
        List<Integer> deploymentUnitIndex = readIntArray(requireArray(root, "deploymentUnitIndex"));
        if (deploymentUnitIndex.size() != GameBalance.BOARD_COLS * 3) {
            throw new DataValidationException("run_snapshot.json/deploymentUnitIndex: 长度必须 = "
                    + GameBalance.BOARD_COLS * 3 + "，实际=" + deploymentUnitIndex.size());
        }
        List<RunSnapshot.EquipmentSnapshot> inventory =
                readEquipmentArray(requireArray(root, "inventory"));
        List<RunSnapshot.EquipmentSnapshot> equipments =
                readEquipmentArray(requireArray(root, "equipments"));
        List<String> shopSlotUnitIds = new ArrayList<String>();
        JsonValue shopNode = requireArray(root, "shopSlotUnitIds");
        for (JsonValue sid = shopNode.child; sid != null; sid = sid.next) {
            if (sid.isNull()) {
                shopSlotUnitIds.add(null);
            } else if (sid.isString()) {
                shopSlotUnitIds.add(sid.asString());
            } else {
                throw new DataValidationException(
                        "run_snapshot.json/shopSlotUnitIds: 元素必须为字符串或 null");
            }
        }
        if (shopSlotUnitIds.size() != GameBalance.SHOP_SLOTS) {
            throw new DataValidationException("run_snapshot.json/shopSlotUnitIds: 长度必须 = "
                    + GameBalance.SHOP_SLOTS + "，实际=" + shopSlotUnitIds.size());
        }
        List<RunSnapshot.WaveEntrySnapshot> enemyWave = new ArrayList<RunSnapshot.WaveEntrySnapshot>();
        JsonValue waveNode = requireArray(root, "enemyWave");
        for (JsonValue w = waveNode.child; w != null; w = w.next) {
            requireObject(w, "run_snapshot.json/enemyWave[]");
            checkUnknownKeys(w, "unitId", "star", "scale", "gridX", "gridY");
            enemyWave.add(new RunSnapshot.WaveEntrySnapshot(requireString(w, "unitId"),
                    requireInt(w, "star"), (float) requireDouble(w, "scale"),
                    requireInt(w, "gridX"), requireInt(w, "gridY")));
        }
        return new RunSnapshot(version, seed, rngConsumedCount, requireString(root, "sceneId"),
                optionalString(root, "heroId"), round, mercyLossCount, mercyGoldThisRound,
                idIssuerNext, playerGold, playerLevel, playerExp, units, benchUnitIndex,
                deploymentUnitIndex, inventory, equipments, shopSlotUnitIds, enemyWave);
    }

    private static List<Integer> readIntArray(JsonValue node) {
        List<Integer> values = new ArrayList<Integer>();
        for (JsonValue v = node.child; v != null; v = v.next) {
            if (!v.isNumber()) {
                throw new DataValidationException("run_snapshot.json: 数组元素必须为数字");
            }
            double d = v.asDouble();
            if (Math.rint(d) != d) {
                throw new DataValidationException("run_snapshot.json: 数组元素必须为整数，实际=" + d);
            }
            values.add((int) d);
        }
        return values;
    }

    private static List<RunSnapshot.EquipmentSnapshot> readEquipmentArray(JsonValue node) {
        List<RunSnapshot.EquipmentSnapshot> list = new ArrayList<RunSnapshot.EquipmentSnapshot>();
        for (JsonValue e = node.child; e != null; e = e.next) {
            requireObject(e, "run_snapshot.json/装备条目");
            checkUnknownKeys(e, "id", "templateId");
            list.add(new RunSnapshot.EquipmentSnapshot(requireInt(e, "id"),
                    requireString(e, "templateId")));
        }
        return list;
    }

    private static void checkUnknownKeys(JsonValue obj, String... allowed) {
        for (JsonValue child = obj.child; child != null; child = child.next) {
            boolean known = false;
            for (String a : allowed) {
                if (a.equals(child.name())) {
                    known = true;
                    break;
                }
            }
            if (!known) {
                throw new DataValidationException("run_snapshot.json: 未知字段 " + child.name()
                        + "（允许: " + Arrays.toString(allowed) + "）");
            }
        }
    }

    private static JsonValue requireArray(JsonValue obj, String field) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull() || !child.isArray()) {
            throw new DataValidationException("run_snapshot.json/" + field + ": 缺失或非数组");
        }
        return child;
    }

    private static void requireObject(JsonValue v, String where) {
        if (!v.isObject()) {
            throw new DataValidationException(where + ": 必须为对象");
        }
    }

    private static String requireString(JsonValue obj, String field) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull() || !child.isString() || child.asString().trim().isEmpty()) {
            throw new DataValidationException("run_snapshot.json/" + field + ": 缺失或非字符串");
        }
        return child.asString();
    }

    private static String optionalString(JsonValue obj, String field) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull()) {
            return null;
        }
        if (!child.isString() || child.asString().trim().isEmpty()) {
            throw new DataValidationException("run_snapshot.json/" + field + ": 必须为非空字符串或 null");
        }
        return child.asString();
    }

    private static int requireInt(JsonValue obj, String field) {
        double d = requireDouble(obj, field);
        if (Math.rint(d) != d) {
            throw new DataValidationException("run_snapshot.json/" + field + ": 必须为整数，实际=" + d);
        }
        return (int) d;
    }

    private static int requireNonNegativeInt(JsonValue obj, String field) {
        int value = requireInt(obj, field);
        if (value < 0) {
            throw new DataValidationException("run_snapshot.json/" + field + ": 不允许负值，实际=" + value);
        }
        return value;
    }

    private static long requireLong(JsonValue obj, String field) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull() || !child.isNumber()) {
            throw new DataValidationException("run_snapshot.json/" + field + ": 缺失或非数字");
        }
        return child.asLong();
    }

    private static double requireDouble(JsonValue obj, String field) {
        JsonValue child = obj.get(field);
        if (child == null || child.isNull() || !child.isNumber()) {
            throw new DataValidationException("run_snapshot.json/" + field + ": 缺失或非数字");
        }
        return child.asDouble();
    }
}
