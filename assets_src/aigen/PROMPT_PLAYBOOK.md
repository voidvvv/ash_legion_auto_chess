# AI 精灵素材提示词手册 v1（兽人试点）

> 日期：2026-11-22 ｜ 适用：Codex 本地图像生成（gpt-image 系；模型名/参数以本地工具实际暴露为准）
> 目标：为 `kz_auto_chess_n` 生成 **32×32、6 档灰 + 星级 tint、种族身体 × 职业武器剪影** 的棋子精灵；先做兽人 6 子，验证后再铺全量。
> 结论先说：**AI 负责“画对剪影和姿态”，后处理负责“变成 32×32 硬像素”**。不要把 32×32、6 色、透明 alpha 这些硬指标交给模型；模型做不到，必须在我们的管线里完成。

---

## 0. 十条铁律（先看这个）

1. **一张图只画一个精灵**；不要一次生成 12 帧整版（模型会漂移、格子对不齐）。12 帧用“基图 + 逐帧编辑”。
2. **生成 1024×1024，最近邻降到 32×32**；永远不要向上放大。模型画不出真正的 32×32，它画的是“像素风格的 1024 图”（mixels）[4][5][6]。
3. **透明背景优先用 API 参数**：`background="transparent"` + `output_format="png"`；只能给提示词时，写“透明背景”，失败就退回纯色 chroma `#FF00FF` 后处理抠掉[2][4][8]。
4. **固定风格块逐字复用**：同一批所有单位共用同一段 style/约束，只改主体描述；不要每条换形容词[4][6]。
5. **先做风格锚**：先生成 `warrior_01` 作为兽人锚图，之后 5 个兽人用它做参考图（Image 1）+ 同一风格块；这是没有 seed 时最稳的一致性手段[4][6][7]。
6. **编辑只改一处**：每帧提示词 = “保持 X/Y/Z 不变，只改 A”；每次迭代重复 invariants；不要一次改十个地方[1]。
7. **不要依赖 seed**：gpt-image 的 Images API 参考里没有公开 seed 参数；若本地工具暴露 seed 就固定，否则用参考图 + 固定风格块 + 4 选 1[3]。
8. **walk 最难**：纯图像生成很难出好走路；优先用 image-to-video 取 2 帧，退而求其次用“夸张步幅”的两张编辑图（±20% 体宽），不要指望 1px 级变化[6]。
9. **先生成彩色低饱和，再程序化去色**：彩色帮助模型区分材质/武器；最后由我们的 6 档灰 LUT + 星级 tint 统一。直接要求“纯灰度”模型经常偷偷加色[4][5]。
10. **后处理是资产化的主战场**：alpha 抠图、按 alpha bbox 裁切、最近邻缩放、内容 22px、脚底 26、6 档灰、量化、命名/manifest/SHA，全部由现有管线做[4][5][6]。

---

## 1. 提示词结构最佳实践

### 1.1 推荐顺序（OpenAI Cookbook 的“结构 + 目的”）

按固定顺序写，复杂请求用短标签分行，不要一大段散文[1]：

```text
Intended use:      用途（32×32 棋盘单位 / 单帧 idle / 游戏精灵）
Background/scene:  透明或纯色 chroma；无场景
Subject:           种族 + 职业 + 体型 + 装备 + 武器
Key details:       剪影、材质、颜色、朝向、姿态
Framing:           全身、居中、内容约占 70% 高、脚底居中
Constraints:       不变项 + 排除项（no ...）
Avoid:             抗锯齿/渐变/文字/边框/多余角色/阴影/地面
```

**为什么**：先把“用途/背景”定下来，模型会进入“资产模式”而不是“插画模式”；约束单独成段比夹在描述里有效[1]。

### 1.2 风格锚定关键词（挑一组，逐字复用）

| 目的 | 推荐写法 | 备注 |
|---|---|---|
| 像素风 | `pixel-art game sprite`, `16-bit`, `chunky pixels`, `crisp pixel edges` | 不要只写 `pixel art`，模型会画成“像素化插画”[5] |
| 硬边 | `hard 1px dark outline`, `no anti-aliasing`, `no blur`, `no soft edges` | 关键：AI 默认会抗锯齿[5] |
| 平涂 | `flat 3-tone shading`, `solid color blocks`, `no gradients`, `no dithering` | 防止渐变/写实光影[5] |
| 剪影 | `bold silhouette readable at 32x32`, `simple oversized weapon shapes` | 这句对“小尺寸可读”最有效[4] |
| 视角 | `three-quarter front view facing slightly right` | 与现有棋子正面/3-4 视角一致；敌方由代码 flipX |
| 调色 | `limited palette`, 具体色名（olive-green skin / bone-white tusks / dull iron） | 比“colorful”可控[5] |
| 留白 | `centered`, `full body`, `even padding`, `about 70% of canvas height` | 后处理还要按 alpha bbox 再裁[4][6] |

