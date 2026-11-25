package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator.FreeTypeFontParameter;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 资源门面（render §7.6 注入式；只允许出现在 render/ 与 screens/，禁静态持有）。
 *
 * <p>region 逐 key 兜底：先真素材层（品质改造第二阶段：{@link GeneratedArt} 按
 * {@code art/generated_v2/manifest.json} 读原创像素素材，miss 回退）、未命中落占位，
 * 仍缺直接抛错（fail-fast，不静默给错图）。
 *
 * <p><b>三段字体角色</b>（2026-11-25 主人新样式要求）：标题 = 中文黑体
 * ({@code font/ember-legion-hei.ttf}，思源黑体 CN Medium 子集)、游戏内容/描述 = 楷体
 * ({@code font/ember-legion-kai.ttf}，霞鹜文楷 Regular 子集)、UI 标签/数值/按钮 = 宋体
 * ({@code font/ember-legion-song.ttf}，思源宋体 CN Regular 子集)。三套 TTF 均为 SIL OFL 1.1
 * 子集，字符集见 {@code font/charset.txt}。各自按 2× 物理像素栅格化（默认窗口 1280×720
 * = 640×360 × 2），再以 {@link #FONT_LOGICAL_SCALE} 收回虚拟 UI 尺寸——矢量字形在默认窗口
 * 1:1 像素映射下清晰、不靠 mono/Nearest 硬边。TTF 缺失才逐角色回退内置位图字体。
 *
 * <p><b>UI 素材出口</b>（UI 抽屉化批0）：{@link #uiRegion}/{@link #uiPatch} 与 {@link #region}
 * 同源查 {@link GeneratedArt}（键集与切分线见 {@link UiKeys}），查找链一致：
 * 真素材 → 占位 → 仍缺抛错。
 *
 * <p><b>FX 软路径</b>（2026-11-21 亡语 FX 缺口回归加固）：装饰性特效键走 {@link #regionFx}——
 * 缺键软告警（每键一次，与 JsonLoader 软告警同口径）+ 降级通用爆点/白片，<b>装饰性贴图永不崩游戏</b>；
 * 结构性键（单位帧/面板/白片）仍走 {@link #region} 硬失败——缺结构图属程序错误必须即死。
 *
 * <p><b>真彩图判定</b>：{@link #isGeneratedArt} 供绘制层关闭占位时代的星级 tint
 * （占位单位为白体靠 tint 分星级；原创单位图自带颜色，tint 会偏色——星级仍由脚下星级点表达）。
 *
 * <p><b>音效出口</b>（品质改造 §1.2）：{@link AudioScheduler.Cue} → {@code audio/*.ogg} 由本类
 * 持有并 dispose（{@code Gdx.audio.newSound}）；缺文件该 cue 静音 + 一次日志。BGM 走 Music 接口，
 * 开关默认关闭（§1.2）。
 */
public final class Assets {

    /** 三段字体角色：标题（黑体）/ 正文描述（楷体）/ UI 标签数值按钮（宋体）。 */
    public enum FontRole {
        /** 标题：思源黑体 CN Medium 子集，2× 栅格 40px → 逻辑 20px。 */
        TITLE("font/ember-legion-hei.ttf", 40, 20f,
                "余烬军团远征准备图鉴测试样片暂停胜利战败超时对拍完成远征已放弃失败通关宝箱补给"),
        /** 正文/棋子技能与剧情描述：霞鹜文楷 Regular 子集，2× 栅格 17px → 逻辑 8.5px。 */
        BODY("font/ember-legion-kai.ttf", 17, 8.5f, null),
        /** UI 标签/数值/按钮/页签：思源宋体 CN Regular 子集，2× 栅格 20px → 逻辑 10px。 */
        UI("font/ember-legion-song.ttf", 20, 10f, null);

        private final String path;
        private final int rasterSize;
        private final float logicalSize;
        /** 角色专属额外字符（标题字少，独立小字符集把 40px 栅格页控制在 1 页） */
        private final String extraCharacters;

        FontRole(String path, int rasterSize, float logicalSize, String extraCharacters) {
            this.path = path;
            this.rasterSize = rasterSize;
            this.logicalSize = logicalSize;
            this.extraCharacters = extraCharacters;
        }

        public String path() {
            return path;
        }

        public int rasterSize() {
            return rasterSize;
        }

        public float logicalSize() {
            return logicalSize;
        }

        String extraCharacters() {
            return extraCharacters;
        }
    }

    /** 运行期字符集（UTF-8 单行；ASCII + 游戏 data/主源码字符串实际用字，含 崽/饷/薇） */
    public static final String FONT_CHARSET_PATH = "font/charset.txt";
    /** 2× 物理栅格 → 虚拟 UI 尺寸的收缩系数（默认窗口 1280×720 = 640×360 × 2） */
    public static final float FONT_LOGICAL_SCALE = 0.5f;
    /** 标题逻辑字号（虚拟 UI px；原 2×12=24 → 20） */
    public static final float TITLE_FONT_SIZE = 20f;
    /** UI 标签/数值/按钮逻辑字号（原 12 → 10） */
    public static final float UI_FONT_SIZE = 10f;
    /** 正文/技能/剧情描述逻辑字号（原 12×0.75=9 → 8.5） */
    public static final float BODY_FONT_SIZE = 8.5f;
    /** 三套字体的实测行高（2× 栅格行高 × {@link #FONT_LOGICAL_SCALE}，FontCoverageTest 锁定） */
    public static final float TITLE_LINE_HEIGHT = 29f;
    public static final float UI_LINE_HEIGHT = 14.5f;
    public static final float BODY_LINE_HEIGHT = 10f;
    /** FreeType 字形打包页上限（1024² RGBA；避免 >1024 单页在移动端超纹理上限） */
    public static final int FONT_PAGE_SIZE = 1024;
    /** 必须覆盖的回归字（charset 文件缺失/过期时的兜底；三字均在游戏 data 中出现） */
    static final String REQUIRED_FONT_CHARS = "崽饷薇";
    /** 主菜单背景（原创 640×360；交付清单 background 类） */
    static final String MAIN_MENU_BACKGROUND_PATH = "art/generated_v2/background/main_menu_background.png";

    private final PlaceholderArt art;
    /** 真素材层（品质改造第二阶段：generated_v2 原创像素素材，manifest 驱动） */
    private final GeneratedArt generatedArt = GeneratedArt.loadDefault();
    /** UI 九宫格缓存（id → NinePatch；轻量对象，随 Assets 生命周期） */
    private final Map<String, NinePatch> uiPatches = new HashMap<String, NinePatch>();
    /** 三段字体，按角色惰性生成、由本类统一释放（各 1 张 1024² 页，见 FontCoverageTest） */
    private final Map<FontRole, BitmapFont> fonts = new EnumMap<FontRole, BitmapFont>(FontRole.class);
    private Texture mainMenuBackground;
    /** FX 软告警去重（每键一次） */
    private final Set<String> warnedFxKeys = new HashSet<String>();
    /** 音效池（cue → Sound；本类持有并 dispose——品质改造 §1.2） */
    private final Map<AudioScheduler.Cue, Sound> sounds =
            new HashMap<AudioScheduler.Cue, Sound>();
    /** 缺素材静音去重（每 cue 一次日志） */
    private final Set<AudioScheduler.Cue> missingCues = new HashSet<AudioScheduler.Cue>();
    /** BGM（Music 接口；开关默认关闭——§1.2） */
    private Music bgm;
    private boolean bgmStarted;

    public Assets(PlaceholderArt art) {
        this.art = Objects.requireNonNull(art, "art 不能为 null");
    }

    /** 真素材优先、未命中落占位；仍缺抛 IllegalStateException（= 程序错误；结构性键专用，FX 走 regionFx） */
    public TextureRegion region(String key) {
        TextureRegion real = generatedArt.region(key);
        if (real != null) {
            return real;
        }
        TextureRegion region = art.region(key);
        if (region == null) {
            throw new IllegalStateException("资源缺 key: " + key);
        }
        return region;
    }

    /**
     * 真素材软查询（原创新增键专用：棋盘框/地砖、头像、装备与羁绊图标等占位层<b>不生成</b>的键）：
     * 命中真素材返回 region，未命中返回 null（调用方自决兜底画法），不抛错不告警。
     */
    public TextureRegion regionOrNull(String key) {
        return generatedArt.region(key);
    }

    /** 该 key 是否已解析为原创真素材（真彩图判定：不做星级 tint；缓存后零磁盘） */
    public boolean isGeneratedArt(String key) {
        return generatedArt.hasArt(key);
    }

    /**
     * FX 软路径（装饰性特效专用）：真素材 → 占位 → 仍缺则软告警（每键一次）并降级通用爆点
     * （再缺降白片）——特效缺图跳过视觉不中断对局；键集完备性由 FxKeyCoverageTest 在 JVM 层锁定。
     */
    public TextureRegion regionFx(String key) {
        TextureRegion real = generatedArt.region(key);
        if (real != null) {
            return real;
        }
        TextureRegion region = art.region(key);
        if (region != null) {
            return region;
        }
        if (warnedFxKeys.add(key)) {
            System.err.println("[Assets][软告警] FX 缺 key: " + key + " —— 降级通用爆点（装饰性贴图不崩局）");
        }
        TextureRegion fallback = art.region(PlaceholderKeys.HIT_DEFAULT);
        return fallback != null ? fallback : art.region(PlaceholderKeys.WHITE);
    }

    /** UI 件（整图出口）：真素材 → 占位 → 仍缺抛 IllegalStateException（键集见 {@link UiKeys}） */
    public TextureRegion uiRegion(String id) {
        TextureRegion real = generatedArt.region(id);
        if (real != null) {
            return real;
        }
        TextureRegion region = art.region(id);
        if (region == null) {
            throw new IllegalStateException("UI 资源缺 key: " + id);
        }
        return region;
    }

    /**
     * UI 九宫格出口：按 {@link UiKeys} 切分线把 {@link #uiRegion} 包成 NinePatch（按 id 缓存，渲染段零分配）。
     * 未知 id 直接抛错（切分线无事实源 = 程序错误，fail-fast）。
     */
    public NinePatch uiPatch(String id) {
        NinePatch cached = uiPatches.get(id);
        if (cached != null) {
            return cached;
        }
        UiKeys.Entry entry = UiKeys.find(id);
        if (entry == null) {
            throw new IllegalStateException("UI 九宫格未知 key（切分线须登记在 UiKeys）: " + id);
        }
        NinePatch patch = new NinePatch(uiRegion(id), entry.left(), entry.right(), entry.top(), entry.bottom());
        uiPatches.put(id, patch);
        return patch;
    }

    // —— 音效（品质改造 §1.2） ——

    /** cue → 素材文件（assets/audio/ 六件，Ogg Vorbis；§1.2 表） */
    private static String audioFile(AudioScheduler.Cue cue) {
        switch (cue) {
            case VICTORY: return "audio/sfx_victory.ogg";
            case DEFEAT: return "audio/sfx_defeat.ogg";
            case CAST: return "audio/sfx_cast.ogg";
            case KILL: return "audio/sfx_kill.ogg";
            case UPGRADE: return "audio/sfx_upgrade.ogg";
            default: return "audio/sfx_hit.ogg";
        }
    }

    private Sound sound(AudioScheduler.Cue cue) {
        Sound loaded = sounds.get(cue);
        if (loaded != null) {
            return loaded;
        }
        if (missingCues.contains(cue)) {
            return null; // 缺素材静音（每 cue 一次日志）
        }
        FileHandle file = Gdx.files.internal(audioFile(cue));
        if (!file.exists()) {
            missingCues.add(cue);
            System.err.println("[Assets][软告警] 音效缺文件（该 cue 静音）: " + audioFile(cue));
            return null;
        }
        Sound sound = Gdx.audio.newSound(file);
        sounds.put(cue, sound);
        return sound;
    }

    /** 音效播放出口（品质改造 §1.2：Assets 持有 Sound 并 dispose，屏只管播放句柄） */
    public final AudioScheduler.CuePlayer audioPlayer = new AudioScheduler.CuePlayer() {
        @Override
        public long play(AudioScheduler.Cue cue, float volume) {
            Sound sound = sound(cue);
            return sound == null ? -1L : sound.play(volume);
        }

        @Override
        public void stop(long playId) {
            // playId = -1（缺素材静音路径）不会进入调度（AudioScheduler 只对已播声道 stop）
        }
    };

    /** BGM 接线（品质改造 §1.2：Music 接口 + 开关默认关闭——codex 下批定播放方式） */
    public void playBgmIfEnabled() {
        if (!com.voidvvv.kz_auto_chess_n.config.GameBalance.BGM_ENABLED || bgmStarted) {
            return;
        }
        FileHandle file = Gdx.files.internal("audio/bgm_battle.ogg");
        if (!file.exists()) {
            System.err.println("[Assets][软告警] BGM 缺文件: audio/bgm_battle.ogg");
            bgmStarted = true;
            return;
        }
        bgm = Gdx.audio.newMusic(file);
        bgm.setLooping(true);
        bgm.play();
        bgmStarted = true;
    }

    /** 标题字体（黑体；逻辑 20px，2× 栅格 40px）。 */
    public BitmapFont titleFont() {
        return font(FontRole.TITLE);
    }

    /** 正文字体（楷体；逻辑 8.5px，2× 栅格 17px）——棋子/技能/剧情描述、悬停卡、详情页。 */
    public BitmapFont bodyFont() {
        return font(FontRole.BODY);
    }

    /** UI 字体（宋体；逻辑 10px，2× 栅格 20px）——标签/数值/按钮/页签/标题板小字。 */
    public BitmapFont uiFont() {
        return font(FontRole.UI);
    }

    /**
     * 角色字体惰性生成（运行期 FreeType；SIL OFL 1.1 子集 TTF）。
     *
     * <p><b>栅格参数按角色分化</b>（不机械套像素字体的 mono/Nearest）：
     * <ul>
     *   <li>字面：{@code mono=false}——三款均为矢量字形，保留灰度抗锯齿；</li>
     *   <li>2× 栅格：{@link FontRole#rasterSize()} = 逻辑尺寸 × 2，默认 1280×720 窗口下
     *       1 栅格像素 = 1 屏幕像素；字体数据以 {@link #FONT_LOGICAL_SCALE} 收回虚拟尺寸；</li>
     *   <li>hinting：标题/UI 用 {@code AutoMedium}（小字号笔画清晰），正文楷体用
     *       {@code AutoSlight}（保留楷书笔锋，避免过强吸附）；</li>
     *   <li>过滤：页纹理 Linear（矢量字形不套 Nearest；非整数窗口缩放也不闪），
     *       {@code genMipMaps=false}，关闭整数定位以保留亚像素字距；</li>
     *   <li>字距：{@code kerning=false}（CJK 步进稳定；既有排版仍按等宽全角估算，关闭 kerning 不引入意外宽度）。</li>
     * </ul>
     *
     * <p><b>打包/释放</b>：内置 {@code PixmapPacker}，页上限 {@link #FONT_PAGE_SIZE}²；
     * 每角色 1 页（字符集 865 字；标题用独立小字符集）。生成器生成完立即 dispose；
     * 字体随 {@link #dispose()} 释放（页纹理由 BitmapFont 拥有并逐页释放）。
     * TTF 缺失才逐角色回退内置位图字体（headless 测试不依赖素材存在）。
     */
    public BitmapFont font(FontRole role) {
        BitmapFont cached = fonts.get(role);
        if (cached != null) {
            return cached;
        }
        FileHandle fontFile = Gdx.files.internal(role.path());
        if (!fontFile.exists()) {
            System.err.println("[Assets][软告警] TTF 字体缺文件，角色 " + role + " 回退内置位图字体: " + role.path());
            cached = fallbackFont();
            fonts.put(role, cached);
            return cached;
        }
        FreeTypeFontGenerator generator = null;
        try {
            generator = new FreeTypeFontGenerator(fontFile);
            FreeTypeFontParameter parameter = new FreeTypeFontParameter();
            parameter.size = role.rasterSize();
            parameter.characters = role == FontRole.TITLE
                    ? loadCharacters(role.extraCharacters()) : loadFontCharacters();
            parameter.kerning = false;
            parameter.mono = false; // 矢量字形保留灰度抗锯齿（像素字体时代的 1-bit 硬边不适用）
            parameter.hinting = role == FontRole.BODY
                    ? FreeTypeFontGenerator.Hinting.AutoSlight
                    : FreeTypeFontGenerator.Hinting.AutoMedium;
            parameter.genMipMaps = false;
            parameter.minFilter = Texture.TextureFilter.Linear;
            parameter.magFilter = Texture.TextureFilter.Linear;
            parameter.spaceY = 0;
            FreeTypeFontGenerator.setMaxTextureSize(FONT_PAGE_SIZE);
            cached = generator.generateFont(parameter);
        } finally {
            if (generator != null) {
                generator.dispose(); // 非增量生成：字形已全部烘焙，生成器可立即释放
            }
        }
        cached.getData().setScale(FONT_LOGICAL_SCALE);
        cached.setUseIntegerPositions(false); // 亚像素字距：矢量字形不吸附整像素
        fonts.put(role, cached);
        return cached;
    }

    /** 缺 TTF 的 headless/缺资源兜底：内置位图字体按同一逻辑收缩系数缩放。 */
    private static BitmapFont fallbackFont() {
        BitmapFont bitmapFont = new BitmapFont();
        bitmapFont.getData().setScale(FONT_LOGICAL_SCALE);
        bitmapFont.setUseIntegerPositions(false);
        return bitmapFont;
    }

    /**
     * 运行期字符集：ASCII 可打印字符 + {@code font/charset.txt}（assets/data 与 core 主源码
     * 字符串实际用字）+ {@link #REQUIRED_FONT_CHARS} 兜底。
     * 文件按码点升序、单行 UTF-8 存放；过滤换行/制表符与 BOM，避免生成无意义字形。
     */
    private static String loadFontCharacters() {
        return loadCharacters(null);
    }

    /** 字符集 = ASCII + required + 可选角色额外字 + charset.txt（按 char 去重保序）。 */
    private static String loadCharacters(String extraCharacters) {
        StringBuilder chars = new StringBuilder(2048);
        for (char c = 32; c <= 126; c++) {
            chars.append(c);
        }
        for (int i = 0; i < REQUIRED_FONT_CHARS.length(); i++) {
            appendUnique(chars, REQUIRED_FONT_CHARS.charAt(i));
        }
        if (extraCharacters != null) {
            // 标题角色：独立小字符集（40px 栅格把 1024² 页压在 1 页；标题是有限的屏名/终局名集合）
            for (int i = 0; i < extraCharacters.length(); i++) {
                appendUnique(chars, extraCharacters.charAt(i));
            }
            return chars.toString();
        }
        FileHandle charsetFile = Gdx.files.internal(FONT_CHARSET_PATH);
        if (charsetFile.exists()) {
            String stored = charsetFile.readString("UTF-8");
            for (int i = 0; i < stored.length(); i++) {
                char c = stored.charAt(i);
                if (c == '\r' || c == '\n' || c == '\t' || c == '\uFEFF') {
                    continue;
                }
                appendUnique(chars, c);
            }
        } else {
            System.err.println("[Assets][软告警] 字体字符集缺文件，仅 ASCII + 回归字: " + FONT_CHARSET_PATH);
        }
        return chars.toString();
    }

    private static void appendUnique(StringBuilder chars, char c) {
        if (chars.indexOf(String.valueOf(c)) < 0) {
            chars.append(c);
        }
    }

    /** 主菜单重建时复用同一张纹理，由 Assets 统一释放。 */
    public Texture mainMenuBackground() {
        if (mainMenuBackground == null) {
            mainMenuBackground = new Texture(Gdx.files.internal(MAIN_MENU_BACKGROUND_PATH));
            mainMenuBackground.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        return mainMenuBackground;
    }

    /** Main.dispose 调：字体、音效/BGM、主菜单背景、真素材（generated_v2）与占位 Texture 全弃 */
    public void dispose() {
        for (BitmapFont bitmapFont : fonts.values()) {
            bitmapFont.dispose();
        }
        fonts.clear();
        for (Sound sound : sounds.values()) {
            sound.dispose();
        }
        sounds.clear();
        if (bgm != null) {
            bgm.dispose();
            bgm = null;
        }
        if (mainMenuBackground != null) {
            mainMenuBackground.dispose();
            mainMenuBackground = null;
        }
        generatedArt.dispose();
        uiPatches.clear(); // NinePatch 自身无 GL 资源，仅持已弃 Texture 的引用——随层清空
        art.dispose();
    }
}
