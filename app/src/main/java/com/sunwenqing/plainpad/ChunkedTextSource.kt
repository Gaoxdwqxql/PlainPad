package com.sunwenqing.plainpad

import java.io.File
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.Charset

/**
 * 大文件的按需读取层：不把文件读进内存，只保留一份稀疏行索引。
 *
 * 内存占用 = 索引（每 512 行一个 long，100MB 文本约 32KB）+ 一个映射窗口，
 * 与文件大小基本无关，这是 100MB 甚至更大文件还能流畅滑动的关键。
 *
 * 行索引按 0x0A 字节扫描，因此要求编码 ASCII 兼容（UTF-8、GBK/GB18030、
 * Big5、Shift_JIS、EUC-KR、ISO-8859-1 等）；UTF-16/32 含 0x00 字节，
 * 仍退回旧的整读路径。
 * 调用方负责在 IO 线程构建，UI 线程只读。
 */
class ChunkedTextSource private constructor(
    private val channel: FileChannel,
    private val indexing: LineIndex,
    val utf8Bom: Boolean,
    private val charset: Charset,
    /** 当前按需读取使用的编码名，供状态栏与诊断显示 */
    val encoding: String,
    /** SAF 场景下 FileChannel 之外还持有 ParcelFileDescriptor，需要一起关掉 */
    private val onClose: (() -> Unit)? = null
) : AutoCloseable {

    /** 文件字节长度（扣除 BOM） */
    val byteLength: Long get() = channel.size() - if (utf8Bom) 3L else 0L

    /** 行数（以 \n 计） */
    val lineCount: Int get() = indexing.count

    /** 稀疏索引实际占用的条目数（诊断用） */
    var indexEntryCount: Int = 0
        private set

    /** 建立索引耗时（诊断用） */
    var indexBuildMs: Long = 0L
        private set

    /** 最近一次 readLines 实际读取的字节数（诊断用） */
    var lastReadBytes: Int = 0
        private set

    /** 最近一次 readLines 耗时（诊断用） */
    var lastReadMs: Long = 0L
        private set

    /** 第 [index] 行的起始字节偏移 */
    fun lineOffset(index: Int): Long = indexing.offsetOf(index)

    /**
     * 一次性读出 [from]..[to] 这些行。
     *
     * 每帧只读一次连续字节，而不是每个可见行各读一次——这是滑动流畅的关键。
     * 返回的列表长度可能小于请求区间（文件末尾），调用方按序绘制即可。
     */
    fun readLines(from: Int, to: Int): List<String> {
        if (from < 0 || to < from || from >= indexing.count) return emptyList()
        val last = minOf(to, indexing.count - 1)

        val startedNs = System.nanoTime()
        lastReadBytes = 0
        lastReadMs = 0L

        val start = indexing.offsetOf(from)
        val rawEnd = indexing.offsetOf(last + 1)
        val end = minOf(if (rawEnd == Long.MAX_VALUE) channel.size() else rawEnd, channel.size())
        var length = (end - start).toInt()
        if (length <= 0) return emptyList()
        length = minOf(length, MAX_WINDOW_BYTES)

        if (windowBuffer.capacity() < length) {
            windowBuffer = ByteBuffer.allocate(length)
        }
        windowBuffer.clear()
        windowBuffer.limit(length)
        channel.position(start)
        while (windowBuffer.hasRemaining()) {
            if (channel.read(windowBuffer) < 0) break
        }

        val size = windowBuffer.position()
        if (size <= 0) return emptyList()

        // 显示行可能因为超长被硬切开，不能只按 '\n' 分；用与建索引相同的规则切字节
        val parts = splitDisplayLines(windowBuffer.array(), size)

        lastReadBytes = size
        lastReadMs = (System.nanoTime() - startedNs) / 1_000_000
        DevDiag.detail("readLines $from..$last bytes=$size ${lastReadMs}ms")
        return parts
    }

    /**
     * 把一段从显示行行首开始的字节切成显示行。
     * 规则必须与建索引时完全一致：换行，或超过 [MAX_VISUAL_BYTES] 后的
     * UTF-8 字符起点 / 最近的空格。
     */
    private fun splitDisplayLines(bytes: ByteArray, size: Int): List<String> {
        val utf8 = encoding.equals("UTF-8", ignoreCase = true)
        val result = ArrayList<String>(48)
        var lineStart = 0
        var i = 0
        var sinceVisual = 0
        var lastSpaceEnd = -1

        while (i < size) {
            val b = bytes[i]
            if (b == '\n'.code.toByte()) {
                result.add(decodeTrimCr(bytes, lineStart, i))
                i++
                lineStart = i
                sinceVisual = 0
                lastSpaceEnd = -1
                continue
            }

            if (sinceVisual >= MAX_VISUAL_BYTES) {
                if (utf8 && (b.toInt() and 0xC0) != 0x80) {
                    result.add(decodeTrimCr(bytes, lineStart, i))
                    lineStart = i
                    sinceVisual = 0
                    lastSpaceEnd = -1
                } else if (!utf8 && lastSpaceEnd > lineStart) {
                    result.add(decodeTrimCr(bytes, lineStart, lastSpaceEnd))
                    lineStart = lastSpaceEnd
                    sinceVisual = i - lastSpaceEnd
                    lastSpaceEnd = -1
                }
            }

            i++
            sinceVisual++
            if (b == ' '.code.toByte()) lastSpaceEnd = i
        }

        if (lineStart < size) {
            result.add(decodeTrimCr(bytes, lineStart, size))
        }
        return result
    }

    private fun decodeTrimCr(bytes: ByteArray, from: Int, to: Int): String {
        var end = to
        if (end > from && bytes[end - 1] == '\r'.code.toByte()) end--
        return String(bytes, from, end - from, charset)
    }

    /**
     * 读取第 [index] 行。不包含行尾的 \n。
     * 超长行会被截断到 [MAX_LINE_BYTES]，避免单行把内存拉爆。
     */
    fun readLine(index: Int): String {
        if (index < 0 || index >= indexing.count) return ""
        val start = indexing.offsetOf(index)
        val end = indexing.offsetOf(index + 1).coerceAtMost(channel.size())
        val length = (end - start).toInt().coerceIn(0, MAX_LINE_BYTES)
        if (length <= 0) return ""

        val buffer = ByteBuffer.allocate(length)
        channel.position(start)
        while (buffer.hasRemaining()) {
            if (channel.read(buffer) < 0) break
        }
        val bytes = buffer.array()
        var size = buffer.position()
        if (size > 0 && bytes[size - 1] == '\n'.code.toByte()) size--
        if (size > 0 && bytes[size - 1] == '\r'.code.toByte()) size--

        return decodeBytes(bytes, size)
    }

    /** 截断标记：这一行是否因为超长被截断了 */
    fun isLineTruncated(index: Int): Boolean {
        if (index < 0 || index >= indexing.count) return false
        val start = indexing.offsetOf(index)
        val end = indexing.offsetOf(index + 1)
        return end - start > MAX_LINE_BYTES
    }

    override fun close() {
        runCatching { channel.close() }
        runCatching { onClose?.invoke() }
    }

    /** 复用缓冲区，避免每帧几十次分配触发 GC */
    private var windowBuffer = ByteBuffer.allocate(64 * 1024)

    private fun decodeBytes(bytes: ByteArray, size: Int): String {
        // 直接用 String + Charset 解码，坏字节自动替换为 U+FFFD；
        // 比每次新建 CharsetDecoder 便宜得多
        return String(bytes, 0, size, charset)
    }

    /** 稀疏索引：每 [STRIDE] 行记录一次字节偏移，其余行按行内扫描定位 */
    private class LineIndex {
        private val offsets = ArrayList<Long>(1024)
        var count: Int = 0
            private set

        /** 已存储的索引点数量（诊断用） */
        val entryCount: Int get() = offsets.size

        private var channelRef: FileChannel? = null
        private var utf8 = true

        fun bind(channel: FileChannel, utf8Encoding: Boolean) {
            channelRef = channel
            utf8 = utf8Encoding
        }

        /** 记录一行的起始字节偏移；只有落在 STRIDE 整数倍上的行才真正存下来 */
        fun add(lineStart: Long) {
            if (count % STRIDE == 0) offsets.add(lineStart)
            count++
        }

        /** 第 n 行的起始偏移；未记录的行从最近的索引点向后扫描 */
        fun offsetOf(n: Int): Long {
            // 第 0 行的起点不一定是 0：带 UTF-8 BOM 时从第 3 个字节开始
            if (n <= 0) return offsets.firstOrNull() ?: 0L
            if (n >= count) return Long.MAX_VALUE

            // 绘制是按行顺序走的，缓存上一条能省掉每行一次重扫
            if (n == cachedLine) return cachedOffset
            if (n == cachedLine + 1) {
                val next = scanForward(cachedOffset, 1)
                if (next >= 0) {
                    cachedLine = n
                    cachedOffset = next
                    return next
                }
            }

            val bucket = n / STRIDE
            if (bucket >= offsets.size) return Long.MAX_VALUE

            // 一次扫完剩余行，而不是每行读一次 64KB
            val pos = scanForward(offsets[bucket], n - bucket * STRIDE)
            if (pos < 0) return Long.MAX_VALUE
            cachedLine = n
            cachedOffset = pos
            return pos
        }

        /**
         * 从 [from] 起跳过 [lines] 个显示行，返回目标行的起始位置；越界返回 -1。
         * 显示行既可能是换行分隔，也可能是超长行在 1KB 处硬切出来的，
         * 所以这里必须用和建索引相同的规则，不能只数换行。
         * 整个过程复用同一块缓冲，一趟读完，快速滑动时不会每行触发一次 read。
         */
        private fun scanForward(from: Long, lines: Int): Long {
            if (lines <= 0) return from
            val ch = channelRef ?: return -1L
            var remaining = lines
            var pos = from
            var sinceVisual = 0
            var lastSpaceEnd = -1L
            val buf = scanBuffer
            while (true) {
                buf.clear()
                ch.position(pos)
                val read = ch.read(buf)
                if (read <= 0) return -1L
                for (i in 0 until read) {
                    val b = buf.get(i)
                    val absolute = pos + i
                    if (b == '\n'.code.toByte()) {
                        remaining--
                        if (remaining == 0) return absolute + 1
                        sinceVisual = 0
                        lastSpaceEnd = -1L
                        continue
                    }

                    if (sinceVisual >= MAX_VISUAL_BYTES) {
                        if (utf8 && (b.toInt() and 0xC0) != 0x80) {
                            remaining--
                            if (remaining == 0) return absolute
                            sinceVisual = 0
                            lastSpaceEnd = -1L
                        } else if (!utf8 && lastSpaceEnd >= 0) {
                            remaining--
                            if (remaining == 0) return lastSpaceEnd
                            sinceVisual = (absolute - lastSpaceEnd).toInt()
                            lastSpaceEnd = -1L
                        }
                    }

                    sinceVisual++
                    if (b == ' '.code.toByte()) lastSpaceEnd = absolute + 1
                }
                pos += read
            }
        }

        private val scanBuffer = ByteBuffer.allocate(64 * 1024)
        private var cachedLine = -1
        private var cachedOffset = 0L
    }


    companion object {
        /** 每 512 行记录一个索引点 */
        private const val STRIDE = 512

        /** 单行最大读取字节数，超出截断 */
        private const val MAX_LINE_BYTES = 256 * 1024

        /** 单帧最多读取的字节数，防止超长行把一次绘制拖很久 */
        private const val MAX_WINDOW_BYTES = 1024 * 1024

        /** 一个显示行最多约这么多字节；超长行会被切成多个显示行 */
        private const val MAX_VISUAL_BYTES = 1024

        /** 按需读取支持 UTF-8 与所有 ASCII 兼容编码；UTF-16/32 返回 null 交给整读路径 */
        fun open(file: File, encoding: String = "UTF-8"): ChunkedTextSource? {
            if (!file.exists() || !file.canRead()) return null
            val channel = runCatching { FileChannel.open(file.toPath()) }.getOrNull() ?: return null
            return open(channel, null, encoding)
        }

        /**
         * 从已打开的通道构建，供 SAF 的 content:// 使用（ParcelFileDescriptor 拿到的 channel）。
         * [onClose] 用于在关闭数据源时一并释放底层流/描述符。
         */
        fun open(
            channel: FileChannel,
            onClose: (() -> Unit)? = null,
            encoding: String = "UTF-8"
        ): ChunkedTextSource? {
            val source = build(channel, onClose, encoding)
            if (source == null) {
                runCatching { channel.close() }
                runCatching { onClose?.invoke() }
            }
            return source
        }

        private fun build(
            channel: FileChannel,
            onClose: (() -> Unit)?,
            encoding: String
        ): ChunkedTextSource? {
            val startedNs = System.nanoTime()

            if (!TextCodec.supportsChunked(encoding)) {
                DevDiag.event("chunked build rejected: encoding=$encoding")
                return null
            }

            val head = ByteBuffer.allocate(3)
            runCatching { channel.read(head) }
            val utf8Bom = encoding.equals("UTF-8", ignoreCase = true) &&
                    head.limit() >= 3 &&
                    head.array()[0] == 0xEF.toByte() &&
                    head.array()[1] == 0xBB.toByte() &&
                    head.array()[2] == 0xBF.toByte()

            // UTF-16 BOM 或明显的二进制内容：不接管
            if (!utf8Bom) {
                channel.position(0)
                val probe = ByteBuffer.allocate(1024)
                val n = runCatching { channel.read(probe) }.getOrDefault(0)
                for (i in 0 until n) {
                    val b = probe.array()[i]
                    if (b.toInt() == 0) {
                        DevDiag.event("chunked build rejected: binary/UTF-16 content ($encoding)")
                        return null
                    }
                }
            }

            val index = LineIndex()
            index.bind(channel, encoding.equals("UTF-8", ignoreCase = true))
            runCatching {
                val startByte = if (utf8Bom) 3L else 0L
                channel.position(startByte)
                var offset = startByte
                var visualStart = startByte
                var sinceVisual = 0
                var lastSpaceEnd = -1L
                val utf8 = encoding.equals("UTF-8", ignoreCase = true)
                index.add(offset) // 第 0 个显示行的起点
                val buffer = ByteBuffer.allocate(256 * 1024)
                while (true) {
                    buffer.clear()
                    val read = channel.read(buffer)
                    if (read <= 0) break
                    for (i in 0 until read) {
                        val b = buffer.get(i)
                        val absolute = offset
                        if (b == '\n'.code.toByte()) {
                            offset++
                            index.add(offset) // 新一行的起点
                            visualStart = offset
                            sinceVisual = 0
                            lastSpaceEnd = -1L
                        } else {
                            // 超长行按显示宽度切开，避免一帧绘制几百 KB 的字符。
                            // UTF-8 在字符起始字节处切；其他 ASCII 兼容编码在最近的空格后切。
                            if (sinceVisual >= MAX_VISUAL_BYTES) {
                                if (utf8 && (b.toInt() and 0xC0) != 0x80) {
                                    index.add(absolute)
                                    visualStart = absolute
                                    sinceVisual = 0
                                    lastSpaceEnd = -1L
                                } else if (!utf8 && lastSpaceEnd > visualStart) {
                                    index.add(lastSpaceEnd)
                                    visualStart = lastSpaceEnd
                                    sinceVisual = (absolute - lastSpaceEnd).toInt()
                                    lastSpaceEnd = -1L
                                }
                            }
                            offset++
                            sinceVisual++
                            if (b == ' '.code.toByte()) lastSpaceEnd = offset
                        }
                    }
                }
            }

            val source = ChunkedTextSource(
                channel = channel,
                indexing = index,
                utf8Bom = utf8Bom,
                charset = TextCodec.charsetFor(encoding),
                encoding = encoding,
                onClose = onClose
            )
            source.indexEntryCount = index.entryCount
            source.indexBuildMs = (System.nanoTime() - startedNs) / 1_000_000
            DevDiag.event(
                "index built encoding=$encoding lines=${index.count} entries=${index.entryCount} " +
                        "bytes=${source.byteLength} bom=$utf8Bom ${source.indexBuildMs}ms"
            )
            return source
        }
    }
}