### 1.3 负面提示

- **OpenAI Images API 没有独立的 negative prompt 字段**；把排除项写成 `Avoid: ...` 或 `Constraints: no ...`[1][3]。
- 只有 Stable Diffusion / Flux / 部分本地工具才有 `negative_prompt` 字段；用那类工具时再填：
  `blurry, anti-aliased, smooth, gradient, realistic, 3d render, photorealistic, soft edges, text, watermark, border, frame, extra characters, scenery, shadow`[5]。
- 不要写“remove the background”：社区反馈它会把主体内部的白色区域也抠掉；改成正向描述“render only the subject, transparent pixels everywhere else”[8]。

### 1.4 单精灵 vs 拼版

| 方案 | 适用 | 风险 |
|---|---|---|
| **单精灵单张（推荐）** | idle/attack/cast/death 的逐帧编辑；最终 12 个独立 PNG | 每帧一次调用，成本高；但一致性最好 |
| 5×2 / 4×3 拼版 | 概念探索、一次看多姿态 | 格子漂移、角色在格内偏移、风格漂移；必须按 alpha bbox 重切/归一化，不能信名义网格[6] |
| 整张 12 帧 sheet | 不推荐给本管线 | 12 帧太小，模型会把帧画糊；切帧/对齐成本高于收益[6] |

**本项目的 12 帧是 12 个独立文件**，所以按单精灵 + 编辑走；拼版只用于“看姿势想法”。

---

## 2. 画幅、留白、透明、尺寸

### 2.1 模型侧尺寸

- gpt-image-1/1.5 的 API 参考目前只列 **1024×1024、1536×1024、1024×1536**（或 auto）；没有 32×32 输出，必须客户端缩放。模型/尺寸以 Codex 本地工具实际暴露为准[2][3]。
- 选 **1024×1024**，方形最稳；1536×1024 适合“骑乘/宽剪影”，但我们的目标格子是方形，1024 更省事。
- `quality` 用 `medium` 起，透明/细节不够再 `high`；透明输出在 medium/high 更稳[2]。
- 编辑（`/images/edits`）支持多张参考图（gpt-image-1 系最多 16 张）；若版本暴露 `input_fidelity`，设 `"high"` 更能保住角色/武器；第一张图权重最高[3]。

### 2.2 透明背景

**优先级**：

1. 工具暴露参数：`background="transparent"`、`output_format="png"`、`quality="medium"` 或 `"high"`[2][3]。
2. 只能写提示词：在 prompt 里写 `transparent background`，并加 `no shadow, no ground, no scenery`。**注意**：只支持 prompt 的模型可能把“透明”理解成“画一个灰白棋盘格”；如果工具不能原生输出 alpha，直接走第 3 条 chroma，不要写 checkerboard[4][8]。
3. 仍不透明：改用 **纯色 chroma**（`#FF00FF` 洋红或 `#00FF00` 绿），后处理按颜色键抠掉；比“让模型自己透明”更可控[4][6]。
4. 编辑模式透明不如生成稳：社区反馈 edits 有时会返回“假透明”棋盘格；每次编辑都重申 transparent，或改用生成 + 后处理[8]。

**不要**：写 `remove the background`（会误删主体内部白色）、要求 `checkerboard background`（模型会真的画棋盘格）[4][8]。

### 2.3 留白/构图

- `single character, full body, centered, feet at bottom-center, about 70% of canvas height, even padding`。
- 不要贴边：后处理要按 alpha bbox 裁切，再缩到内容 22px；模型侧留白越多，裁切越稳[4][6]。
- 骑乘单位（野猪骑士）额外写：`compact mount, low and small, rider remains upright, entire silhouette fits one square cell, no oversized mount`；否则 32px 下会横向溢出。

### 2.4 参数模板（如果本地工具走 OpenAI Images API / 兼容接口）

```json
{
  "model": "gpt-image-1",
  "prompt": "<粘贴 §6 的完整 prompt>",
  "size": "1024x1024",
  "quality": "high",
  "background": "transparent",
  "output_format": "png"
}
```

编辑帧（把锚图作为 Image 1）：

```text
images.edit(
  model="gpt-image-1",
  image=[anchor_warrior_01.png],
  prompt="<粘贴 §7.3 的逐帧 prompt>",
  size="1024x1024",
  quality="high",
  background="transparent",
  output_format="png",
  input_fidelity="high"   # 若版本支持
)
```

> 本地工具若不暴露这些参数：只保留 prompt，并把 `background` 诉求写进文字；仍不透明就走 chroma。模型名/参数以工具实际暴露为准。

---

## 3. 一致性技法（同一包多单位）

