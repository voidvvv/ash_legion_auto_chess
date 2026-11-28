package com.voidvvv.kz_auto_chess_n.gm;

import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.Result;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.RunView;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.SceneView;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.SlotView;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.TemplateView;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.View;
import com.voidvvv.kz_auto_chess_n.systems.RosterSystem.Edit;
import imgui.ImGui;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImInt;
import imgui.type.ImString;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** Dear ImGui widgets and immutable views. All practice operations are posted through the attachment. */
final class GmPanel {
    private enum Page { PROGRESS, ROSTER, BAG }
    private static final String[] STARS = {"1 星", "2 星", "3 星"};
    private final PracticeAccess access;
    private final Attachment attachment;
    private final GmEquipmentPanel equipment;
    private final Runnable detach;
    private final ImString gold = new ImString(32);
    private final ImString exp = new ImString(32);
    private final ImString wave = new ImString("1", 24);
    private final ImInt stars = new ImInt();
    private View view;
    private String status = "";
    private boolean successful;
    private boolean actionPending;
    private boolean refreshPending;
    private long nextRefresh;
    private int progressIndex;
    private int targetIndex;
    private String templateId;
    private int slotIndex = -1;
    private int selectedUnitId;
    private boolean onBoard;
    private Page page = Page.PROGRESS;
    private Runnable confirmedJump;

    GmPanel(PracticeAccess access, Consumer<Runnable> gameQueue, Runnable detach) {
        this.access = access;
        this.detach = detach;
        attachment = new Attachment(gameQueue, Runnable::run);
        equipment = new GmEquipmentPanel(access, this::execute);
    }

    void close() {
        attachment.close();
    }

    void draw(int width, int height, GmIcons icons) {
        refresh();
        ImGui.setNextWindowPos(0, 0);
        ImGui.setNextWindowSize(width, height);
        ImGui.begin("GM 调试", ImGuiWindowFlags.NoTitleBar | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoResize
                | ImGuiWindowFlags.NoCollapse | ImGuiWindowFlags.NoSavedSettings);
        if (ImGui.button("卸载 GM（F10）")) detach.run();
        ImGui.sameLine();
        if (!status.isEmpty()) {
            if (successful) ImGui.textColored(0.57f, 0.88f, 0.68f, 1, status);
            else ImGui.textColored(1, 0.64f, 0.51f, 1, status);
        }
        ImGui.separator();
        if (view == null) {
            ImGui.textUnformatted("正在读取当前远征…");
        } else {
            showRun(view.run);
            if (pageButton("状态与跳转", page == Page.PROGRESS)) page = Page.PROGRESS;
            ImGui.sameLine();
            if (pageButton("备战阵容", page == Page.ROSTER)) page = Page.ROSTER;
            ImGui.sameLine();
            if (pageButton("背包", page == Page.BAG)) page = Page.BAG;
            ImGui.separator();
            if (page == Page.ROSTER) showRoster(view.run, icons);
            else if (page == Page.BAG) equipment.drawBag(view.run, canEditRun(), icons);
            else showProgress(view);
        }
        ImGui.end();
    }

    private void showRun(RunView run) {
        if (run != null) {
            ImGui.textUnformatted(run.sceneId + "   第 " + run.round + "/" + run.totalRounds + " 波   "
                    + phaseLabel(run.phase) + (run.practice ? " · 练习远征" : "")
                    + "   已耗重试 " + run.retriesUsed + "/" + run.retryLimit
                    + (run.chestPending ? "   待领奖" : "") + (run.incomePaid ? "   本波补给已入账" : ""));
            ImGui.textUnformatted("金币 " + run.gold + "   等级 " + run.level + "   经验 "
                    + (run.nextLevelExp == 0 ? "MAX" : run.exp + "/" + run.nextLevelExp
                    + "（再加 " + run.remainingExp + " 到满级）")
                    + "   人口 " + run.deployed + "/" + run.population);
        }
        ImGui.textDisabled(view.runBlockReason == null ? "备战可调整" : view.runBlockReason);
    }

