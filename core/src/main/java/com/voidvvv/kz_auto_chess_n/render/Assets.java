package com.voidvvv.kz_auto_chess_n.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.audio.Music;
import com.badlogic.gdx.audio.Sound;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 资源门面（render §7.6 注入式；只允许出现在 render/ 与 screens/，禁静态持有）。
 *
 * <p>region 逐 key 兜底：先真素材层（Q4=B：art/units/ 逐帧 PNG，miss 回退）、未命中落占位，
 * 仍缺直接抛错（fail-fast，不静默给错图）。font 换载 Fusion Pixel 12px 位图字体
 * （文件缺失回退内置默认——headless 测试不依赖素材存在，Q4 守卫沿用）。
 * skin/sound 推 Phase 5/7（差异声明 #4）。
 *
 * <p><b>UI 素材出口</b>（UI 抽屉化批0）：{@link #uiRegion}/{@link #uiPatch} 走独立真素材层
 * {@link UiArt}（art/ui/，键集与切分线见 {@link UiKeys}），查找链与 {@link #region} 一致：
 * 真素材 → 占位 → 仍缺抛错。
 *
 * <p><b>FX 软路径</b>（2026-11-21 亡语 FX 缺口回归加固）：装饰性特效键走 {@link #regionFx}——
 * 缺键软告警（每键一次，与 JsonLoader 软告警同口径）+ 降级通用爆点/白片，<b>装饰性贴图永不崩游戏</b>；
 * 结构性键（单位帧/面板/白片）仍走 {@link #region} 硬失败——缺结构图属程序错误必须即死。
 *
 * <p><b>音效出口</b>（品质改造 §1.2）：{@link AudioScheduler.Cue} → {@code audio/*.ogg} 由本类
 * 持有并 dispose（{@code Gdx.audio.newSound}，本类此前无 Sound 先例——新建出口）；缺文件该 cue
 * 静音 + 一次日志，其余 cue 与渲染正常构建。BGM 走 Music 接口，开关默认关闭（§1.2）。
 */
public final class Assets {
    /** 真素材层字体文件（Hiero 生成 .fnt + .png；缺文件回退内置默认） */
    static final String FONT_PATH = "font/fusion_pixel_12.fnt";

    private final PlaceholderArt art;
    /** 真素材层（Q4=B：itch 小人；miss 自动回退占位） */
    private final RealArt realArt = new RealArt();
    /** UI 真素材层（UI 抽屉化批0：art/ui/ 框架件；miss 自动回退占位） */
    private final UiArt uiArt = new UiArt();
    /** UI 九宫格缓存（id → NinePatch；轻量对象，随 Assets 生命周期） */
    private final Map<String, NinePatch> uiPatches = new HashMap<String, NinePatch>();
    private BitmapFont font;
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
        TextureRegion real = realArt.region(key);
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
     * FX 软路径（装饰性特效专用）：真素材 → 占位 → 仍缺则软告警（每键一次）并降级通用爆点
     * （再缺降白片）——特效缺图跳过视觉不中断对局；键集完备性由 FxKeyCoverageTest 在 JVM 层锁定。
     */
    public TextureRegion regionFx(String key) {
        TextureRegion real = realArt.region(key);
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
        TextureRegion real = uiArt.region(id);
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

    /** Fusion Pixel 12px 位图字体（Q4=B；文件缺失回退内置默认——headless 测试不依赖素材存在） */
    public BitmapFont font() {
        if (font == null) {
            FileHandle fontFile = Gdx.files.internal(FONT_PATH);
            font = fontFile.exists() ? new BitmapFont(fontFile, false) : new BitmapFont();
        }
        return font;
    }

    /** 主菜单重建时复用同一张纹理，由 Assets 统一释放。 */
    public Texture mainMenuBackground() {
        if (mainMenuBackground == null) {
            mainMenuBackground = new Texture(Gdx.files.internal("art/ui/main_menu_background.png"));
            mainMenuBackground.setFilter(Texture.TextureFilter.Nearest, Texture.TextureFilter.Nearest);
        }
        return mainMenuBackground;
    }

    /** Main.dispose 调：字体、音效/BGM、主菜单背景、真素材（单位/UI）与占位 Texture 全弃 */
    public void dispose() {
        if (font != null) {
            font.dispose();
            font = null;
        }
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
        realArt.dispose();
        uiArt.dispose();
        uiPatches.clear(); // NinePatch 自身无 GL 资源，仅持已弃 Texture 的引用——随层清空
        art.dispose();
    }
}