### 3.1 没有 seed 时的“四件套”

gpt-image 的 Images API 参考没有公开 seed；本项目按以下组合锁风格[3][4][6][7]：

1. **风格锚图**：先做 `warrior_01`，4 选 1，人工确认“兽人种族 + 战士剪影 + 硬边 + 留白”都对。
2. **参考图**：后续 5 个兽人都把锚图作为 Image 1，提示词写 `same orc race, same palette, same outline width, same camera, same padding as Image 1; new class/gear`。
3. **逐字风格块**：所有 prompt 共用同一段 style/constraints，不改写。
4. **4 选 1 + 24px 选**：每次出 4 张，缩到 24px 看剪影；不要在高分辨率下被细节骗。

> 若本地工具暴露 seed：把锚图的 seed 记下来，后续同 seed；但仍要参考图 + 风格块，seed 不是万能[4][7]。

### 3.2 参考图/编辑模式的写法

- 多图输入要按 **索引 + 描述** 引用：`Image 1: identity anchor... Image 2: pixel-grid layout guide...`；说清各自作用[1]。
- 编辑时写 `change only X` + `keep everything else identical`，并把不变项**每条重复**；模型会漂移[1]。
- 每帧都从**同一张基图**编辑，不要从上一帧链式编辑；链式会累积漂移[4]。
- 若版本暴露 `input_fidelity`，设 `"high"` 对角色/武器保真最好；第一张参考图权重最高[3]。

### 3.3 像素网格参考（可选，但有效）

- 可给一张 1024 的“交替像素/网格”参考图，提示词写：`Image 1 role: pixel-grid anchor. Use it only to enforce chunky pixel block discipline and centered single-frame composition. Do not copy its content.`[6]
- **不要把高精度插画/box art 当 img2img 参考**：会把输出拉向 painterly，剪影反而变差[6]。
- 若工具不支持多图/参考图，跳过网格，靠提示词 + 后处理。

---

## 4. 到我们规格的落地（32×32 / 22px / 基线 26 / 6 灰）

### 4.1 分工表（最重要）

| 事项 | 交给提示词？ | 交给后处理？ | 说明 |
|---|---|---|---|
| 种族身体（兽人绿皮/獠牙/尖耳） | ✅ 描述 | 可选强化 | 种族靠身体，不靠颜色 |
| 职业剪影（盾+斧/杖+球/弓/双刃） | ✅ **必须写清楚** | 可补武器道具 | 32px 下武器要“超大、简单、外扩” |
| 朝向（右/3-4 正面） | ✅ | flipX 由代码 | 只画一套 |
| 透明背景 | ✅ 提示 + API 参数 | ✅ 抠图/alpha | 模型透明不稳定 |
| 硬边/无抗锯齿 | ✅ 关键词 | ✅ 量化/硬 alpha | 模型常犯规 |
| 32×32 / 内容 22px | ❌ 不要要求 | ✅ 最近邻缩放 | 模型做不到精确 32 |
| 脚底 y=26 | ❌ | ✅ 按 alpha bbox 对齐 | 归一化必须做 |
| 6 档灰 / ≤10 色 | ❌ 不要要求“6 色” | ✅ 灰度 LUT + 量化 | 模型不懂“6 档灰” |
| 12 帧一致 | 部分（编辑约束） | ✅ 归一化/脚底/缩放 | 每帧从基图编辑 |
| 星级 tint | ❌ | ✅ 运行期 `StarColors` | 贴图本身无色相 |

### 4.2 后处理步骤（沿用现有管线）

1. 读入 1024 RGBA/chroma 图 → 抠背景/键色 → 真实 alpha。
2. 按 alpha bbox 裁切（不要信画面上的“视觉中心”）。
3. 最近邻缩放到内容高 **22px**（宽按比例；不超过 28px，留 2px 边距）。
4. 水平居中，脚底最下非透明行 = **26**。
5. 6 档灰：按不透明像素亮度排名映射到 `31/105/140/171/205/247`（与现有管线一致）。
6. 若仍 >10 色：合并到 6 档灰（模式 B 再叠加道具的 4 档灰）；硬 alpha 0/255。
7. 输出 32×32 PNG，文件名 = `PlaceholderKeys` key，写 manifest/SHA。

> 现有脚本：`assets_src/tools/make_batch2.ps1`（批量生成/灰度/基线/manifest/preview）与 `process_pilot.ps1`；AI 图只需替换“身体帧”输入，后处理逻辑可复用。

### 4.3 提示词里不要写的东西

- 不要写 `32x32 pixels` 当作输出尺寸（会得到“像素化小图”再被放大，糊）；写 `32x32-readable silhouette` + `delivered at 1024` 更稳[4][6]。
- 不要写 `exactly 6 colors` / `6 grayscale levels`；模型不理解，写成 `limited palette, flat shading, hard outline`，量化交给后处理。
- 不要写 `perfect pixel grid` 后又不给网格参考；模型会自由发挥。

