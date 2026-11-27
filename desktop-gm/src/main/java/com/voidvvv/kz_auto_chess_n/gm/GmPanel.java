package com.voidvvv.kz_auto_chess_n.gm;

import com.voidvvv.kz_auto_chess_n.entities.GamePhase;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.Result;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.RunView;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.SceneView;
import com.voidvvv.kz_auto_chess_n.screens.PracticeAccess.View;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.Color;
import java.awt.FlowLayout;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.Supplier;

/** EDT-owned window, input bindings and refresh timer. The game never receives Swing events. */
final class GmPanel {
    private final PracticeAccess access;
    private final Attachment attachment;
    private final JFrame window = new JFrame("余烬军团 · GM 调试");
    private final WindowAdapter windowListener;
    private final Timer timer = new Timer(200, e -> refresh());
    private final JLabel location = new JLabel("正在读取当前远征…");
    private final JLabel economy = new JLabel(" ");
    private final JLabel enemies = new JLabel(" ");
    private final JLabel blocked = new JLabel(" ");
    private final JLabel status = new JLabel(" ");
    private final JLabel jumpRule = new JLabel(" ");
    private final JPanel progress = new JPanel();
    private final JComboBox<SceneChoice> grants = new JComboBox<>();
    private final JComboBox<SceneChoice> target = new JComboBox<>();
    private final JTextField gold = new JTextField(10);
    private final JTextField exp = new JTextField(6);
    private final JTextField wave = new JTextField("1", 4);
    private final JButton setGold = new JButton("设置");
    private final JButton changeExp = new JButton("调整");
    private final JButton grant = new JButton("开放");
    private final JButton revoke = new JButton("撤销");
    private final JButton jump = new JButton("跳转");
    private View view;
    private boolean refreshPending;
    private boolean actionPending;