    private void showProgress(View snapshot) {
        RunView run = snapshot.run;
        if (run != null) ImGui.textWrapped("敌阵：" + run.enemies);
        ImGui.beginDisabled(!canEditRun());
        ImGui.setNextItemWidth(130);
        ImGui.inputText("金币绝对值", gold);
        ImGui.sameLine();
        if (ImGui.button("设置金币")) integer(gold, "金币", n -> execute(() -> access.setGold(run, n)));
        ImGui.sameLine(380);
        ImGui.setNextItemWidth(100);
        ImGui.inputText("经验增减量（不降级）", exp);
        ImGui.sameLine();
        if (ImGui.button("调整经验")) integer(exp, "经验改变量", n -> execute(() -> access.changeExp(run, n)));
        ImGui.endDisabled();
        ImGui.spacing();
        ImGui.separatorText("档案场景开放");
        for (SceneView scene : snapshot.scenes) {
            String state = scene.naturalOpen ? "正常开放" : scene.granted ? "练习授权" : "锁定";
            ImGui.textUnformatted(scene.name + "  [" + scene.id + "]   " + state
                    + (scene.completed ? " · 已正式通关" : "")
                    + (scene.prerequisite == null ? "" : " · 前置 " + scene.prerequisite));
        }
        if (snapshot.scenes.isEmpty()) return;
        progressIndex = chooseScene("授权场景", snapshot.scenes, progressIndex);
        SceneView selected = snapshot.scenes.get(progressIndex);
        ImGui.beginDisabled(actionPending);
        ImGui.sameLine();
        if (ImGui.button("开放")) execute(() -> access.setSceneGrant(selected.id, true));
        ImGui.sameLine();
        if (ImGui.button("撤销")) execute(() -> access.setSceneGrant(selected.id, false));
        ImGui.endDisabled();
        ImGui.spacing();
        ImGui.separatorText("前往目标波次");
        ImGui.beginDisabled(!canEditRun());
        targetIndex = chooseScene("目标场景", snapshot.scenes, targetIndex);
        ImGui.sameLine();
        ImGui.setNextItemWidth(70);
        ImGui.inputText("波次", wave);
        ImGui.sameLine();
        if (ImGui.button("跳转")) {
            SceneView target = snapshot.scenes.get(targetIndex);
            integer(wave, "波次", value -> requestJump(run, target, value));
        }
        ImGui.endDisabled();
        ImGui.textWrapped("同场景向前保留阵容。跨场景或回退开启新的练习远征。");
        if (ImGui.beginPopupModal("开启新练习远征", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.textUnformatted("将替换当前远征和挂起快照，并用同一英雄开启新练习远征。");
            if (ImGui.button("确认跳转")) {
                confirmedJump.run();
                ImGui.closeCurrentPopup();
            }
            ImGui.sameLine();
            if (ImGui.button("取消")) ImGui.closeCurrentPopup();
            ImGui.endPopup();
        }
    }

    private void showRoster(RunView run, GmIcons icons) {
        if (run == null) return;
        ImGui.separatorText("备战席");
        cells(run.bench, run.bench.size(), false);
        ImGui.spacing();
        ImGui.separatorText("己方棋盘");
        cells(run.deployment, run.boardColumns, true);
        ImGui.spacing();
        List<SlotView> selectedArea = onBoard ? run.deployment : run.bench;
        if (slotIndex < 0 || slotIndex >= selectedArea.size()) {
            ImGui.textDisabled("选择一个位置编辑。");
            return;
        }
        SlotView slot = selectedArea.get(slotIndex);
        ImGui.separatorText((onBoard ? "己方格 " : "备战席 ") + slot.position);
        ImGui.textUnformatted(slot.occupied()
                ? slot.name + "  " + slot.star + " 星   装备 " + slot.equipment + " 件   #" + slot.unitId
                : "空位");
        ImGui.setNextItemWidth(275);
        TemplateView chosen = template(run.templates);
        if (ImGui.beginCombo("棋子模板", chosen == null ? "选择棋子" : chosen.name)) {
            for (TemplateView item : run.templates) {
                if (ImGui.selectable(item.name + "##" + item.id, item == chosen)) templateId = item.id;
            }
            ImGui.endCombo();
        }
        ImGui.sameLine();
        ImGui.setNextItemWidth(90);
        ImGui.combo("星级", stars, STARS);
        ImGui.beginDisabled(!canEditRun());
        if (slot.occupied()) {
            if (ImGui.button("调整星级")) edit(run, slot, Edit.STAR);
            ImGui.sameLine();
            if (ImGui.button("替换棋子")) edit(run, slot, Edit.REPLACE);
            ImGui.sameLine();
            if (ImGui.button("移除棋子")) edit(run, slot, Edit.REMOVE);
        } else if (slot.canAdd) {
            if (ImGui.button("新增棋子")) edit(run, slot, Edit.ADD);
        } else {
            ImGui.textDisabled("备战席按顺序排列，请选第一个空位。");
        }
        ImGui.endDisabled();
        if (slot.occupied() && selectedUnitId == slot.unitId) {
            equipment.drawSlots(run, slot, canEditRun(), icons);
        } else if (slot.occupied()) {
            ImGui.textDisabled("棋子已变化，请重新选择以编辑装备。");
        }
    }

    private void cells(List<SlotView> slots, int columns, boolean board) {
        float spacing = ImGui.getStyle().getItemSpacingX();
        float width = (float) Math.floor((ImGui.getContentRegionAvailX() - (columns - 1) * spacing) / columns);
        for (int i = 0; i < slots.size(); i++) {
            if (i % columns != 0) ImGui.sameLine();
            SlotView slot = slots.get(i);
            boolean selected = onBoard == board && slotIndex == i;
            if (selected) ImGui.pushStyleColor(ImGuiCol.Button, 0.28f, 0.40f, 0.53f, 1);
            String heading = slot.position + (slot.occupied() ? "  " + slot.star + " 星" : "");
            String occupant = slot.occupied() ? slot.name : "空位";
            if (ImGui.button(heading + "\n" + occupant + "###" + (board ? "cell" : "bench") + i,
                    width, board ? 49 : 57)) {
                onBoard = board;
                slotIndex = i;
                selectedUnitId = slot.unitId;
                equipment.clearSlotChoices();
                stars.set(slot.occupied() ? slot.star - 1 : 0);
                templateId = slot.templateId;
            }
            if (slot.occupied() && ImGui.isItemHovered()) ImGui.setTooltip(slot.name);
            if (selected) ImGui.popStyleColor();
        }
    }

    private static boolean pageButton(String label, boolean active) {
        if (active) ImGui.pushStyleColor(ImGuiCol.Button, 0.28f, 0.40f, 0.53f, 1);
        boolean pressed = ImGui.button(label);
        if (active) ImGui.popStyleColor();
        return pressed;
    }

    private TemplateView template(List<TemplateView> templates) {
        for (TemplateView item : templates) {
            if (item.id.equals(templateId)) return item;
        }
        return null;
    }

    private void edit(RunView run, SlotView slot, Edit edit) {
        String template = templateId;
        int star = stars.get() + 1;
        execute(() -> access.editRoster(run, slot, edit, template, star));
    }

    private int chooseScene(String label, List<SceneView> scenes, int index) {
        ImGui.setNextItemWidth(230);
        if (ImGui.beginCombo(label, scenes.get(index).name)) {
            for (int i = 0; i < scenes.size(); i++) {
                SceneView scene = scenes.get(i);
                if (ImGui.selectable(scene.name + "  " + scene.id, i == index)) index = i;
            }
            ImGui.endCombo();
        }
        return index;
    }

    private void requestJump(RunView run, SceneView target, int round) {
        Runnable jump = () -> execute(() -> access.jump(run, target.id, round));
        if (target.isOpen() && round >= 1 && round <= run.totalRounds
                && (!run.sceneId.equals(target.id) || round < run.round)) {
            confirmedJump = jump;
            ImGui.openPopup("开启新练习远征");
        } else {
            jump.run();
        }
    }

    private boolean canEditRun() {
        return !actionPending && view != null && view.run != null && view.runBlockReason == null;
    }

    private void refresh() {
        long now = System.nanoTime();
        if (now < nextRefresh || refreshPending || actionPending) return;
        nextRefresh = now + 150_000_000L;
        refreshPending = true;
        attachment.submit(access::read, snapshot -> {
            adopt(snapshot);
            refreshPending = false;
        });
    }

    private void execute(Supplier<Result> work) {
        actionPending = true;
        attachment.submit(work, result -> {
            actionPending = false;
            status = result.message;
            successful = result.applied && result.saved;
            adopt(access.read());
        });
    }

    private void adopt(View snapshot) {
        if (view == null || snapshot.run == null || !snapshot.run.sameExpedition(view.run)) {
            slotIndex = -1;
            selectedUnitId = 0;
            equipment.reset();
        }
        view = snapshot;
    }

    private void integer(ImString input, String label, IntConsumer action) {
        try {
            action.accept(Integer.parseInt(input.get().trim()));
        } catch (NumberFormatException ex) {
            status = label + "须为范围内的整数。";
            successful = false;
        }
    }

    private static String phaseLabel(GamePhase phase) {
        switch (phase) {
            case SHOPPING: return "备战";
            case BATTLE: return "战斗";
            case RESULT: return "战后结算";
            case RUN_END: return "远征结束";
            default: return phase.name();
        }
    }
}