---

## 5. 与现有视觉体系的衔接

### 5.1 三轴不能丢

- **种族**：兽人 = 绿皮 + 獠牙 + 尖耳 + 粗壮；野兽 = 佝偻/口鼻；亡灵 = 骷髅/下颌；巨人 = 大体型/宽肩。
- **职业**：战士 = 盾 + 斧/锤/剑；法师 = 杖 + 球/新月/骷髅/提灯 + 帽/兜帽；游侠 = 弓/长弓/标枪/投石索 + 箭袋；刺客 = 兜帽 + 短刃/双刃。
- **星级**：灰度 + `StarColors` tint；1★ 骨白、2★ 金、3★ 紫。AI 图不要自带金色/紫色主体，否则会与 tint 打架。

### 5.2 彩色生成 → 程序化去色（推荐 R1-a 案）

- **推荐**：提示词要 `limited palette, low saturation, flat shading, hard dark outline`，生成彩色；后处理按现有 6 档灰 LUT 去色。理由：彩色能让模型区分材质/武器，灰度化后仍有明暗层级；直接要求“纯灰度”模型经常偷偷加色，反而更难控[4][5]。
- **备选**：提示词写 `monochrome, bone-white and charcoal, no color accents`，适合“先看剪影”的探索；正式批仍走 6 档灰。
- **不要**：生成高饱和彩色后直接用运行期 tint；3★ 紫会把整只压成暗蓝紫（pilot 已验证，见 `assets_src/validation_grid/previews/color_vs_grey_stars.png`）。
- 若主人最终想要全彩：需要代码加“真素材不 tint”分支 + 全量彩色帧；这是另一条路线，不在本手册默认方案内。

### 5.3 武器剪影 vs 后处理道具：两种接入模式

- **模式 A（本手册 §6 的写法，建议先试）：AI 生成“完整单位”**——身体 + 职业武器都在一张图里；后处理只做抠图/裁切/缩放/灰度/基线，**不再叠道具**。优点：武器与身体透视自然、剪影一体；缺点：职业剪影完全靠提示词，AI 可能把斧画小、把双刃画成披风。
- **模式 B（回退）：AI 只生成“种族身体”**——提示词加 `empty hands, no weapon, neutral hands`，再用现有 `make_batch2.ps1` 的盾/斧/杖/球/弓/箭袋/匕首/兜帽叠层。优点：职业剪影可控、与已验证的 revision 2 视觉规范一致；缺点：武器与身体的透视/握持可能不贴，需要手调锚点。
- **模式 C（混合）**：AI 全图过 24px 看；哪一把武器不读，就抹掉重生成“空手身体”，再叠现有道具。
- 24px 验收：用 `assets_src/validation_grid/` 的方法做 4 族 × 4 职业盲测；兽人 6 子先做一轮 6 格自读，重点看“武器是否超出身体轮廓”。若 AI 的武器太小/糊，按模式 B 回退，不要硬修 1024 细节。

---

## 6. 兽人试点：6 条完整提示词

> 用法：每条都是英文完整 prompt（可直接粘贴）。末尾统一带 `STYLE` 段；生成 1024×1024，透明背景（或 chroma），4 选 1。
> 如果你用的工具支持参数：`size=1024x1024`, `background=transparent`, `output_format=png`, `quality=high`；若版本支持编辑保真，再加 `input_fidelity="high"`[2][3]。
> 若只能 prompt：把 `transparent background` 保留；失败改 chroma——把 STYLE 段里的 `Transparent background` 换成 `Solid flat #FF00FF chroma background`，后处理键色。

**公共 STYLE 段（已重复在每条末尾，勿改写）**

```text
Pixel-art game sprite, single character, full body, three-quarter front view facing slightly right, centered, feet at bottom-center, about 70% of the canvas height with even padding. Chunky silhouette readable at 32x32, hard 1px dark outline, flat 3-tone shading, no anti-aliasing, no blur, no gradients, no soft edges, no dithering, limited palette of olive-green orc skin, bone-white tusks, dark brown leather, dull iron and muted red cloth. Transparent background; no shadow, no ground, no scenery, no border, no frame, no text, no watermark, no extra characters, no UI.
```

### 6.1 `unit_warrior_01` 兽人战士（战士：盾 + 斧）

