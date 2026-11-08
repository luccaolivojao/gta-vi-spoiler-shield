package br.com.spoilershield.core

import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

/** Data/hora padrão da operação: 18/11/2026 às 06:30 em America/Sao_Paulo (instante absoluto). */
object ShieldDefaults {
    val ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")
    val START_MS: Long = ZonedDateTime.of(2026, 11, 18, 6, 30, 0, 0, ZONE).toInstant().toEpochMilli()
    const val TEST_DURATION_MS: Long = 5 * 60 * 1000L
}

enum class Phase { WAITING, ACTIVE, TESTING, ENDED }

/**
 * Estado persistido da proteção. Todos os instantes são epoch-millis (UTC), portanto
 * mudanças de fuso horário não alteram o momento de ativação.
 */
data class ShieldState(
    val startAtMs: Long = ShieldDefaults.START_MS,
    /** Preenchido pelo botão ATIVAR BLOQUEIO TOTAL AGORA. */
    val manualStartMs: Long? = null,
    /** Fim do teste de 5 minutos, se houver. */
    val testUntilMs: Long? = null,
    /** Momento em que o usuário encerrou o modo anti-spoiler. */
    val endedAtMs: Long? = null,
    val enabledApps: Set<String> = SocialApp.defaultIds,
    val webFilter: Boolean = true,
    /** O usuário declara que o app TikTok dele já passou na auditoria da Content Posting API. */
    val tiktokAudited: Boolean = false,
)

object ProtectionLogic {

    fun phase(s: ShieldState, now: Long): Phase {
        val testing = s.testUntilMs != null && s.testUntilMs > now
        if (s.endedAtMs != null) return if (testing) Phase.TESTING else Phase.ENDED
        val manual = s.manualStartMs != null && s.manualStartMs <= now
        if (manual || s.startAtMs <= now) return Phase.ACTIVE
        return if (testing) Phase.TESTING else Phase.WAITING
    }

    fun isEnforcing(s: ShieldState, now: Long): Boolean =
        phase(s, now).let { it == Phase.ACTIVE || it == Phase.TESTING }

    /** Desde quando a proteção real (não teste) está ativa; null se não está. */
    fun protectedSinceMs(s: ShieldState, now: Long): Long? {
        if (phase(s, now) != Phase.ACTIVE) return null
        val candidates = listOfNotNull(
            s.startAtMs.takeIf { it <= now },
            s.manualStartMs?.takeIf { it <= now },
        )
        return candidates.minOrNull()
    }

    /** Próximo instante em que algo muda sozinho (início agendado ou fim de teste). */
    fun nextBoundaryMs(s: ShieldState, now: Long): Long? {
        val list = mutableListOf<Long>()
        if (s.endedAtMs == null && s.startAtMs > now && s.manualStartMs == null) list += s.startAtMs
        s.testUntilMs?.takeIf { it > now }?.let { list += it }
        return list.minOrNull()
    }

    // ---------- Ações (funções puras que devolvem o novo estado) ----------

    fun panic(s: ShieldState, now: Long) = s.copy(manualStartMs = now, endedAtMs = null)

    fun startTest(s: ShieldState, now: Long) = s.copy(testUntilMs = now + ShieldDefaults.TEST_DURATION_MS)

    fun end(s: ShieldState, now: Long) = s.copy(endedAtMs = now, testUntilMs = null)

    /** Reativa depois de encerrar: volta a valer a data agendada / bloqueio manual. */
    fun rearm(s: ShieldState, now: Long) = s.copy(endedAtMs = null, manualStartMs = now)

    fun reschedule(s: ShieldState, newStartMs: Long) =
        s.copy(startAtMs = newStartMs, manualStartMs = null, endedAtMs = null, testUntilMs = null)
}

// ---------- Texto da contagem regressiva ----------

data class CountdownParts(val days: Long, val hours: Long, val minutes: Long, val seconds: Long)

object CountdownFormat {

    fun parts(d: Duration): CountdownParts {
        val total = d.seconds.coerceAtLeast(0)
        return CountdownParts(total / 86_400, (total % 86_400) / 3_600, (total % 3_600) / 60, total % 60)
    }

    private fun unit(n: Long, one: String, many: String) = "$n ${if (n == 1L) one else many}"

    private fun join(parts: List<String>): String = when (parts.size) {
        0 -> ""
        1 -> parts[0]
        else -> parts.dropLast(1).joinToString(", ") + " E " + parts.last()
    }

    private fun phrase(p: CountdownParts): List<String> = buildList {
        if (p.days > 0) add(unit(p.days, "DIA", "DIAS"))
        if (p.hours > 0) add(unit(p.hours, "HORA", "HORAS"))
        if (p.minutes > 0 || isEmpty()) add(unit(p.minutes, "MINUTO", "MINUTOS"))
    }

    /** "FALTAM 9 DIAS, 4 HORAS E 25 MINUTOS PARA A OPERAÇÃO ANTI-SPOILER." */
    fun countdownSentence(remaining: Duration): String {
        val p = parts(remaining)
        if (p.days == 0L && p.hours == 0L && p.minutes == 0L) {
            return "FALTAM MENOS DE 60 SEGUNDOS PARA A OPERAÇÃO ANTI-SPOILER."
        }
        val items = phrase(p)
        val verb = if (items.size == 1 && items[0].startsWith("1 ")) "FALTA" else "FALTAM"
        return "$verb ${join(items)} PARA A OPERAÇÃO ANTI-SPOILER."
    }

    /** "PROTEGIDO HÁ 2 DIAS, 3 HORAS E 10 MINUTOS" */
    fun protectedSentence(elapsed: Duration): String {
        val p = parts(elapsed)
        if (p.days == 0L && p.hours == 0L && p.minutes == 0L) return "PROTEGIDO HÁ MENOS DE 1 MINUTO"
        return "PROTEGIDO HÁ ${join(phrase(p))}"
    }
}
