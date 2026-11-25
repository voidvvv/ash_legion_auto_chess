package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.PixmapPacker;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeBitmapFontData;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;
import com.badlogic.gdx.utils.GdxNativesLoader;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 运行期字体覆盖回归（2026-11-25 三段字体：标题黑体 / 正文楷体 / UI 宋体）。
 *
 * <p>不创建 GL 纹理：用自备 {@link PixmapPacker} 走 {@code generateData}，只验证 FreeType
 * 栅格化结果与字符集。覆盖四层：
 * <ol>
 *   <li>三套 TTF 均为真实文件，{@code charset.txt} 覆盖 崽/饷/薇 与 assets/data 全部非 ASCII 字符；</li>
 *   <li>三套 TTF 的 cmap 均含标题/正文/UI 关键样例字符串的每个字；</li>
 *   <li>FreeType 对各自字符集逐字生成 0 缺失，且 1024² 页打包各 ≤ 1 页（控制运行期纹理体积）；</li>
 *   <li>逻辑字号/行高契约：标题 20px、UI 10px、正文 8.5px；行高 29/14.5/10（2× 栅格 × 0.5）。</li>
 * </ol>
 */
class FontCoverageTest {
    private static final Path ASSETS = Paths.get("..", "assets");
    private static final Path FONT_DIR = ASSETS.resolve("font");
    private static final Path CHARSET = FONT_DIR.resolve("charset.txt");
    private static final Path DATA_DIR = ASSETS.resolve("data");

    /** 标题字符集必须覆盖的真实标题（各屏/弹窗/终局/宝箱） */
    private static final String[] TITLE_SAMPLES = {
            "余烬军团", "远征准备", "图鉴", "测试样片", "暂停", "胜利", "战败", "超时",
            "对拍完成", "远征通关", "远征已放弃", "远征失败", "宝箱", "战败补给", "BOSS 宝箱"
    };
    /** UI 角色必须覆盖的标签/数值/按钮/页签样例 */
    private static final String[] UI_SAMPLES = {
            "开始远征", "继续远征", "开战", "出售", "速度 x1", "投降", "刷新 2金", "经验 4金",
            "锁店 · 下轮不刷新", "已锁店 · 点击取消", "长按商店查看",
            "自身", "羁绊", "装备", "卸下", "返回", "点击选择", "（已选）",
            "熟练度 Lv.1（升级解锁加成）", "抵达第 1/12 轮", "种子 12345", "通关「翡翠林地」解锁"
    };
    /** 正文角色必须覆盖的描述/悬停卡/详情样例（含回归字与真实 data 文案） */
    private static final String[] BODY_SAMPLES = {
            "购入即合成", "举例：下阵", "人口已满 · 拖至棋子上", "野兽 3→4 升档",
            "崽", "饷", "薇", "（敌方）", "被动：开局金币 +25",
            "旧王国军士，熄星之夜的幸存者，深知补给线的价值",
            "号令全军，攻击提升", "攻击 +15%", "兽人", "下一档 4（基础）：攻击 +10%"
    };

    @BeforeAll
    static void loadNatives() {
        GdxNativesLoader.load();
    }

    @Test
    @DisplayName("三段字体：charset 覆盖 崽/饷/薇 + data 全字；三 TTF cmap 含各类样例；逐字 0 缺失；各 ≤1 页")
    void threeRolesCoverGameText() throws IOException {
        assertThat(CHARSET).as("charset 资源").isRegularFile();
        String charset = new String(Files.readAllBytes(CHARSET), StandardCharsets.UTF_8);
        for (char c : Assets.REQUIRED_FONT_CHARS.toCharArray()) {
            assertThat(charset.indexOf(c)).as("charset 含回归字 %s (U+%04X)", c, (int) c)
                    .isGreaterThanOrEqualTo(0);
        }
        List<String> missingFromCharset = new ArrayList<String>();
        collectDataChars(charset, missingFromCharset);
        assertThat(missingFromCharset).as("charset 必须覆盖 assets/data 全部非 ASCII 字符").isEmpty();
        assertThat(Assets.FONT_LOGICAL_SCALE).isEqualTo(0.5f);

        assertRole("TITLE", Assets.FontRole.TITLE, Assets.TITLE_FONT_SIZE, Assets.TITLE_LINE_HEIGHT,
                TITLE_SAMPLES, titleCharacters(charset));
        assertRole("BODY", Assets.FontRole.BODY, Assets.BODY_FONT_SIZE, Assets.BODY_LINE_HEIGHT,
                BODY_SAMPLES, charset);
        assertRole("UI", Assets.FontRole.UI, Assets.UI_FONT_SIZE, Assets.UI_LINE_HEIGHT,
                UI_SAMPLES, charset);
    }