```text
Intended use: one idle sprite frame for a 32x32 pixel-art auto-battler unit, orc warrior.
Subject: a broad-shouldered orc warrior, olive-green skin, two bone-white tusks, pointed ears, leather harness, one dull-iron shoulder plate, heavy leather boots.
Class silhouette: a massive single-bladed iron axe in the right hand and a round wooden shield with an iron boss on the left arm; both are oversized, simple, bold shapes that read at 32x32.
Pose: confident idle, feet planted apart, facing slightly right, calm and grounded.
Pixel-art game sprite, single character, full body, three-quarter front view facing slightly right, centered, feet at bottom-center, about 70% of the canvas height with even padding. Chunky silhouette readable at 32x32, hard 1px dark outline, flat 3-tone shading, no anti-aliasing, no blur, no gradients, no soft edges, no dithering, limited palette of olive-green orc skin, bone-white tusks, dark brown leather, dull iron and muted red cloth. Transparent background; no shadow, no ground, no scenery, no border, no frame, no text, no watermark, no extra characters, no UI.
```

### 6.2 `unit_boar_rider` 野猪骑士（战士：盾 + 斧，骑乘）

```text
Intended use: one idle sprite frame for a 32x32 pixel-art auto-battler unit, orc boar rider.
Subject: a compact orc boar rider, olive-green skin, bone-white tusks, pointed ears, leather harness and iron shoulder plate, sitting upright on a small bristly boar mount with short tusks and a dark leather saddle.
Class silhouette: a single-bladed iron axe in the right hand and a small round shield on the left arm; the rider's torso, head, axe and shield dominate the silhouette.
Mount rule: the boar is low, short and compact, tucked under the rider; the whole rider-plus-mount silhouette must fit one square 32x32 cell, no oversized mount, no long body.
Pose: aggressive but controlled idle, facing slightly right.
Pixel-art game sprite, single character, full body, three-quarter front view facing slightly right, centered, feet at bottom-center, about 70% of the canvas height with even padding. Chunky silhouette readable at 32x32, hard 1px dark outline, flat 3-tone shading, no anti-aliasing, no blur, no gradients, no soft edges, no dithering, limited palette of olive-green orc skin, bone-white tusks, dark brown leather, dull iron and muted red cloth. Transparent background; no shadow, no ground, no scenery, no border, no frame, no text, no watermark, no extra characters, no UI.
```

### 6.3 `unit_mage_apprentice` 焰痕祭司 / 兽人法师学徒（法师：杖 + 球 + 帽/兜帽）

```text
Intended use: one idle sprite frame for a 32x32 pixel-art auto-battler unit, orc mage apprentice.
Subject: a young orc mage apprentice, olive-green skin, small bone-white tusks, pointed ears, hooded ember-orange robe with dark trim, small leather belt pouch, one hand raised in a calm casting gesture.
Class silhouette: a wooden staff taller than the character, topped with a large glowing ember-orange orb with a simple dark crescent holder; the orb and staff extend above the head and read as a magic staff, not a spear.
Pose: calm idle, staff planted, facing slightly right.
Pixel-art game sprite, single character, full body, three-quarter front view facing slightly right, centered, feet at bottom-center, about 70% of the canvas height with even padding. Chunky silhouette readable at 32x32, hard 1px dark outline, flat 3-tone shading, no anti-aliasing, no blur, no gradients, no soft edges, no dithering, limited palette of olive-green orc skin, bone-white tusks, dark brown leather, dull iron and muted red cloth. Transparent background; no shadow, no ground, no scenery, no border, no frame, no text, no watermark, no extra characters, no UI.
```

### 6.4 `unit_legend_quartermaster` 王家军需官（法师：杖 + 账本）

```text
Intended use: one idle sprite frame for a 32x32 pixel-art auto-battler unit, veteran orc quartermaster.
Subject: a heavy-set veteran orc quartermaster, olive-green skin, one iron-capped tusk, pointed ears, armored coat with supply straps, a thick chain-bound ledger hanging at the belt, a stern expression.
Class silhouette: a tall wooden staff with a dark crescent top and a small cold-blue orb; the chained ledger is a secondary mage/quartermaster cue at the hip.
Pose: authoritative idle, staff planted, ledger visible, facing slightly right.
Pixel-art game sprite, single character, full body, three-quarter front view facing slightly right, centered, feet at bottom-center, about 70% of the canvas height with even padding. Chunky silhouette readable at 32x32, hard 1px dark outline, flat 3-tone shading, no anti-aliasing, no blur, no gradients, no soft edges, no dithering, limited palette of olive-green orc skin, bone-white tusks, dark brown leather, dull iron and muted red cloth. Transparent background; no shadow, no ground, no scenery, no border, no frame, no text, no watermark, no extra characters, no UI.
```

### 6.5 `unit_shadow_blade` 暗影之刃（刺客：双匕首 + 兜帽）

