# 桌面 GM 调试面板

Windows PowerShell，从仓库根目录使用项目所需的 JDK 17 启动。

```powershell
.\gradlew.bat -Pgm :desktop-gm:run
```

进入游戏后，按 **F10** 挂载 GM。在任一窗口按 F10、点击「卸载 GM」或关闭 GM 窗口，都可卸载；在游戏窗口再次按 F10 可重新挂载。每次启动会在系统临时目录创建独立练习档，路径打印在终端。要跨进程复用练习档，传入 `"-PgmSaveDir=C:\path\to\gm-save"`。目录内使用 `gm-profile.json` 和 `gm-run_snapshot.json`，普通桌面入口仍使用 `save/profile.json` 和 `save/run_snapshot.json`。

## 操作和规则

- **金币与经验**。金币填新余额，范围是 0 至 2,147,483,647。经验填带符号的改变量；正数可以连续升级并增加人口，负数最多扣到当前等级的 0 经验。到达 Lv.7 后显示满级，只接受 0 经验改变量。成功后 HUD 下一帧刷新，当前备战快照立即写盘；磁盘写入异常会在面板上显示。
- **已开放场景**。正常开放沿用 `scenes.json` 的 `unlockAfter` 与档案的 `completedScenes`。可给锁定场景增加或撤销独立的练习授权。授权立即反映在准备屏，且不会登记前置场景通关或开放后续场景。撤销授权后，当前远征和已经写好的挂起快照仍可继续。正式解锁始终有效。商店池按现有规则在装配或读档时计算。
- **当前位置**。选择实际存在且开放的场景，再输入第 1 至 25 波。同场景向前保留棋子、装备和金币经验，通过正常的轮开始流程生成目标敌阵、刷新商店或消费一次锁店。跳回较早的同场景波次或跨场景时，有确认提示；确认后用同一英雄和新种子开启全新的练习远征，重新装配名单、装备、经济、商店和重试，并以新快照替换旧快照。
- **备战阵容**。打开「备战阵容」，点选备战席或己方格，再选择棋子模板与星级。备战席按席位顺序排列，新增从第一个空席开始，移除后后续棋子前移。棋盘坐标按模型显示，己方可落子区为 `x=0–5, y=4–6`，空格新增受人口上限约束。替换可直接使用相同格位。模板取自本局商店允许的己方池，支持 1 至 3 星。
- **阵容与装备**。调整星级保留编号、装备和累计花费。新增或替换分配新编号，累计花费从零开始。替换或移除让原装备回背包，金币保持原值。无效目标、过期棋子读数、无效星级与模板会在发号和修改前被拒绝。成功后游戏棋盘、备战席和羁绊随正常渲染刷新，快照立即写盘。磁盘写入失败会报告局内已生效，暂停和离屏时会按原备战快照规则补写。
- **重试与结算**。跳到当前波次会被拒绝，保留原敌阵、已耗重试和该波补给入账状态。向前进入新波按原规则从零次战败开始，旧波已耗重试和入账不会倒回。同一份远征只能向前跳。跳转本身不产生胜负、宝箱、补给、熟练度或场景通关记录。到达后实际战斗仍可按正常规则领局内宝箱，每波补给只入账一次。跳转、修改经济或编辑阵容会从首次有效修改起把快照标成练习远征，包括读档后打完末波时也不写熟练度、通关或进度解锁。通过练习授权进入的远征同样适用。
- **阶段边界**。经济、阵容和跳转只在普通远征的稳定备战阶段可提交。战斗、未领奖或战败结算、终局、暂停、转场和正在处理的游戏输入均受阻。测试档 999 和测试样片可读。场景授权可以在主菜单或备战选择界面管理。

## 模块和生命周期

`-Pgm` 才会把 `desktop-gm` 纳入 Gradle。游戏与普通入口共用原 LWJGL3 / ANGLE 配置。GM 使用 imgui-java 1.89.0 的 JNI 与 Dear ImGui 1.89.9，拥有自己的 GLFW 窗口和非共享 EGL 上下文。GM 的 GLES2 后端上传 ImGui 自己的顶点、索引与纹理，并处理裁剪和顶点偏移。每次 GM 帧和资源处理后都恢复游戏的 GLFW 上下文及 LWJGL GLES 能力表。游戏负责唯一的 GLFW 事件轮询。绑定和原生库仅进入可选模块。

