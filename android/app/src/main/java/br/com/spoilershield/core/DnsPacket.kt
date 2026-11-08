package br.com.spoilershield.core

/**
 * Utilitários puros (sem Android) para o filtro DNS local.
 * O VpnService só roteia o IP do "servidor DNS" virtual para a interface TUN; por isso
 * os únicos pacotes que chegam aqui são consultas DNS sobre UDP/IPv4.
 */
object DnsPacket {

    class Query(
        val srcIp: ByteArray,
        val dstIp: ByteArray,
        val srcPort: Int,
        val dstPort: Int,
        val dns: ByteArray,
        val qname: String,
        /** Posição logo após a seção de pergunta (nome + QTYPE + QCLASS). */
        val questionEnd: Int,
    )

    private fun u16(b: ByteArray, o: Int) = ((b[o].toInt() and 0xFF) shl 8) or (b[o + 1].toInt() and 0xFF)

    /** Lê um nome DNS sem compressão. Retorna (nome, offset logo após o byte 0 final) ou null. */
    fun readName(dns: ByteArray, start: Int): Pair<String, Int>? {
        var o = start
        val sb = StringBuilder()
        while (true) {
            if (o >= dns.size) return null
            val len = dns[o].toInt() and 0xFF
            if (len == 0) { o++; break }
            if (len and 0xC0 != 0) return null // ponteiro de compressão: não esperado na pergunta
            if (o + 1 + len > dns.size) return null
            if (sb.isNotEmpty()) sb.append('.')
            for (i in 0 until len) sb.append((dns[o + 1 + i].toInt() and 0xFF).toChar())
            o += 1 + len
        }
        return sb.toString().lowercase() to o
    }

    fun parseUdpDnsQuery(pkt: ByteArray, len: Int): Query? {
        if (len < 28) return null
        if ((pkt[0].toInt() ushr 4) and 0xF != 4) return null
        val ihl = (pkt[0].toInt() and 0xF) * 4
        if (ihl < 20 || len < ihl + 8 + 12) return null
        if ((pkt[9].toInt() and 0xFF) != 17) return null // só UDP
        val srcPort = u16(pkt, ihl)
        val dstPort = u16(pkt, ihl + 2)
        if (dstPort != 53) return null
        val udpLen = u16(pkt, ihl + 4)
        val dnsLen = minOf(udpLen - 8, len - ihl - 8)
        if (dnsLen < 12) return null
        val dns = pkt.copyOfRange(ihl + 8, ihl + 8 + dnsLen)
        if (u16(dns, 2) and 0x8000 != 0) return null // é resposta, não consulta
        if (u16(dns, 4) < 1) return null
        val (name, afterName) = readName(dns, 12) ?: return null
        val qEnd = afterName + 4
        if (qEnd > dns.size) return null
        return Query(
            srcIp = pkt.copyOfRange(12, 16),
            dstIp = pkt.copyOfRange(16, 20),
            srcPort = srcPort,
            dstPort = dstPort,
            dns = dns,
            qname = name,
            questionEnd = qEnd,
        )
    }

    /** Resposta DNS NXDOMAIN (RCODE=3) contendo apenas a seção de pergunta original. */
    fun nxdomainPayload(q: Query): ByteArray {
        val r = q.dns.copyOfRange(0, q.questionEnd)
        r[2] = ((r[2].toInt() or 0x80) and 0xF9).toByte() // QR=1, AA=0, TC=0 (mantém opcode e RD)
        r[3] = 0x83.toByte()                               // RA=1, RCODE=3
        r[4] = 0; r[5] = 1                                  // QDCOUNT=1
        for (i in 6..11) r[i] = 0                           // AN/NS/AR = 0
        return r
    }

    /** Monta o pacote IPv4/UDP de resposta para o cliente que fez a consulta. */
    fun buildUdpResponse(q: Query, dnsPayload: ByteArray): ByteArray {
        val total = 20 + 8 + dnsPayload.size
        val p = ByteArray(total)
        p[0] = 0x45
        p[2] = (total ushr 8).toByte(); p[3] = total.toByte()
        p[6] = 0x40 // DF
        p[8] = 64   // TTL
        p[9] = 17   // UDP
        q.dstIp.copyInto(p, 12) // origem = IP do "servidor DNS"
        q.srcIp.copyInto(p, 16) // destino = cliente
        val ck = ipChecksum(p, 20)
        p[10] = (ck ushr 8).toByte(); p[11] = ck.toByte()
        p[20] = (q.dstPort ushr 8).toByte(); p[21] = q.dstPort.toByte()
        p[22] = (q.srcPort ushr 8).toByte(); p[23] = q.srcPort.toByte()
        val udpLen = 8 + dnsPayload.size
        p[24] = (udpLen ushr 8).toByte(); p[25] = udpLen.toByte()
        // checksum UDP = 0 (permitido em IPv4)
        dnsPayload.copyInto(p, 28)
        return p
    }

    fun ipChecksum(b: ByteArray, headerLen: Int): Int {
        var sum = 0
        var i = 0
        while (i < headerLen) {
            if (i != 10) sum += u16(b, i) // ignora o próprio campo de checksum
            i += 2
        }
        while (sum ushr 16 != 0) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.inv() and 0xFFFF
    }
}