```text
Intended use: one idle sprite frame for a 32x32 pixel-art auto-battler unit, orc shadow blade assassin.
Subject: a lean orc assassin, olive-green skin, bone-white tusks and pointed ears visible under a dark hood and lower-face mask, dark leather armor with a short chest strap.
Class silhouette: two curved dark-iron daggers held low and slightly outward, both blades extending past the body silhouette; the hood is low and rounded, not a wizard hat.
Pose: crouched forward, weight on the front foot, facing slightly right, ready to strike.
Pixel-art game sprite, single character, full body, three-quarter front view facing slightly right, centered, feet at bottom-center, about 70% of the canvas height with even padding. Chunky silhouette readable at 32x32, hard 1px dark outline, flat 3-tone shading, no anti-aliasing, no blur, no gradients, no soft edges, no dithering, limited palette of olive-green orc skin, bone-white tusks, dark brown leather, dull iron and muted red cloth. Transparent background; no shadow, no ground, no scenery, no border, no frame, no text, no watermark, no extra characters, no UI.
```

### 6.6 `unit_spear_headhunter` 掷矛猎头者（游侠：标枪 + 手持矛）

```text
Intended use: one idle sprite frame for a 32x32 pixel-art auto-battler unit, orc spear headhunter.
Subject: a lean orc javelin hunter, olive-green skin, bone-white tusks, pointed ears, topknot, red war paint across the eyes, light leather armor and a short cape.
Class silhouette: three javelins strapped diagonally across the back, with fletched tips visible above the right shoulder, and a long spear held in the right hand; the bundle of javelins reads as a ranger silhouette at 32x32.
Pose: side-on throwing stance, spear angled, facing slightly right, feet planted.
Pixel-art game sprite, single character, full body, three-quarter front view facing slightly right, centered, feet at bottom-center, about 70% of the canvas height with even padding. Chunky silhouette readable at 32x32, hard 1px dark outline, flat 3-tone shading, no anti-aliasing, no blur, no gradients, no soft edges, no dithering, limited palette of olive-green orc skin, bone-white tusks, dark brown leather, dull iron and muted red cloth. Transparent background; no shadow, no ground, no scenery, no border, no frame, no text, no watermark, no extra characters, no UI.
```

**兽人试点风险提示**

- 野猪骑士最容易在 32px 超宽；先看 `roster` 24px 剪影，若溢出就把坐骑压小/裁切，保留“骑手感”即可。
- 军需官的“账本”在 24px 可能只剩一个点；职业读法靠“杖 + 袍”，账本是彩蛋。
- 影刃的兜帽 + 双刃是职业主线索；别让披风把双刃剪影吃掉。
- 猎头者的 3 根标枪要超出肩线；如果模型画成一根，回退到现有 `javelin` 道具叠层。

---

## 7. `unit_warrior_01` 的 12 帧动画策略

### 7.1 结论

- **idle/attack/cast/death：用基图 + 逐帧编辑**（每帧从基图出发，不链式）。
- **walk：优先 image-to-video**；没有视频能力就用两张“夸张步幅”的编辑图，接受偏硬。
- **不要生成整张 12 帧 sheet**；不要用“整体平移/缩放”假装动作。
- 编辑时把“不变项”逐条重复，否则 12 帧会漂移[1][4][6]。

### 7.2 基图（idle_0）

直接使用 §6.1 的完整 prompt，追加：

```text
This is the canonical idle frame. Neutral stance, feet planted at the bottom-center, full body, no attack, no spell effect.
```

生成 4 张，选 1 张作为 **Image 1 identity anchor**；后续所有帧都从它编辑。

### 7.3 逐帧编辑提示词（英文，直接可用）

> 每条都以 `Use Image 1 as the exact character reference.` 开头；生成 1024，最后按 §4.2 归一化。

