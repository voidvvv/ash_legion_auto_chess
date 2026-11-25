import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.font.FontRenderContext;
import java.awt.font.GlyphVector;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 像素字体页栅格化工具（品质改造第二阶段 · 主人裁定：字体缺项用栅格化生成新字库页）。
 *
 * <p>输入：项目既有 fusion_pixel_12.fnt（AngelCode 文本格式——度量契约：字符集/矩形/步进
 * 全部沿用，游戏排版数学零漂移）+ 上游 TTF
 * （fusion-pixel-12px-proportional-zh_hans.ttf，release 2026.08.11，SIL OFL 1.1——
 * 许可原文见 assets/font/OFL-LICENSE.txt，同字体同版本，与 assets/font/README.md 记载一致）。
 *
 * <p>栅格化参数（与 README 记载的原生成口径一致）：12px、TEXT_ANTIALIAS OFF、
 * FRACTIONALMETRICS OFF（整数度量）；逐字形按 .fnt 记录矩形 (x,y,w,h) 落墨——
 * GlyphVector.getGlyphPixelBounds 取墨迹包围盒，平移到记录矩形原点绘制；
 * 页 1024×1024、白字透明底。产出：
 * <pre>
 *   assets/art/generated_v2/font/fusion_pixel_12.png  新页（每字形重栅格化）
 *   assets/art/generated_v2/font/fusion_pixel_12.fnt  .fnt 原样字节拷贝（页文件同基名同目录）
 * </pre>
 * 用法：java RasterizeFontPage &lt;ttf&gt; &lt;in.fnt&gt; &lt;outDir&gt; [&lt;旧页&gt;（可选，对账）]
 */
public final class RasterizeFontPage {

    public static void main(String[] args) throws Exception {
        if (args.length < 3) {
            System.err.println("用法: java RasterizeFontPage <ttf> <in.fnt> <outDir> [<旧页png>]");
            System.exit(2);
        }
        Path ttf = Paths.get(args[0]);
        Path fntIn = Paths.get(args[1]);
        Path outDir = Paths.get(args[2]);
        Path oldPage = args.length > 3 ? Paths.get(args[3]) : null;
        if (!Files.isRegularFile(ttf) || !Files.isRegularFile(fntIn)) {
            System.err.println("输入缺失: " + ttf + " / " + fntIn);
            System.exit(2);
        }
        Files.createDirectories(outDir);

        List<int[]> glyphs = parseChars(fntIn); // {id,x,y,w,h,xoffset,yoffset,xadvance}
        System.out.println("[font] .fnt 字形数: " + glyphs.size());

        int scaleW = 1024;
        int scaleH = 1024;
        BufferedImage page = new BufferedImage(scaleW, scaleH, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = page.createGraphics();
        g.setColor(Color.WHITE);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_SPEED);
        FontRenderContext frc = new FontRenderContext(null, false, false);
        Font font = Font.createFont(Font.TRUETYPE_FONT, ttf.toFile()).deriveFont(12f);

        int rendered = 0;
        int exact = 0;
        int sizeMismatch = 0;
        int emptyInFont = 0;
        Map<String, Integer> mismatchSample = new LinkedHashMap<String, Integer>();
        for (int[] e : glyphs) {
            int id = e[0];
            if (e[3] == 0 || e[4] == 0) {
                continue; // 空白/零墨迹字形（如空格）：页上无墨
            }
            String ch = new String(Character.toChars(id));
            GlyphVector gv = font.createGlyphVector(frc, ch);
            if (gv.getNumGlyphs() == 0) {
                emptyInFont++;
                continue;
            }
            java.awt.Rectangle ink = gv.getGlyphPixelBounds(0, frc, 0f, 0f);
            if (ink.isEmpty()) {
                emptyInFont++;
                continue;
            }
            if (ink.width != e[3] || ink.height != e[4]) {
                sizeMismatch++;
                if (mismatchSample.size() < 12) {
                    mismatchSample.put(String.format("U+%04X(%s) 记录%dx%d 实栅%dxd",
                            id, ch, e[3], e[4], ink.width, ink.height), 1);
                }
            } else {
                exact++;
            }
            // 越界防御：墨迹平移到记录矩形原点后必须整页在界
            if (e[1] < 0 || e[2] < 0 || e[1] + e[3] > scaleW || e[2] + e[4] > scaleH) {
                throw new IllegalStateException(String.format(
                        "字形 U+%04X 记录矩形越页: x=%d y=%d w=%d h=%d", id, e[1], e[2], e[3], e[4]));
            }
            g.drawGlyphVector(gv, e[1] - ink.x, e[2] - ink.y);
            rendered++;
        }
        g.dispose();

        Path outPng = outDir.resolve("fusion_pixel_12.png");
        Path outFnt = outDir.resolve("fusion_pixel_12.fnt");
        ImageIO.write(page, "png", outPng.toFile());
        Files.copy(fntIn, outFnt, StandardCopyOption.REPLACE_EXISTING);

        System.out.println("[font] 落墨字形: " + rendered + "（精确尺寸一致 " + exact
                + "，尺寸偏差 " + sizeMismatch + "，TTF 无墨/缺字形 " + emptyInFont + "）");
        for (String s : mismatchSample.keySet()) {
            System.out.println("[font]   尺寸偏差样例: " + s);
        }
        if (oldPage != null && Files.isRegularFile(oldPage)) {
            diffPages(oldPage, outPng);
        }
        System.out.println("[font] 产出: " + outPng + " + " + outFnt);
        if (emptyInFont > 31) { // README 记载 31 个字符不在 zh_hans 变体（假名/西里尔等）
            System.err.println("[font][警告] TTF 缺字形数超过 README 记载的 31：核对 TTF 版本");
        }
    }

