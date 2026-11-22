package com.voidvvv.kz_auto_chess_n.render.ui;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.voidvvv.kz_auto_chess_n.render.Assets;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 抽屉滚动文字块（UI 抽屉化批3）：scene2d {@code ScrollPane} 的内容 Actor——按行集高度撑开自身，
 * 由 ScrollPane 负责裁剪与滚动（介绍页最长真实文案 23 行必须能滚到底——批3 硬验收）。
 * 高度公式 {@link #heightFor} 为纯函数（headless 可测，DrawerTextCapacityTest 用它锚 ScrollPane 的可滚到底性）。
 */
final class DrawerTextBlock extends Actor {

    static final float PAD_X = 4f;
    static final float PAD_TOP = 4f;
    static final float PAD_BOTTOM = 4f;

    private final Assets assets;
    private final float textScale;
    private final float lineHeight;
    private List<String> lines = Collections.emptyList();

    /** @param textScale 字号缩放（0.75 = 9px 小字）；行高 = 12 × textScale */
    DrawerTextBlock(Assets assets, float textScale) {
        this.assets = Objects.requireNonNull(assets, "assets 不能为 null");
        this.textScale = textScale;
        this.lineHeight = 12f * textScale;
        setTouchable(Touchable.disabled);
    }

    /** 内容高度 = 上留白 + 行数 × 行高 + 下留白（0 行 = 上下留白之和，ScrollPane 视口更高时不滚） */
    static float heightFor(int lineCount, float lineHeight) {
        return PAD_TOP + Math.max(0, lineCount) * lineHeight + PAD_BOTTOM;
    }

    /** 设置行集（全量，不截断）并按行数撑高；宽度由 ScrollPane 视口给定 */
    void setLines(List<String> lines, float width) {
        this.lines = lines;
        setSize(width, heightFor(lines.size(), lineHeight));
    }

    List<String> lines() {
        return lines;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        BitmapFont font = assets.font();
        font.getData().setScale(textScale); // 用后即还（共用字体纪律）
        float alpha = DrawerSkin.alphaOf(this, parentAlpha);
        float top = getHeight() - PAD_TOP;
        for (int i = 0; i < lines.size(); i++) {
            DrawerSkin.drawText(batch, font, lines.get(i), PAD_X, top, alpha);
            top -= lineHeight;
        }
        font.getData().setScale(1f);
    }
}