| 帧 | 编辑提示词（只改这一处） |
|---|---|
| `idle_1` | `Use Image 1 as the exact character reference. Keep identity, proportions, outfit, palette, hard outline, camera, scale, foot baseline and framing identical. Change only: the chest and shoulders rise about 2%, and the axe hand relaxes slightly. No translation, no zoom, no rotation.` |
| `walk_0` | `Use Image 1 as the exact character reference. Keep identity, proportions, outfit, palette, hard outline, camera, scale, foot baseline and framing identical. Change only: the left leg strides forward about 20% of body width, the right arm counter-swings about 8%, the torso stays centered. Feet remain on the same baseline. No whole-character translation.` |
| `walk_1` | `Use Image 1 as the exact character reference. Keep identity, proportions, outfit, palette, hard outline, camera, scale, foot baseline and framing identical. Change only: the right leg strides forward about 20% of body width, the left arm counter-swings about 8%, the torso stays centered. Feet remain on the same baseline. No whole-character translation.` |
| `attack_0` | `Use Image 1 as the exact character reference. Keep identity, proportions, outfit, palette, hard outline, camera, scale, foot baseline and framing identical. Change only: the torso leans back about 6%, the axe arm pulls back about 20%, both feet stay planted. Keep the head, shield, scale and position identical.` |
| `attack_1` | `Use Image 1 as the exact character reference. Keep identity, proportions, outfit, palette, hard outline, camera, scale, foot baseline and framing identical. Change only: the torso leans forward about 8%, the axe sweeps forward across the body, the front foot stays planted. Keep the head, scale and position identical.` |
| `attack_2` | `Use Image 1 as the exact character reference. Keep identity, proportions, outfit, palette, hard outline, camera, scale, foot baseline and framing identical. Change only: the axe lowers back toward the idle position, the torso returns upright. Keep the head, shield, scale and position identical.` |
| `cast_0` | `Use Image 1 as the exact character reference. Keep identity, proportions, outfit, palette, hard outline, camera, scale, foot baseline and framing identical. Change only: a small warm-orange battle-cry spark appears at the free hand. The body, pose, weapon and feet stay identical.` |
| `cast_1` | `Use Image 1 as the exact character reference. Keep identity, proportions, outfit, palette, hard outline, camera, scale, foot baseline and framing identical. Change only: the spark becomes a small bright-orange ring with three spark pixels at the free hand. The body, pose, weapon and feet stay identical.` |
| `death_0` | `Use Image 1 as the exact character reference. Keep identity, proportions, outfit, palette, hard outline, camera, scale, foot baseline and framing identical. Change only: the character staggers, the shoulders drop and the axe lowers. Feet remain on the baseline.` |
| `death_1` | `Use Image 1 as the exact character reference. Keep identity, proportions, outfit, palette, hard outline, camera, scale, foot baseline and framing identical. Change only: the knees buckle, the torso hunches forward and the axe drops. Feet remain on the baseline.` |
| `death_2` | `Use Image 1 as the exact character reference. Keep identity, proportions, outfit, palette, hard outline, camera, scale, foot baseline and framing identical. Change only: the character lies on the side, same scale and position, silhouette still readable. No translation, no zoom.` |

**每条末尾统一追加**：

```text
Preserve the exact character identity, proportions, outfit, palette, hard 1px dark outline, camera, scale, foot baseline and framing. Change only the part listed above. Do not translate, rotate, zoom, resize, or recenter the character. No anti-aliasing, no blur, no gradients, no text, no watermark, no extra characters, no scenery, no shadow.
```

### 7.4 为什么不用 1px 级变化

1024 图缩到 32px 后，1024 上的 1px 只剩约 0.03px，必然消失。所有帧差要按 **最终 32px 的可见量** 设计：

- 走路步幅：至少 **±20% 体宽**（1024 上约 30~50px），32px 后才约 2~4px。
- 攻击前倾/挥砍：**±8~20%**，不要 2~3px。
- 施法光效：至少 **4~6px 亮斑**（1024 上 20~30px），否则缩小后看不见。
- 死亡：明显姿态变化（屈膝/横卧），不要只降 2px。

### 7.5 walk 的两种做法

| 做法 | 步骤 | 评价 |
|---|---|---|
| **image-to-video（首选）** | 把 idle 基图喂给 i2v 模型，提示“原地走、步幅小、不转向、不位移”；取 4s 视频，选 2 个对侧步帧 | 纯图像生成很难出好 walk；i2v 是目前最稳的[6] |
| 编辑两张 | `walk_0` 左腿前 / `walk_1` 右腿前，步幅 ±20%，其余不变 | 无视频能力时的退路；比“整体平移”好，但仍偏硬 |

> 现有批 2 管线用 `run 1/3` 源帧 + lean 合成；如果 AI 帧质量不够，可以回退到“AI 只出 idle 基图 + 现有 12 帧合成”，保证合同与基线。

### 7.6 归一化（必须做）

1. 每帧按 alpha bbox 裁切（不要信 1024 画面里的“视觉中心”）。
2. 以 idle 的可见高度为基准，统一缩放（attack 常偏小）。
3. 统一内容高 22px、脚底 26、水平居中。
4. 6 档灰 + 量化；检查 24px 剪影。
5. 生成 contact sheet/GIF 检查漂移；再写 manifest[6]。

---

## 8. Codex 执行清单（按顺序）