    /** 解析 AngelCode 文本 .fnt 的 char 行（仅取几何/度量字段） */
    private static List<int[]> parseChars(Path fnt) throws IOException {
        List<int[]> out = new ArrayList<int[]>();
        for (String line : Files.readAllLines(fnt, StandardCharsets.UTF_8)) {
            if (!line.startsWith("char ")) {
                continue;
            }
            Map<String, Integer> kv = new LinkedHashMap<String, Integer>();
            for (String tok : line.split("\\s+")) {
                int eq = tok.indexOf('=');
                if (eq <= 0) {
                    continue;
                }
                try {
                    kv.put(tok.substring(0, eq), Integer.parseInt(tok.substring(eq + 1).trim()));
                } catch (NumberFormatException ignored) {
                    // chnl 之外的字符串字段不会出现在 char 行的数值位
                }
            }
            out.add(new int[]{kv.get("id"), kv.get("x"), kv.get("y"),
                    kv.get("width"), kv.get("height"), kv.get("xoffset"),
                    kv.get("yoffset"), kv.get("xadvance")});
        }
        return out;
    }

    /** 与旧页对账（信息性输出：有墨像素一致率——同字体同参数应高度一致） */
    private static void diffPages(Path oldPng, Path newPng) throws IOException {
        BufferedImage a = ImageIO.read(oldPng.toFile());
        BufferedImage b = ImageIO.read(newPng.toFile());
        if (a.getWidth() != b.getWidth() || a.getHeight() != b.getHeight()) {
            System.out.println("[font][对账] 页尺寸不同，跳过逐像素对账");
            return;
        }
        long oldInk = 0;
        long sameInk = 0;
        long newInk = 0;
        for (int y = 0; y < a.getHeight(); y++) {
            for (int x = 0; x < a.getWidth(); x++) {
                boolean ia = (a.getRGB(x, y) >>> 24) != 0;
                boolean ib = (b.getRGB(x, y) >>> 24) != 0;
                if (ia) {
                    oldInk++;
                }
                if (ib) {
                    newInk++;
                }
                if (ia && ib) {
                    sameInk++;
                }
            }
        }
        System.out.println(String.format(
                "[font][对账] 旧页有墨 %d px / 新页有墨 %d px / 重合 %d px（重合率 %.2f%%，信息性）",
                oldInk, newInk, sameInk, oldInk == 0 ? 0d : 100d * sameInk / oldInk));
    }

    private RasterizeFontPage() {
    }
}