    private static void assertRole(String label, Assets.FontRole role, float logicalSize,
                                   float expectedLineHeight, String[] samples, String characters) {
        assertThat(role.rasterSize()).as("%s 2× 栅格字号", label)
                .isEqualTo(Math.round(logicalSize / Assets.FONT_LOGICAL_SCALE));
        Path ttf = FONT_DIR.resolve(role.path().substring("font/".length()));
        assertThat(ttf).as("%s TTF 资源", label).isRegularFile();

        FreeTypeFontGenerator generator = new FreeTypeFontGenerator(new FileHandle(ttf.toFile()));
        PixmapPacker packer = new PixmapPacker(Assets.FONT_PAGE_SIZE, Assets.FONT_PAGE_SIZE,
                Pixmap.Format.RGBA8888, 1, false);
        try {
            for (String sample : samples) {
                for (int i = 0; i < sample.length(); i++) {
                    char c = sample.charAt(i);
                    if (c == ' ') {
                        continue;
                    }
                    assertThat(characters.indexOf(c)).as("%s 字符集含样例 %s 的 %s (U+%04X)",
                            label, sample, c, (int) c).isGreaterThanOrEqualTo(0);
                    assertThat(generator.hasGlyph(c)).as("%s TTF cmap 含 %s (U+%04X)",
                            label, c, (int) c).isTrue();
                }
            }

            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.size = role.rasterSize();
            parameter.characters = characters;
            parameter.kerning = false;
            parameter.mono = false;
            parameter.hinting = FreeTypeFontGenerator.Hinting.AutoMedium;
            parameter.genMipMaps = false;
            parameter.packer = packer;
            FreeTypeBitmapFontData data =
                    generator.generateData(parameter, new FreeTypeBitmapFontData());

            assertThat(data.lineHeight * Assets.FONT_LOGICAL_SCALE)
                    .as("%s 逻辑行高（2× 栅格行高 %d × 0.5）", label, data.lineHeight)
                    .isEqualTo(expectedLineHeight);

            List<String> missingGlyphs = new ArrayList<String>();
            for (int i = 0; i < characters.length(); i++) {
                char c = characters.charAt(i);
                if (c == '\r' || c == '\n' || c == '\t') {
                    continue;
                }
                if (data.getGlyph(c) == null) {
                    missingGlyphs.add(String.format("U+%04X", (int) c));
                }
            }
            assertThat(missingGlyphs).as("%s FreeType 逐字生成缺失", label).isEmpty();

            for (char c : Assets.REQUIRED_FONT_CHARS.toCharArray()) {
                BitmapFont.Glyph glyph = data.getGlyph(c);
                assertThat(glyph.width).as("%s %s 墨水宽", label, c).isGreaterThan(0);
                assertThat(glyph.height).as("%s %s 墨水高", label, c).isGreaterThan(0);
            }
            assertThat(data.getGlyph('崽').xadvance).as("%s CJK 全角步进", label)
                    .isEqualTo(role.rasterSize());
            assertThat(packer.getPages().size).as("%s 1024² 打包页数（控制纹理体积）", label)
                    .isLessThanOrEqualTo(1);
        } finally {
            packer.dispose();
            generator.dispose();
        }
    }

    /** 标题运行期字符集 = ASCII + 回归字 + FontRole.TITLE 的专属标题字（与 Assets 同口径，不含全文库） */
    private static String titleCharacters(String charset) {
        StringBuilder chars = new StringBuilder(256);
        for (char c = 32; c <= 126; c++) {
            chars.append(c);
        }
        appendUnique(chars, Assets.REQUIRED_FONT_CHARS);
        appendUnique(chars, Assets.FontRole.TITLE.extraCharacters());
        return chars.toString();
    }

    private static void appendUnique(StringBuilder chars, String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\r' || c == '\n' || c == '\t' || c == '\uFEFF') {
                continue;
            }
            if (chars.indexOf(String.valueOf(c)) < 0) {
                chars.append(c);
            }
        }
    }

    private static void collectDataChars(String charset, List<String> missing) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(DATA_DIR, "*.json")) {
            for (Path path : stream) {
                String text = new String(Files.readAllBytes(path), StandardCharsets.UTF_8);
                for (int i = 0; i < text.length(); ) {
                    int cp = text.codePointAt(i);
                    i += Character.charCount(cp);
                    if (cp <= 0x7F || Character.isWhitespace(cp)) {
                        continue;
                    }
                    if (cp > 0xFFFF) {
                        missing.add(path.getFileName() + ":U+" + Integer.toHexString(cp) + "(非 BMP)");
                    } else if (charset.indexOf(cp) < 0) {
                        missing.add(path.getFileName() + ":U+" + Integer.toHexString(cp));
                    }
                }
            }
        }
    }
}
