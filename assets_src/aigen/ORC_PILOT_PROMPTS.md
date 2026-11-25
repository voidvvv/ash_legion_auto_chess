# 兽人族试点 · 桌面版 Codex 生图操作包（复制粘贴即用）

## 使用方法（主人操作）

1. 打开 Codex 桌面版（您平时能生图的那个入口）
2. 下面 6 条提示词，一次贴一条，每条建议生成 4 张选 1 张最好的
3. 把选中的图按对应文件名保存/放到：`assets_src/aigen/orc_raw/`（目录我稍后创建，直接放进去即可）
   - unit_warrior_01.png（兽人战士）
   - unit_boar_rider.png（野猪骑士）
   - unit_mage_apprentice.png（法师学徒）
   - unit_legend_quartermaster.png（军需官）
   - unit_shadow_blade.png（影刃）
   - unit_spear_headhunter.png（掷矛猎头者）
4. 放好后告诉宝宝一声，剩下的（缩到 32×32、灰度化、12 帧动画、预览、进游戏对比）管线自动完成

> 透明背景优先；如果桌面版不支持透明参数，提示词里的 Transparent background 保持不动即可，后处理会用色键抠底。
> warrior_01 的 12 帧动画：基图选定后在**同一个对话**里继续发编辑指令（见 PROMPT_PLAYBOOK.md §7.3 的 11 条逐帧编辑提示词），或先只出基图看效果，动画下一步再说。

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

