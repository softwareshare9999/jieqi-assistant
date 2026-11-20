package com.jieqi.assistant.vision

import android.content.Context
import android.graphics.Bitmap
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import com.jieqi.assistant.core.Const
import java.io.File
import java.io.FileOutputStream
import java.nio.FloatBuffer
import kotlin.math.exp

/**
 * P2：ONNX Runtime Mobile 棋子识别。
 * 复刻 chess-helper-app 的 _preprocess：Resize(80x80) -> RGB -> /255 -> NCHW -> batch。
 * 输出 logits -> softmax -> argmax + confidence。
 */
class PieceRecognizer private constructor(private val session: OrtSession) {

    companion object {
        private const val ASSET = "model/jj_piece.onnx"

        fun create(ctx: Context): PieceRecognizer? {
            return try {
                val f = File(ctx.filesDir, "jj_piece.onnx")
                if (!f.exists() || f.length() < 1000) {
                    ctx.assets.open(ASSET).use { input ->
                        FileOutputStream(f).use { out -> input.copyTo(out) }
                    }
                }
                val env = OrtEnvironment.getEnvironment()
                val opts = OrtSession.SessionOptions().apply {
                    setOptimizationLevel(OrtSession.SessionOptions.OptLevel.ALL_OPT)
                    setIntraOpNumThreads(2)
                }
                val s = env.createSession(f.absolutePath, opts)
                PieceRecognizer(s)
            } catch (e: Throwable) {
                null
            }
        }
    }

    data class Result(val code: Char, val conf: Float, val index: Int)

    private val inputName: String = session.inputNames.iterator().next()

    /** 识别单个棋子格子图 */
    fun recognize(cell: Bitmap): Result? {
        return try {
            val env = OrtEnvironment.getEnvironment()
            val rgba = Bitmap.createScaledBitmap(cell, Const.MODEL_INPUT_SIZE, Const.MODEL_INPUT_SIZE, true)
            val n = Const.MODEL_INPUT_SIZE * Const.MODEL_INPUT_SIZE
            val buf = FloatArray(3 * n)
            val px = IntArray(n)
            rgba.getPixels(px, 0, Const.MODEL_INPUT_SIZE, 0, 0, Const.MODEL_INPUT_SIZE, Const.MODEL_INPUT_SIZE)
            for (i in 0 until n) {
                val c = px[i]
                val r = (c shr 16 and 0xFF) / 255f
                val g = (c shr 8 and 0xFF) / 255f
                val b = (c and 0xFF) / 255f
                // NCHW
                buf[i] = r
                buf[n + i] = g
                buf[2 * n + i] = b
            }
            val shape = longArrayOf(1, 3, Const.MODEL_INPUT_SIZE.toLong(), Const.MODEL_INPUT_SIZE.toLong())
            val tensor = OnnxTensor.createTensor(env, FloatBuffer.wrap(buf), shape)
            val outputs = session.run(mapOf(inputName to tensor))
            tensor.close()
            @Suppress("UNCHECKED_CAST")
            val logits = (outputs[0].value as Array<FloatArray>)[0]
            var maxI = 0; var maxV = Float.NEGATIVE_INFINITY
            for (i in logits.indices) if (logits[i] > maxV) { maxV = logits[i]; maxI = i }
            var sum = 0f
            for (v in logits) sum += exp(v - maxV)
            val conf = exp(maxV - maxV) / sum // = 1/sum，稳定 softmax
            // 上面 maxV-maxV=0 => exp=1；等价于正确 softmax 概率
            val code = Const.CLASS_MAP[maxI]?.first() ?: '.'
            Result(code, conf, maxI)
        } catch (e: Throwable) {
            null
        }
    }

    fun close() { try { session.close() } catch (_: Throwable) {} }
}
