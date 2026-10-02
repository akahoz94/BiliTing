package com.tingbili.app.player

import androidx.annotation.OptIn
import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * PCM 线性增益（音量增强）。
 *
 * 为什么需要它：`ExoPlayer.volume` 的取值范围是 0.0~1.0，只能把音量"调小"，
 * 原始音轨本身录得偏轻（很多 B 站音频稿件都是）就再也放不大了。要比原始音量更响，
 * 只能在音频链路上直接放大 PCM 样本 —— 这就是本处理器的职责。
 *
 * 实现要点：
 *  - 只在 16bit PCM 上工作（media3 的音频链路在这一档最稳定）；遇到别的编码直接
 *    UnhandledAudioFormatException，让 sink 把它跳过而不是串出杂音。
 *  - 逐样本乘增益并夹到 short 范围内（削波保护）：超过 1.0 的增益必然会有削波风险，
 *    这是所有"音量增益"实现的共同代价。
 *  - gain 用 @Volatile：UI 线程设置，音频线程读取，必须可见。
 *
 * 三个能"安静毁掉播放"的坑（都是实测撞出来的，别改回去）：
 *  1. 不能用 `out.put(inputBuffer)`：media3 的 AudioProcessingPipeline 会把处理器上一轮
 *     返回的输出缓冲**原样再喂回来**当输入，此时 out 和 inputBuffer 是同一个对象，
 *     `ByteBuffer.put(ByteBuffer)` 会抛 IllegalArgumentException("The source buffer is
 *     this buffer") → 被 ExoPlayer 包成 ERROR_CODE_FAILED_RUNTIME_CHECK，
 *     整个播放器进 ERROR 态，表现是"点播放毫无反应"。
 *  2. 不能靠 `out.flip()` 收尾：`asShortBuffer()` 出来的视图有自己独立的 position，
 *     往视图里写不会推进父缓冲的位置，flip 出来长度是 0 —— 等于没声。
 *  3. 不能在 `replaceOutputBuffer()` **之后**再读输入的 position/limit：它会把输入缓冲
 *     `clear()` 掉（同一对象时），所以起止位置必须提前取。
 *  → 所以：位置只提前取一次，读写一律用绝对下标（get/put(index)），最后统一摆好 position。
 */
@OptIn(UnstableApi::class)
class GainAudioProcessor : BaseAudioProcessor() {

    @Volatile
    var gain: Float = 1.0f

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        return if (inputAudioFormat.encoding == C.ENCODING_PCM_16BIT) {
            inputAudioFormat
        } else {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        val g = gain
        val inStart = inputBuffer.position()
        val size = inputBuffer.remaining()
        // 注意：这一句可能把 inputBuffer 清空（当它就是本处理器上一轮的输出缓冲时）
        val out = replaceOutputBuffer(size)

        // src 从 0 开始编号，方便用 inStart/2 作为绝对基准；dst 与 out 同一块内存。
        val src = inputBuffer.duplicate().also { it.position(0) }
            .order(ByteOrder.nativeOrder()).asShortBuffer()
        val dst = out.order(ByteOrder.nativeOrder()).asShortBuffer()
        val base = inStart / 2
        val samples = size / 2
        for (i in 0 until samples) {
            dst.put(i, (src.get(base + i) * g).toInt().coerceIn(MIN_SAMPLE, MAX_SAMPLE).toShort())
        }

        inputBuffer.position(inputBuffer.limit())
        out.position(size).flip()
    }

    private companion object {
        const val MIN_SAMPLE = -32768
        const val MAX_SAMPLE = 32767
    }
}