    GmPanel(PracticeAccess access, Consumer<Runnable> gameQueue, Runnable detach) {
        this.access = access;
        this.attachment = new Attachment(gameQueue, SwingUtilities::invokeLater);
        windowListener = new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                detach.run();
            }
        };
        window.addWindowListener(windowListener);
        window.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        KeyStroke shortcut = KeyStroke.getKeyStroke(KeyEvent.VK_F10, 0);
        window.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(shortcut, "detach");
        window.getRootPane().getActionMap().put("detach", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                detach.run();
            }
        });
        JButton close = new JButton("卸载 GM（F10）");
        close.addActionListener(e -> detach.run());
        setGold.addActionListener(e -> number(gold.getText(), "金币", value -> {
            RunView run = view.run;
            execute(() -> access.setGold(run, value));
        }));
        changeExp.addActionListener(e -> number(exp.getText(), "经验改变量", value -> {
            RunView run = view.run;
            execute(() -> access.changeExp(run, value));
        }));
        grant.addActionListener(e -> setGrant(true));
        revoke.addActionListener(e -> setGrant(false));
        jump.addActionListener(e -> number(wave.getText(), "波次", this::jump));
        gold.setToolTipText("输入 0 至 " + Integer.MAX_VALUE + " 的新余额");

        JPanel content = new JPanel();
        content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
        content.setBorder(BorderFactory.createEmptyBorder(10, 12, 10, 12));
        JPanel run = section("当前远征");
        addRows(run, location, economy, enemies, blocked);
        run.add(row(new JLabel("金币绝对值"), gold, setGold,
                new JLabel("经验增减量（不降级）"), exp, changeExp));
        progress.setLayout(new BoxLayout(progress, BoxLayout.Y_AXIS));
        JPanel profile = section("档案场景开放");
        profile.add(progress);
        profile.add(row(grants, grant, revoke));
        JPanel travel = section("前往目标波次");
        travel.add(row(target, new JLabel("波次"), wave, jump));
        addRows(travel, jumpRule);
        content.add(run);
        content.add(Box.createVerticalStrut(6));
        content.add(profile);
        content.add(Box.createVerticalStrut(6));
        content.add(travel);
        content.add(row(close, status));
        window.setContentPane(content);
        window.pack();
        window.setSize(Math.max(700, window.getWidth()), window.getHeight() + 70);
        window.setLocationByPlatform(true);
        updateButtons();
    }

    void open() {
        window.setVisible(true);
        refresh();
        timer.start();
    }

    void close() {
        timer.stop();
        attachment.close();
        window.removeWindowListener(windowListener);
        window.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).clear();
        window.getRootPane().getActionMap().remove("detach");
        window.dispose();
    }

    private void refresh() {
        if (refreshPending || actionPending) return;
        refreshPending = true;
        attachment.submit(access::read, snapshot -> {
            refreshPending = false;
            show(snapshot);
        });
    }

    private void execute(Supplier<Result> work) {
        actionPending = true;
        updateButtons();
        attachment.submit(() -> new Reply(work.get(), access.read()), reply -> {
            actionPending = false;
            show(reply.view);
            notifyUser(reply.result.message, reply.result.applied && reply.result.saved);
        });
    }

    private void setGrant(boolean open) {
        SceneChoice selected = (SceneChoice) grants.getSelectedItem();
        if (selected != null) execute(() -> access.setSceneGrant(selected.id, open));
    }

    private void jump(int round) {
        SceneChoice selected = (SceneChoice) target.getSelectedItem();
        if (selected == null || view.run == null) return;
        RunView run = view.run;
        SceneView scene = view.scenes.get(target.getSelectedIndex());
        boolean newExpedition = !run.sceneId.equals(selected.id) || round < run.round;
        if (scene.isOpen() && round >= 1 && round <= run.totalRounds && newExpedition
                && JOptionPane.showConfirmDialog(window,
                "此操作将替换当前远征和挂起快照，并用同一英雄开启新练习远征。继续？",
                "开启新练习远征", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION) {
            return;
        }
        execute(() -> access.jump(run, selected.id, round));
    }

    private void show(View snapshot) {
        boolean first = view == null;
        view = snapshot;
        if (first) {
            for (SceneView scene : view.scenes) {
                SceneChoice choice = new SceneChoice(scene);
                grants.addItem(choice);
                target.addItem(choice);
                progress.add(row(new JLabel()));
            }
        }
        for (int i = 0; i < view.scenes.size(); i++) {
            SceneView scene = view.scenes.get(i);
            JLabel line = (JLabel) ((JPanel) progress.getComponent(i)).getComponent(0);
            String state = scene.naturalOpen ? "正常开放" : scene.granted ? "练习授权" : "锁定";
            line.setText(scene.name + "  [" + scene.id + "]   " + state
                    + (scene.completed ? " · 已正式通关" : "")
                    + (scene.prerequisite == null ? "" : " · 前置 " + scene.prerequisite));
        }
        RunView run = view.run;
        location.setText(run == null ? "当前没有对局" : run.sceneId + "   第 " + run.round + "/" + run.totalRounds
                + " 波   " + phaseLabel(run.phase) + (run.practice ? " · 练习远征" : "")
                + "   已耗重试 " + run.retriesUsed + "/" + run.retryLimit
                + (run.chestPending ? "   待领奖" : "")
                + (run.incomePaid ? "   本波补给已入账" : ""));
        economy.setText(run == null ? " " : "金币 " + run.gold + "   等级 " + run.level + "   经验 "
                + (run.nextLevelExp == 0 ? "MAX" : run.exp + "/" + run.nextLevelExp
                + "（再加 " + run.remainingExp + " 到满级）") + "   人口 " + run.deployed + "/" + run.population);
        enemies.setText(run == null ? " " : "敌阵：" + run.enemies);
        exp.setToolTipText(run == null ? null : "经验改变量范围 " + (-run.exp) + " 至 +" + run.remainingExp);
        blocked.setText(view.runBlockReason == null ? "备战可调整" : view.runBlockReason);
        showJumpRule();
        updateButtons();
    }

    private void showJumpRule() {
        jumpRule.setText(view == null || view.run == null ? " " : "1 至 " + view.run.totalRounds
                + " 波  ·  同场景向前保留阵容  ·  跨场景或回退开启新练习远征");
    }

    private void updateButtons() {
        boolean ready = view != null && !actionPending;
        boolean canEditRun = ready && view.runBlockReason == null;
        grant.setEnabled(ready);
        revoke.setEnabled(ready);
        setGold.setEnabled(canEditRun);
        changeExp.setEnabled(canEditRun);
        jump.setEnabled(canEditRun);
    }

    private void number(String input, String label, IntConsumer action) {
        int value;
        try {
            value = Integer.parseInt(input.trim());
        } catch (NumberFormatException ex) {
            notifyUser(label + "须为范围内的整数。", false);
            return;
        }
        action.accept(value);
    }

    private void notifyUser(String message, boolean saved) {
        status.setForeground(saved ? new Color(18, 105, 56) : new Color(154, 46, 32));
        status.setText(message);
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

    private static JPanel section(String title) {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createTitledBorder(title));
        return panel;
    }

    private static void addRows(JPanel parent, JLabel... labels) {
        for (JLabel label : labels) parent.add(row(label));
    }

    private static JPanel row(JComponent... children) {
        JPanel row = new JPanel(new FlowLayout(FlowLayout.LEADING, 6, 2));
        for (JComponent child : children) row.add(child);
        return row;
    }

    private static final class SceneChoice {
        final String id;
        final String name;

        SceneChoice(SceneView scene) {
            id = scene.id;
            name = scene.name;
        }

        @Override
        public String toString() {
            return name + " (" + id + ")";
        }
    }

    private static final class Reply {
        final Result result;
        final View view;

        Reply(Result result, View view) {
            this.result = result;
            this.view = view;
        }
    }
}
