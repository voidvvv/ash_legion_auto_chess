# Golden UI — 来源与许可存证

| 项 | 值 |
|---|---|
| 素材包 | Golden UI |
| 作者 | Buch（OpenGameArt 用户） |
| 作者页 / 原始来源 | https://opengameart.org/users/buch ／ https://opengameart.org/content/golden-ui |
| 本次下载页 | 同上（OpenGameArt 原始页，无镜像） |
| 许可 | **CC0 1.0**（Public Domain Dedication） |
| 许可原文链接 | https://creativecommons.org/publicdomain/zero/1.0/ |
| 是否需署名 | **否**；作者在 OGA 页写 “NOT REQUIRED, though appreciated: credit me as Buch and link back to my OGA profile page” |
| 取件日期 | 2026-11-22 |
| 包文件与大小 | `ui.psd`（573,124 bytes）、`ui_split.png`（21,699 bytes）、`ui_preview_upscaled.png`（24,950 bytes） |
| `ui_split.png` SHA-256 | `1b5422a4d66121f6727be855055fa23f0b21fe620bc52da0a117d480f8c7dd75` |
| `ui.psd` SHA-256 | `56c660e25a8484fc84ad92c2cfa668937869286560ec17567bd3eb07ec59948d` |
| 预览图 SHA-256 | `c1882197bd25360d2e74499c034f142546da6d77398f6967d82c9d909a366cbb` |
| 包内相关文件 | `ui_split.png`：单张 606×414 拆件图；`ui.psd`：分层源文件（本任务未解析 PSD，裁切以 split PNG 为准） |
| 本项目所做修改 | 裁切/9-slice 重排/调色为深蓝灰+暗棕木+暗金；大部分件（M01/M02/M03/M05/M06/M07/M09/M10/M11/M12）以同包 16 色盘与线宽程序化重绘，M04/M08 为直接裁切。脚本：`assets_src/tools/process_ui_assets.ps1`；无手改原图 |
| 返修记录 | 2026-11-22：M05/M06 清除 x=29 透明列、端饰移出中段；M09 拉槽/图标位移入上下端帽；M10 补齐左侧 2×2 缺口；M02/M03 线宽加到 2px。1:1 与 1.25× 同步更新 |
| 许可证据 | `provenance/opengameart-golden-ui.html`（OGA 页面快照，含 CC0 图标与作者说明）、`provenance/cc0-1.0-deed.html`（CC0 1.0 官方 deed 快照） |

## 备注

- Golden UI 包内没有单独的 `LICENSE` 文件；授权声明在 OpenGameArt 页面（CC0 图标 + “License(s): CC0”）。本目录 `LICENSE.txt` 是按页面声明整理的许可说明，不是包内原文件。
- 该包是 16 色像素 UI，线条 2px；与本项目 0x72 地牢素材的“深蓝灰石材 + 暗棕木 + 暗金”方向兼容，但原包偏暖金/橙，本项目按清单做了整体调暗与冷化。
- 本项目未采购 B02 `ui_btn_sell`；来源包中虽有可裁的按钮件，未使用、未入库。
