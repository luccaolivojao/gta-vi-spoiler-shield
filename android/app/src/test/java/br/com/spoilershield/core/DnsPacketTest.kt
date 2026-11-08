package br.com.spoilershield.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DnsPacketTest {

    /** Monta um pacote IPv4/UDP com uma consulta DNS tipo A para [name]. */
    private fun queryPacket(name: String, dstPort: Int = 53): ByteArray {
        val dns = ArrayList<Byte>()
        listOf(0x12, 0x34, 0x01, 0x00, 0, 1, 0, 0, 0, 0, 0, 0).forEach { dns += it.toByte() }
        name.split('.').forEach { label -> dns += label.length.toByte(); label.forEach { dns += it.code.toByte() } }
        dns += 0
        listOf(0, 1, 0, 1).forEach { dns += it.toByte() }
        val total = 20 + 8 + dns.size
        val p = ByteArray(total)
        p[0] = 0x45; p[2] = (total ushr 8).toByte(); p[3] = total.toByte(); p[8] = 64; p[9] = 17
        byteArrayOf(10, 111.toByte(), 0, 2).copyInto(p, 12)
        byteArrayOf(10, 111.toByte(), 0, 1).copyInto(p, 16)
        p[20] = 0xC3.toByte(); p[21] = 0x50 // porta origem 50000
        p[22] = (dstPort ushr 8).toByte(); p[23] = dstPort.toByte()
        val udp = 8 + dns.size
        p[24] = (udp ushr 8).toByte(); p[25] = udp.toByte()
        dns.toByteArray().copyInto(p, 28)
        return p
    }

    @Test
    fun parsesQueryName() {
        val pkt = queryPacket("www.Instagram.com")
        val q = DnsPacket.parseUdpDnsQuery(pkt, pkt.size)
        assertNotNull(q)
        assertEquals("www.instagram.com", q!!.qname)
        assertEquals(50000, q.srcPort)
        assertEquals(53, q.dstPort)
    }

    @Test
    fun ignoresNonDnsAndMalformed() {
        val notDns = queryPacket("a.com", dstPort = 80)
        assertNull(DnsPacket.parseUdpDnsQuery(notDns, notDns.size))
        assertNull(DnsPacket.parseUdpDnsQuery(ByteArray(10), 10))
        val truncated = queryPacket("example.com")
        assertNull(DnsPacket.parseUdpDnsQuery(truncated, 35))
    }

    @Test
    fun nxdomainResponseIsWellFormed() {
        val pkt = queryPacket("v16.tiktok.com")
        val q = DnsPacket.parseUdpDnsQuery(pkt, pkt.size)!!
        val dns = DnsPacket.nxdomainPayload(q)
        assertEquals(0x12.toByte(), dns[0]); assertEquals(0x34.toByte(), dns[1]) // mesmo ID
        assertTrue((dns[2].toInt() and 0x80) != 0)        // QR=1
        assertEquals(3, dns[3].toInt() and 0x0F)           // RCODE=NXDOMAIN
        assertEquals(1, dns[5].toInt())                    // QDCOUNT
        assertEquals(0, dns[7].toInt())                    // ANCOUNT
        assertEquals(q.questionEnd, dns.size)

        val resp = DnsPacket.buildUdpResponse(q, dns)
        assertEquals(20 + 8 + dns.size, resp.size)
        // origem/destino trocados
        assertEquals(1, resp[15].toInt()); assertEquals(2, resp[19].toInt())
        // porta de origem da resposta = 53; destino = 50000
        assertEquals(53, ((resp[20].toInt() and 0xFF) shl 8) or (resp[21].toInt() and 0xFF))
        assertEquals(50000, ((resp[22].toInt() and 0xFF) shl 8) or (resp[23].toInt() and 0xFF))
        // checksum IP válido: recomputar deve bater com o campo gravado
        val stored = ((resp[10].toInt() and 0xFF) shl 8) or (resp[11].toInt() and 0xFF)
        assertEquals(stored, DnsPacket.ipChecksum(resp, 20))
    }

    @Test
    fun domainMatcherBlocksSubdomainsOnLabelBoundary() {
        val d = SocialApp.domainsFor(SocialApp.defaultIds)
        assertTrue(DomainMatcher.isBlocked("instagram.com", d))
        assertTrue(DomainMatcher.isBlocked("www.instagram.com.", d))
        assertTrue(DomainMatcher.isBlocked("VIDEO.TWIMG.COM", d))
        assertFalse(DomainMatcher.isBlocked("box.com", d))        // não confundir com x.com
        assertFalse(DomainMatcher.isBlocked("notx.com", d))
        assertFalse(DomainMatcher.isBlocked("example.org", d))
        assertFalse(DomainMatcher.isBlocked("", d))
    }
}
