# JJ 揭棋助手（Android）

一款纯 Android、无需 Root、无需电脑的「JJ 象棋揭棋」自动辅助应用。

## 原理图

```
┌─────────────┐   MediaProjection    ┌──────────────┐   dHash稳定帧   ┌──────────────┐
│  JJ象棋画面  │ ──ImageReader抓帧──> │ Projection   │ ──连续2帧一致──> │ BoardRecognizer│
│  (前台App)   │                      │ Service(前台) │                 │  (ONNX识别)   │
└─────────────┘                      └──────────────┘                 └──────┬───────┘
                                                                             │ 棋盘数组
                                                                             v
┌─────────────┐   dispatchGesture    ┌──────────────┐   揭棋FEN       ┌──────────────┐
│  JJ象棋落子  │ <──起点→延时→终点──── │ AutoClick    │ <──bestmove──── │ Pikajie引擎   │
│             │                      │ Service(无障碍)│   (5位走法)     │ (NDK arm64)  │
└─────────────┘                      └──────────────┘                 └──────┬───────┘
                                            ^                                │
                                     ┌──────┴────────────────────────────────┘
                                     │  中文记谱 + 箭头 + 评分 → 悬浮窗显示
                              ┌──────────────┐
                              │ OverlayService│ (PNG贴图棋盘 + 两开关)
                              └──────────────┘
```

**识别链路**：截图 → 按 ROI 的 9×10 格点切图 → ONNX（80×80 输入，16 类：红黑各7子+空+无效）→ softmax 置信度≥0.85 → 组棋盘。逐格 dHash 缓存未变格复用结果。

**揭棋链路**：棋盘 + 暗子库（RrNnBbAaCcPp）→ 生成揭棋 FEN `棋盘 轮次 暗子库 0 1` → Pikajie 引擎（Pikafish `jieqi_old` 分支，NDK 交叉编译 arm64）→ 5 位 bestmove（末位为揭示子）→ 黑方视角坐标翻转 → 无障碍手势落子。

## 使用方法

1. 安装 APK（见 Releases），然后依次完成主页的 5 个步骤：
   1. **授权投屏截图**（MediaProjection，系统弹窗点"立即开始"）
   2. **开启悬浮窗权限**
   3. **开启无障碍服务**（用于自动落子手势）
   4. **框选棋盘区域**：拖动四角对齐棋盘 9×10 交叉点
   5. 打开 JJ 象棋进入揭棋对局，点**启动辅助**
2. 悬浮窗两个开关：
   - **自动辅助**：开启后持续识别并显示棋盘 + 推荐走法
   - **自动落子**：在自动辅助基础上自动执行走子（关=仅提示）
3. 悬浮窗内容：棋盘缩略图（PNG 棋子贴图，红箭头标推荐走法）+ 中文记谱 + 评分 + 诊断行。

## 编译

Push 到 `main` 或手动触发 Actions，`build.yml` 会：
1. 下载 JJ 象棋 ONNX 模型（来自 YoungerIOS/chess-helper-app，不入仓库）
2. 用 Android NDK 把 Pikajie 源码编译成 arm64 可执行引擎
3. Gradle 构建 release APK，发布到 Releases（tag `v1.0.0-<run_number>`）

技术栈：Kotlin / AGP 8.5.2 / ONNX Runtime Mobile 1.17.3 / targetSdk 34 / arm64-v8a。

## 换皮肤重训模型

见 [`train_tools/README.md`](train_tools/README.md)：从棋子贴图素材 → 合成训练集 → YOLO 训练 → ONNX 导出，替换 `assets/model/jj_piece.onnx` 即可（需保持 16 类顺序一致）。

## 参考项目

- [lxsgx23/Jieqi-Moonlink](https://github.com/lxsgx23/Jieqi-Moonlink)：揭棋 FEN / 暗子库 / 5位走法 / YOLO 训练工具
- [YoungerIOS/chess-helper-app](https://github.com/YoungerIOS/chess-helper-app)：JJ 象棋 ONNX 模型 / PNG 贴图悬浮窗 / dHash 稳定帧 / 增量缓存
- [official-pikafish/Pikafish](https://github.com/official-pikafish/Pikafish) `jieqi_old` 分支：揭棋引擎源码

## 免责声明

本软件仅供学习与技术研究使用。请勿用于任何违反游戏平台服务条款或法律法规的场景。使用第三方游戏产生的后果由使用者自行承担。本项目不包含任何游戏的加速器/破解/外挂分发，识别模型与引擎均为开源社区成果的集成。
