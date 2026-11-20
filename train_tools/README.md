# 棋子识别模型训练工具链

从 Moonlink 移植，支持"换皮肤重训"：把你收集的棋子贴图素材放进目录，合成训练集 → 训练 → 导出 ONNX，替换 App 内的 `jj_piece.onnx`。

## 类别顺序（必须保持一致，共16类）

```
rP rR rK rN rC rA rB - .  RP RK RN RC RA RB
```
即索引 0..15，与 `Constants.CLASS_MAP` 对应。

## 步骤

1. **准备素材**：把每种棋子的透明底 PNG 分别放入 `pieces/`（按 `rP.png`/`R.png`…命名）。
2. **合成训练集**：
   ```
   python create_dataset.py
   ```
   随机把棋子贴到棋盘背景上，生成 `datasets/images/{train,val}` 与 YOLO 格式标签（不过本 App 用的是分类模型，见下）。
3. **训练分类模型**（80×80 输入，16 类）：
   ```
   python train_classifier.py
   ```
   产出 `best.onnx`。
4. **替换**：把 `best.onnx` 改名为 `jj_piece.onnx` 放入 App 的 `assets/model/`。

> 说明：App 当前内置的模型来自 YoungerIOS/chess-helper-app（`jj_piece_model.onnx`），是 80×80 输入、16 类输出的**分类模型**（非检测模型）。重训时保持相同输入尺寸和类别顺序即可无缝替换。
