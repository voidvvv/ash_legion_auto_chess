package com.voidvvv.kz_auto_chess_n.gm;

import com.voidvvv.kz_auto_chess_n.data.EquipmentSlot;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.EquipmentTemplateView;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.ItemView;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.Result;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.RunView;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.SlotView;
import com.voidvvv.kz_auto_chess_n.systems.EquipmentSystem.InventoryEdit;
import imgui.ImGui;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

final class GmEquipmentPanel {
    private final PracticeAccess access;
    private final Consumer<Supplier<Result>> submit;
    private final int[] incoming = new int[EquipmentSlot.values().length];
    private int selectedBagItem;
    private String templateId;

    GmEquipmentPanel(PracticeAccess access, Consumer<Supplier<Result>> submit) {
        this.access = access;
        this.submit = submit;
    }

    void reset() {
        selectedBagItem = 0;
        templateId = null;
        clearSlotChoices();
    }

    void clearSlotChoices() {
        Arrays.fill(incoming, 0);
    }

    void drawBag(RunView run, boolean editable, GmIcons icons) {
        if (run == null) return;
        ImGui.separatorText("背包装备");
        ImGui.textUnformatted("共 " + run.inventory.size() + " 件 · 背包无上限");
        ItemView selected = find(run.inventory, selectedBagItem);
        if (selected == null && selectedBagItem != 0) ImGui.textDisabled("原装备已离开背包，请重新选择。");
        ImGui.setNextItemWidth(340);
        if (ImGui.beginCombo("选中装备", selected == null ? "选择背包装备" : itemLabel(selected))) {
            for (int i = 0; i < run.inventory.size(); i++) {
                ItemView item = run.inventory.get(i);
                icons.draw(item.template.slot);
                ImGui.sameLine();
                if (ImGui.selectable((i + 1) + ". " + itemLabel(item), item.id == selectedBagItem)) {
                    selectedBagItem = item.id;
                }
            }
            ImGui.endCombo();
        }
        ImGui.setNextItemWidth(340);
        String preview = "选择装备模板";
        for (EquipmentTemplateView template : run.equipmentTemplates) {
            if (template.id.equals(templateId)) preview = template.name;
        }
        if (ImGui.beginCombo("装备模板", preview)) {
            for (EquipmentTemplateView template : run.equipmentTemplates) {
                icons.draw(template.slot);
                ImGui.sameLine();
                if (ImGui.selectable(template.name + " · " + slotLabel(template.slot) + "##" + template.id,
                        template.id.equals(templateId))) templateId = template.id;
            }
            ImGui.endCombo();
        }
        ImGui.beginDisabled(!editable);
        if (ImGui.button("新增装备")) editBag(run, InventoryEdit.ADD, null);
        ImGui.sameLine();
        ImGui.beginDisabled(selected == null);
        if (ImGui.button("替换选中件")) editBag(run, InventoryEdit.REPLACE, selected);
        ImGui.sameLine();
        if (ImGui.button("移除选中件")) editBag(run, InventoryEdit.REMOVE, selected);
        ImGui.endDisabled();
        ImGui.endDisabled();
    }

    void drawSlots(RunView run, SlotView unit, boolean editable, GmIcons icons) {
        ImGui.separatorText("棋子装备槽");
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ImGui.pushID(slot.ordinal());
            ItemView equipped = unit.equipped.get(slot);
            icons.draw(slot);
            ImGui.sameLine();
            ImGui.textUnformatted(slotLabel(slot) + "  " + (equipped == null ? "空槽" : itemLabel(equipped)));
            ImGui.sameLine(270);
            ItemView choice = find(run.inventory, incoming[slot.ordinal()]);
            ImGui.setNextItemWidth(215);
            if (ImGui.beginCombo("##compatible", choice == null ? "选择兼容装备" : itemLabel(choice))) {
                for (ItemView item : run.inventory) {
                    if (item.template.slot == slot && ImGui.selectable(itemLabel(item), item == choice)) {
                        incoming[slot.ordinal()] = item.id;
                    }
                }
                ImGui.endCombo();
            }
            ImGui.beginDisabled(!editable);
            ImGui.sameLine();
            ImGui.beginDisabled(choice == null);
            if (ImGui.button(equipped == null ? "装配" : "换装")) {
                submit.accept(() -> access.editEquipmentSlot(run, unit, slot, choice));
            }
            ImGui.endDisabled();
            ImGui.sameLine();
            ImGui.beginDisabled(equipped == null);
            if (ImGui.button("卸装")) submit.accept(() -> access.editEquipmentSlot(run, unit, slot, null));
            ImGui.endDisabled();
            ImGui.endDisabled();
            ImGui.popID();
        }
    }

    private void editBag(RunView run, InventoryEdit edit, ItemView selected) {
        String template = templateId;
        submit.accept(() -> access.editInventory(run, edit, selected, template));
    }

    private static ItemView find(List<ItemView> bag, int id) {
        for (ItemView item : bag) if (item.id == id) return item;
        return null;
    }

    private static String itemLabel(ItemView item) {
        return item.template.name + " #" + item.id;
    }

    private static String slotLabel(EquipmentSlot slot) {
        switch (slot) {
            case WEAPON: return "武器";
            case ARMOR: return "护甲";
            case TRINKET: return "饰品";
            default: return slot.name();
        }
    }
}
