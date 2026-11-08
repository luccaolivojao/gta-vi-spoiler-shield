package br.com.spoilershield.block

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.ParcelFileDescriptor
import br.com.spoilershield.core.DnsPacket
import br.com.spoilershield.core.DomainMatcher
import br.com.spoilershield.core.SocialApp
import br.com.spoilershield.shieldApp
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.Executors
import kotlin.concurrent.thread

/**
 * Filtro web por DNS usando a API OFICIAL VpnService. É local: nenhum tráfego passa por servidor
 * nosso. Só as consultas DNS são roteadas para a interface virtual; domínios das redes bloqueadas
 * recebem NXDOMAIN, o restante é repassado ao DNS normal da rede. Não inspeciona conteúdo.
 *
 * Limites: DNS-sobre-HTTPS dentro do navegador e "DNS privado" estrito contornam este filtro.
 */
class DnsShieldVpnService : VpnService() {
    private var tun: ParcelFileDescriptor? = null
    private var reader: Thread? = null
    private val pool = Executors.newFixedThreadPool(4)
    @Volatile private var running = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startFilter()
        return START_STICKY
    }

    private fun startFilter() {
        if (running) return
        val pfd = Builder()
            .setSession("Spoiler Shield (filtro web)")
            .addAddress(CLIENT_IP, 32)
            .addDnsServer(DNS_IP)
            .addRoute(DNS_IP, 32) // somente DNS passa pelo túnel
            .setMtu(1500)
            .establish() ?: return // usuário ainda não autorizou a VPN
        tun = pfd
        running = true
        isRunning = true
        reader = thread(name = "spoilershield-dns", isDaemon = true) { loop(pfd) }
    }

    private fun loop(pfd: ParcelFileDescriptor) {
        val input = FileInputStream(pfd.fileDescriptor)
        val output = FileOutputStream(pfd.fileDescriptor)
        val buf = ByteArray(32767)
        try {
            while (running) {
                val n = input.read(buf)
                if (n <= 0) continue
                val q = DnsPacket.parseUdpDnsQuery(buf, n) ?: continue
                val domains = SocialApp.domainsFor(shieldApp.repo.current.enabledApps)
                if (DomainMatcher.isBlocked(q.qname, domains)) {
                    writeTo(output, DnsPacket.buildUdpResponse(q, DnsPacket.nxdomainPayload(q)))
                } else {
                    pool.execute { forward(q, output) }
                }
            }
        } catch (_: Exception) {
            // túnel fechado
        }
    }

    private fun forward(q: DnsPacket.Query, output: FileOutputStream) {
        try {
            DatagramSocket().use { sock ->
                protect(sock) // evita que a consulta volte para o próprio túnel
                sock.soTimeout = 4000
                sock.send(DatagramPacket(q.dns, q.dns.size, upstreamDns(), 53))
                val resp = DatagramPacket(ByteArray(4096), 4096)
                sock.receive(resp)
                writeTo(output, DnsPacket.buildUdpResponse(q, resp.data.copyOf(resp.length)))
            }
        } catch (_: Exception) {
            // sem resposta: o cliente tenta de novo
        }
    }

    private fun writeTo(out: FileOutputStream, bytes: ByteArray) {
        synchronized(out) { out.write(bytes) }
    }

    /** DNS da rede real (Wi-Fi/dados); se não achar, usa 1.1.1.1. */
    private fun upstreamDns(): InetAddress {
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            for (net in cm.allNetworks) {
                val caps = cm.getNetworkCapabilities(net) ?: continue
                if (caps.hasTransport(NetworkCapabilities.TRANSPORT_VPN)) continue
                if (!caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) continue
                cm.getLinkProperties(net)?.dnsServers?.firstOrNull { it is Inet4Address }?.let { return it }
            }
        } catch (_: Exception) { }
        return InetAddress.getByName("1.1.1.1")
    }

    private fun stopFilter() {
        running = false
        isRunning = false
        try { tun?.close() } catch (_: Exception) { }
        tun = null
        pool.shutdownNow()
    }

    override fun onRevoke() { stopFilter(); super.onRevoke() }
    override fun onDestroy() { stopFilter(); super.onDestroy() }

    companion object {
        private const val CLIENT_IP = "10.111.222.2"
        private const val DNS_IP = "10.111.222.1"
        @Volatile var isRunning = false
            private set

        fun start(ctx: Context) {
            if (isRunning) return
            try { ctx.startService(Intent(ctx, DnsShieldVpnService::class.java)) } catch (_: Exception) { }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, DnsShieldVpnService::class.java))
        }
    }
}