实机探针解析到 LWJGL 3.3.3、GLFW 3.4.0 和 ANGLE 2.1.16862。上游 Java 后端构件声明 LWJGL 3.3.4。实际面板仅引入 JNI binding 与 native，LWJGL 继续由 libGDX 提供。JNI 能加载，桌面 OpenGL 后端在 ANGLE 进程中初始化时报告 `setFunctionMissingAddresses has been called already`。另开 EGL 窗口能正常切换和恢复。上游 Java GLFW 后端能在第二窗口初始化，但会设置进程级监视器回调，因此这里用仅绑定 GM 窗口的按键、字符、鼠标、滚轮和焦点回调。参考 [imgui-java](https://github.com/SpaiR/imgui-java/blob/main/README.md)、[Dear ImGui 后端说明](https://github.com/ocornut/imgui/blob/master/docs/BACKENDS.md)、[GLFW 上下文说明](https://www.glfw.org/docs/latest/context_guide.html)。

面板使用 FreeType 将项目字库和 GM 补字加入 Dear ImGui 字体图集。补字源为 Source Han Sans CN Medium 2.005R，只包含 `desktop-gm/src/main/resources/gm/glyphs.txt` 中的字符，并依 OFL 1.1 重命名为 Ember Legion GM。直接使用绑定的 Java 字节数组字体和范围接口时，实机卸载触发 Windows 堆故障 `0xC0000374`。当前实现将 FreeType 字形像素复制进 ImGui 图集，避免让图集保留 JVM 临时数组的地址。[上游字体与许可证](https://github.com/adobe-fonts/source-han-sans/tree/2.005R)。

GM 读取与写入走游戏线程队列，界面只持有不可变读数。游戏线程在执行前重新核对当前远征、阶段和目标棋子的编号、星级。卸载先关闭会话，使排队操作和回复失效，再解除窗口回调，释放着色器、缓冲区、图集纹理、ImGui 上下文及 GLFW 窗口。只要游戏当前在渲染，已挂载的 GM 随游戏帧更新。游戏输入处理器继续沿用 Screen 输入链。调试启动器保留 F10 的挂载入口，普通运行和发布 JAR 的 classpath 均无 GM、ImGui 或其原生库。

## 复测

1. 调试入口新开远征，选择森林。按 F10，将金币设为 `123`、经验增加 `14`。起始 Lv.1 应升到 Lv.3，剩余经验 `2/12`，人口上限 5，HUD 与面板一致。试填空值、负金币和超出当前等级可扣经验的负值。
2. 在备战席第 1 格和棋盘 `(0, 4)` 分别尝试新增、调整星级、替换及移除。随后在备战席留下焰痕祭司，在 `(0, 4)` 放三星兽人战士，在 `(1, 4)` 放三星野猪骑士。检查人口 2/5、备战席 1/9 及兽人和战士羁绊各 2。
3. 按 GM 窗口 F10 或关闭窗口。用游戏的 Esc 打开暂停，返回主菜单并点「继续远征」。再次按 F10，核对刚保存的阵容，打第一波并选择金币宝箱。正常应从 123 金增到 129 金，经验由 2/12 增到 4/12。
4. 尝试跳到锁定的墓穴第 7 波，再对墓穴点「开放」。森林前置仍未通关且雪山仍锁定。先跳森林第 7 波，检查保留的阵容和森林 Boss。再跳墓穴第 7 波并确认，检查空名单及墓穴 Boss。撤销墓穴授权。
5. 在独立空目录运行普通发布包，验证可直接进入主菜单。

```powershell
.\gradlew.bat -Pgm :core:test :desktop-gm:build :android:assembleDebug :ios:compileJava
.\gradlew.bat :lwjgl3:jar
$jar = (Resolve-Path '.\lwjgl3\build\libs\ember-legion-1.0.0.jar').Path
Push-Location (New-Item -ItemType Directory -Path (Join-Path $env:TEMP ("ember-release-" + [guid]::NewGuid())))
java -jar $jar
Pop-Location
```

## 验证记录

本次基线为 `54290f27e3f0ac5bd9aef81454ae8d01dbc5803e`。Windows / JDK 17 实测通过。核心 1,173 项与桌面模块 2 项测试全过，GM 模块、默认桌面 JAR、Android debug APK 和 iOS Java 编译通过。macOS/Linux 桌面交互、移动端运行、iOS 原生包及物理键盘和鼠标未覆盖。

实测用 `:desktop-gm:run` 启动真实 LWJGL3 游戏并加载原生 Dear ImGui。Win32 `PostMessage` 将按键、字符、点击和关闭消息投递到这次测试的实际窗口，由 GLFW 的本地事件链处理。覆盖两区增、换、删和星级调整、模板空值拒绝、同进程 F10 挂卸载和关闭窗口、卸载后 Esc、原菜单继续远征、中文棋子与羁绊显示。也验证了游戏最小化时关闭 GM、恢复游戏后重新挂载以及从 GM 按钮卸载。改过的阵容实打获胜，领取后只推进一波、宝箱和补给各入账一次。实测还覆盖金币与经验、墓穴授权/撤销、锁定目标拒绝、森林和墓穴第 7 波切换，以及隔离档案未记录通关。核心测试额外覆盖过期读数、非法格位和星级、人口和备战容量、装备归还、IdIssuer、档案失效边界和重复领箱。桌面测试覆盖挂载会话撤销和重复创建/销毁原生中文图集。

普通 JAR 在空目录用 `java -jar` 实际启动到主菜单，JAR 条目和 Gradle 默认 runtimeClasspath 确认无 ImGui 与 GM 界面。验证产物保存在当前工作区 `build/gm-imgui-validation/`，包括 `13-imgui-roster-before-battle.png`、`14-game-roster-preparation.png`、`16-imgui-reattached-resumed.png`、`19-game-real-win-chest.png`、`27-imgui-crypt-wave7.png`、`29-normal-release.png` 和 `observations.log`，以及两套构建、原生探针与启动日志。
