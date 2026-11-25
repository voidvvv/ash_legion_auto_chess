package com.voidvvv.kz_auto_chess_n.render.board;

/**
 * 棋盘原创素材键约定（品质改造第二阶段；纯函数 + 零 Gdx，headless 可测）。
 *
 * <p>键形（与 {@code art/generated_v2/manifest.json} board 类同源）：
 * 框 {@code board_frame_<sceneId>}（216×248，绘于原棋盘外各留 12 px）；
 * 地砖 {@code tile_<sceneId>_<area>_<a|b>}（32×32）——行 0~2 用 enemy、3 用 neutral、4~6 用 ally，
 * {@code (x+y)&1} 取 a/b 交错。占位层不生成这些键（原创新增键），经
 * {@code Assets#regionOrNull} 软查询；未命中时 {@code BattleRenderer} 回退白纹理旧棋盘画法。
 */
public final class BoardArtKeys {

    /** 棋盘框相对原棋盘的外留边（216 = 192 + 2×12；248 = 224 + 2×12） */
    public static final int FRAME_MARGIN_PX = 12;

    private BoardArtKeys() {
    }

    /** 棋盘外框键 */
    public static String frame(String sceneId) {
        return "board_frame_" + sceneId;
    }

    /** 地砖键（gridY 定区：0~2 敌 / 3 缓冲 / 4~6 友；a/b 按 (x+y) 奇偶交错） */
    public static String tile(String sceneId, int gridX, int gridY) {
        return "tile_" + sceneId + "_" + area(gridY) + "_" + (((gridX + gridY) & 1) == 0 ? 'a' : 'b');
    }

    /** 行号 → 区域名（敌 0~2 / 缓冲 3 / 友 4~6；与 BattleRenderer 白纹理时代 zoneTint 同口径） */
    public static String area(int gridY) {
        if (gridY <= 2) {
            return "enemy";
        }
        if (gridY == 3) {
            return "neutral";
        }
        return "ally";
    }
}
