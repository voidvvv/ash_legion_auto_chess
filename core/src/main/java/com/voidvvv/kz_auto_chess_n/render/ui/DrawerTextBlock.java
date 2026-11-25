package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.GlyphLayout;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.voidvvv.kz_auto_chess_n.render.Assets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 抽屉滚动文字块（UI 抽屉化批3 → 品质改造批7 重排）：scene2d {@code ScrollPane} 的内容 Actor——
 * 按行集高度撑开自身，由 ScrollPane 负责裁剪与滚动。
 *
 * <p><b>批7 排版口径</b>（Codex 盘点修正）：
 * <ul>
 *   <li><b>实测度量</b>：行高与换行不再按「12 列全角 / 9px 行」估算——{@link GlyphLayout} 对当前字号
 *       实测单行高度与逐串宽度（Fusion Pixel 12px proportional 的 ASCII 步进并非恒半宽，列估算会溢出/亏宽）；
 *       中文/英文混排按空格断点贪心 + 超宽 token 逐字符硬断（显式换行保留），全部内容在裁切区内完整显示。</li>
 *   <li><b>内容偏移</b>：draw 以自身 getX()/getY() 为原点——ScrollPane 每帧把内容 setPosition 到
 *       (−scrollX, −scrollY) 偏移上（Label 等标准件同口径），旧写法画在 (0,0) 局部坐标导致
 *       「滚轮被消费、文字不随滚动移动」。</li>
 *   <li><b>共享字体纪律</b>：绘制前后保存/恢复 BitmapFont 的真实 scale 与 color（不是硬回 1/白）。</li>
 * </ul>
 *
 * <p>高度公式 {@link #heightFor} 为纯函数（headless 可测，DrawerTextCapacityTest 用它锚 ScrollPane 的可滚到底性）。
 */
final class DrawerTextBlock extends Actor {

    static final float PAD_X = 4f;
    static final float PAD_TOP = 4f;
    static final float PAD_BOTTOM = 4f;
    static final float SCROLLBAR_WIDTH = 9f;

    private final Assets assets;
    private float lineHeight;
    /** 实测复用（GL 线程单实例，零分配） */
    private final GlyphLayout measure = new GlyphLayout();
    private final Color savedColor = new Color();
    private List<String> lines = Collections.emptyList();

    DrawerTextBlock(Assets assets) {
        this.assets = Objects.requireNonNull(assets, "assets 不能为 null");
        setTouchable(Touchable.disabled);
    }

    /** 内容高度 = 上留白 + 行数 × 行高 + 下留白（0 行 = 上下留白之和，ScrollPane 视口更高时不滚） */
    static float heightFor(int lineCount, float lineHeight) {
        return PAD_TOP + Math.max(0, lineCount) * lineHeight + PAD_BOTTOM;
    }

    /**
     * 段落集 → 实测换行行集并按行数撑高（宽度由 ScrollPane 视口给定）。段落间为显式换行；
     * 段内中文/英文混排贪心折行（空格断点优先、超宽 token 逐字符硬断），全量不截断。
     */
    void setParagraphs(List<String> paragraphs, float width) {
        BitmapFont font = assets.bodyFont();
        float maxWidth = Math.max(1f, width - 2 * PAD_X - SCROLLBAR_WIDTH);
        lines = wrapMeasured(font, measure, paragraphs, maxWidth);
        lineHeight = font.getLineHeight();
        setSize(width, heightFor(lines.size(), lineHeight));
    }

    static List<String> wrapMeasured(BitmapFont font, GlyphLayout measure, List<String> paragraphs, float maxWidth) {
        List<String> wrapped = new ArrayList<String>();
        for (String paragraph : paragraphs) {
            for (String line : (paragraph == null ? "" : paragraph).split("\\r\\n|[\\r\\n]", -1)) {
                wrapLine(font, measure, line, maxWidth, wrapped);
            }
        }
        return wrapped;
    }

    /** 单串实测像素宽（当前字号下） */
    private static float widthOf(BitmapFont font, GlyphLayout measure, CharSequence text) {
        measure.setText(font, text);
        return measure.width;
    }

    /** 空格断点贪心折行（实测宽度）：单 token 超行宽按字符硬断；整段容纳 = 原样单行 */
    private static void wrapLine(BitmapFont font, GlyphLayout measure, String text, float maxWidth, List<String> out) {
        if (widthOf(font, measure, text) <= maxWidth) {
            out.add(text);
            return;
        }
        StringBuilder current = new StringBuilder();
        for (String token : text.split(" ")) {
            if (token.isEmpty()) {
                continue;
            }
            if (current.length() > 0) {
                String candidate = current + " " + token;
                if (widthOf(font, measure, candidate) <= maxWidth) {
                    current.replace(0, current.length(), candidate);
                    continue;
                }
                out.add(current.toString());
                current.setLength(0);
            }
            if (widthOf(font, measure, token) > maxWidth) {
                hardSplitMeasured(font, measure, token, maxWidth, out);
                continue;
            }
            current.append(token);
        }
        if (current.length() > 0) {
            out.add(current.toString());
        }
    }

    /** 逐字符硬断（实测宽度累计；防零推进保证至少 1 字符/行） */
    private static void hardSplitMeasured(BitmapFont font, GlyphLayout measure, String token,
                                          float maxWidth, List<String> out) {
        int start = 0;
        while (start < token.length()) {
            int end = start;
            while (end + 1 <= token.length()
                    && widthOf(font, measure, token.substring(start, end + 1)) <= maxWidth) {
                end++;
            }
            if (end == start) {
                end = start + 1;
            }
            out.add(token.substring(start, end));
            start = end;
        }
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (lines.isEmpty()) {
            return;
        }
        BitmapFont font = assets.bodyFont();
        savedColor.set(font.getColor());
        try {
            font.setColor(1f, 1f, 1f, DrawerSkin.alphaOf(this, parentAlpha));
            float top = getY() + getHeight() - PAD_TOP;
            for (String line : lines) {
                font.draw(batch, line, getX() + PAD_X, top);
                top -= lineHeight;
            }
        } finally {
            font.setColor(savedColor);
        }
    }
}
