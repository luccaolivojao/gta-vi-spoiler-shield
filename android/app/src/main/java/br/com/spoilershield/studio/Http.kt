package br.com.spoilershield.studio

import okhttp3.MediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody
import okio.BufferedSink
import java.io.File
import java.io.IOException
import java.io.RandomAccessFile
import java.util.concurrent.TimeUnit

object Http {
    /** Cliente único. Sem callTimeout total: uploads de vídeo podem demorar. */
    val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .build()
    }
}

/** Corpo de requisição que envia um trecho de um arquivo sem carregá-lo inteiro na memória. */
class FileRangeBody(
    private val file: File,
    private val start: Long,
    private val length: Long,
    private val type: MediaType?,
    private val onBytes: ((Long) -> Unit)? = null,
) : RequestBody() {
    override fun contentType() = type
    override fun contentLength() = length

    override fun writeTo(sink: BufferedSink) {
        RandomAccessFile(file, "r").use { raf ->
            raf.seek(start)
            val buf = ByteArray(64 * 1024)
            var left = length
            while (left > 0) {
                val n = raf.read(buf, 0, minOf(buf.size.toLong(), left).toInt())
                if (n < 0) throw IOException("Arquivo terminou antes do esperado")
                sink.write(buf, 0, n)
                left -= n
                onBytes?.invoke(n.toLong())
            }
        }
    }
}