1. **跑锚图**：用 §6.1 生成 4 张 1024，选 1 张 `warrior_01_idle_0` 候选；缩 24px 看“兽人 + 盾 + 斧”是否一眼可读。
2. **锁风格**：把锚图存为 `anchor_warrior.png`；生成其余 5 个兽人时，把锚图作为 Image 1，提示词用 §6.2~6.6 + `same orc race style/palette/outline/camera/padding as Image 1`。
3. **6 子各 4 选 1**：每子 4 张，缩 24px；优先“武器剪影超出身体”，其次“种族特征（绿皮/獠牙/尖耳）清楚”；不要被 1024 细节骗。
4. **12 帧**：只对 `warrior_01` 先做；基图 + §7.3 编辑；walk 用 i2v 或两张夸张步幅。
5. **后处理**：按 §4.2 走现有管线；输出 32×32、22px 内容、基线 26、6 档灰、≤10 色、硬 alpha。
6. **验收**：24px contact sheet / 3★ tint / 敌方红框 + flipX；兽人 6 子 + 4 职业盲测；不达标先改剪影，不急着加细节。
7. **落盘**：`assets_src/aigen/draft_orc/`（或与批 2 同构的目录）；manifest 记录 prompt/model/参数/seed（若有）/SHA；不要直接覆盖 `assets/art/units/`，等主人目检。

---

## 9. 来源

### 9.1 实践 → 来源对照

| 实践 | 主要来源 |
|---|---|
| 结构 + 目的、约束/不变项、编辑只改一处、重复 invariants | [1] |
| 尺寸/quality/background/output_format/参考图上限/input_fidelity | [2][3] |
| 无公开 seed；无 negative 参数（用 Avoid/Constraints） | [1][3] |
| 透明 edits 不稳定、“remove background”误删白区 | [8] |
| 像素风修饰词、负向列表、1024→32 最近邻、调色板 | [5] |
| 风格锚、参考图、chroma、逐帧从同一图标编辑 | [4] |
| 单精灵 vs 拼版、归一化（alpha bbox/脚底/缩放）、walk 用 i2v | [6] |
| 提示词具体性、style reference、先探索后生产 | [7] |
| 透明 PNG + mask 的官方示例 | [9] |

### 9.2 链接

1. **OpenAI Cookbook — gpt-image-1.5 Prompting Guide**（2025-12）：结构、约束、编辑只改一处、参考图/风格迁移、透明产品图。  
   https://cookbook.openai.com/examples/multimodal/image-gen-1.5-prompting_guide
2. **OpenAI — Image generation guide**（gpt-image 系参数：尺寸、quality、background=transparent、透明输出）。  
   https://developers.openai.com/api/docs/guides/image-generation
3. **OpenAI API Reference — Images generate / edit**（1024/1536 尺寸、background、output_format、最多 16 张参考图、input_fidelity；无公开 seed）。  
   https://developers.openai.com/api/reference/resources/images/methods/generate/  
   https://developers.openai.com/api/reference/resources/images/methods/edit/
4. **Summer Engine — How to make game icons and item sprites with AI**（风格锚、同一 seed、参考图、chroma、像素规则、1024→32 最近邻、逐帧从同一图标编辑）。  
   https://github.com/summerengine/docs/blob/c7a2cabd45eb036e74998c5cbde61e77c3ecdfde/scripts/api/guides/icons-and-sprites.md
5. **Pixel Art Prompting Guide（jwynia/agent-skills）**（pixel art 修饰词、negative prompts、1024→32 最近邻、调色板、常见问题）。  
   https://github.com/jwynia/agent-skills/blob/e02ec7e226a6e4f8419fd3b88a1d8e472d421b32/skills/tech/game-development/godot/godot-asset-generator/references/prompting/pixel-art.md
6. **AI Game Spritesheets（chongdashu）**（南向锚图、pixel-grid 参考、5×2 sheet、归一化：alpha bbox/脚底/缩放、walk 用 image-to-video）。  
   https://github.com/chongdashu/ai-game-spritesheets
7. **PixelLab 最佳实践（社区整理）**（提示词具体性、style reference、先探索后生产、透明导出、分辨率/帧数限制）。  
   https://github.com/JacobStephens2/clowder-and-crest/blob/main/todo/art/What%20are%20best%20practices%20for%20AI%20agents%20generating%20Pixel%20Art%20with%20PixelLab.md
8. **OpenAI Developer Community — gpt-image-1 透明背景**（edits 透明不稳定；“remove background”会误删主体白区）。  
   https://community.openai.com/t/gpt-image-1-transparent-backgrounds-with-edit-request/1240577  
   https://community.openai.com/t/gpt-image-1-transparency-remove-background-also-cuts-out-white-spots-of-the-image/1273481
9. **OpenAI Cookbook — Generate images with GPT Image**（透明 PNG + mask 编辑的官方示例）。  
   https://cookbook.openai.com/examples/generate_images_with_gpt_image

> 注：OpenAI 官方页面在本次取件时对无头抓取返回 403；上述实践同时用 Cookbook 原文与可访问的社区/第三方指南交叉核对。模型版本/参数以 Codex 本地工具实际暴露为准；如果本地工具用的是 DALL·E 3，则透明背景不可用，必须走 chroma 键色 + 后处理。
