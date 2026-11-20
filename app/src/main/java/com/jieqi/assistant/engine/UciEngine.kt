package com.jieqi.assistant.engine

import android.content.Context
import com.jieqi.assistant.core.Const
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader
import java.io.OutputStreamWriter

/**
 * P4：Pikajie（Pikafish jieqi_old 分支，NDK 交叉编译 arm64 可执行）UCI 通信。
 * 通过 ProcessBuilder 启动，stdin/stdout 走 UCI 协议。
 * position fen <揭棋FEN> -> go movetime 2000 -> bestmove（5位，末位揭示子）。
 */
class UciEngine(private val ctx: Context) {

    private var process: Process? = null
    private var stdin: BufferedWriter? = null
    private var stdout: BufferedReader? = null
    @Volatile var ready = false
        private set

    /** 复制 assets 中引擎二进制+NNUE 到私有目录并赋权 */
    fun prepare(): Boolean {
        return try {
            val dir = File(ctx.filesDir, "engine"); dir.mkdirs()
            val bin = File(dir, "pikajie")
            if (!bin.exists() || bin.length() < 10000) {
                ctx.assets.open(Const.ENGINE_ASSET).use { i -> FileOutputStream(bin).use { o -> i.copyTo(o) } }
            }
            bin.setExecutable(true, false); bin.setReadable(true, false)
            // NNUE 权重放同目录（引擎默认从可执行文件同目录读取）
            val nnue = File(dir, "pikafish.nnue")
            if (!nnue.exists() || nnue.length() < 1000) {
                try { ctx.assets.open(Const.NNUE_ASSET).use { i -> FileOutputStream(nnue).use { o -> i.copyTo(o) } } }
                catch (_: Throwable) {}
            }
            // 处理通用名 nnue.bin
            val nnue2 = File(dir, "nn.bin")
            if (!nnue2.exists() && nnue.exists()) nnue.copyTo(nnue2)

            val pb = ProcessBuilder(bin.absolutePath)
                .directory(dir)
                .redirectErrorStream(false)
            process = pb.start()
            stdin = BufferedWriter(OutputStreamWriter(process!!.outputStream))
            stdout = BufferedReader(InputStreamReader(process!!.inputStream))
            send("uci")
            // 等 uciok
            readUntil("uciok", 3000)
            send("isready")
            readUntil("readyok", 3000)
            ready = true
            true
        } catch (e: Throwable) {
            ready = false
            false
        }
    }

    private fun send(cmd: String) {
        try { stdin?.write(cmd + "\n"); stdin?.flush() } catch (_: Throwable) {}
    }

    /** 读取直到出现关键字或超时，返回最后一行 */
    private fun readUntil(key: String, timeoutMs: Long): String? {
        val end = System.currentTimeMillis() + timeoutMs
        var line: String?
        try {
            while (System.currentTimeMillis() < end) {
                line = stdout?.readLine() ?: break
                if (line.contains(key)) return line
            }
        } catch (_: Throwable) {}
        return null
    }

    /**
     * 分析局面，返回 bestmove UCI 字符串（5位）；失败返回 null。
     */
    fun bestmove(fen: String, movetime: Int = Const.ENGINE_THINK_MS): String? {
        if (!ready) return null
        return try {
            send("position fen $fen")
            send("go movetime $movetime")
            val end = System.currentTimeMillis() + movetime + 5000
            while (System.currentTimeMillis() < end) {
                val line = stdout?.readLine() ?: break
                if (line.startsWith("bestmove")) {
                    return line.split("\\s+".toRegex()).getOrNull(1)
                }
            }
            null
        } catch (e: Throwable) { null }
    }

    fun stop() {
        try { send("quit") } catch (_: Throwable) {}
        try { process?.destroy() } catch (_: Throwable) {}
        ready = false
    }
}
