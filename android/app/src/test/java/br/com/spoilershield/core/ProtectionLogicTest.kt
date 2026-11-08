package br.com.spoilershield.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Duration
import java.time.Instant

class ProtectionLogicTest {

    private val start = ShieldDefaults.START_MS
    private val s0 = ShieldState()

    @Test
    fun defaultStartIs18Nov2026_0630_SaoPaulo_which_is_0930Z() {
        assertEquals(Instant.parse("2026-11-18T09:30:00Z").toEpochMilli(), start)
    }

    @Test
    fun waitingBeforeStart_activeAfter() {
        assertEquals(Phase.WAITING, ProtectionLogic.phase(s0, start - 1))
        assertEquals(Phase.ACTIVE, ProtectionLogic.phase(s0, start))
        assertEquals(Phase.ACTIVE, ProtectionLogic.phase(s0, start + 10_000_000))
    }

    @Test
    fun panicActivatesImmediatelyAndKeepsProtectedSince() {
        val now = start - 5_000_000
        val s = ProtectionLogic.panic(s0, now)
        assertEquals(Phase.ACTIVE, ProtectionLogic.phase(s, now))
        assertEquals(now, ProtectionLogic.protectedSinceMs(s, now + 1000))
    }

    @Test
    fun testLasts5MinutesThenReturnsToWaiting() {
        val now = start - 10_000_000
        val s = ProtectionLogic.startTest(s0, now)
        assertEquals(Phase.TESTING, ProtectionLogic.phase(s, now + 1))
        assertTrue(ProtectionLogic.isEnforcing(s, now + 299_999))
        assertEquals(Phase.WAITING, ProtectionLogic.phase(s, now + 300_000))
        assertNull(ProtectionLogic.protectedSinceMs(s, now + 1))
    }

    @Test
    fun endingStopsEnforcementAndDoesNotRetrigger() {
        val s = ProtectionLogic.end(s0, start + 1000)
        assertEquals(Phase.ENDED, ProtectionLogic.phase(s, start + 2000))
        assertFalse(ProtectionLogic.isEnforcing(s, start + 999_999_999))
    }

    @Test
    fun testStillWorksAfterEnded() {
        val ended = ProtectionLogic.end(s0, start + 1000)
        val t = ProtectionLogic.startTest(ended, start + 5000)
        assertEquals(Phase.TESTING, ProtectionLogic.phase(t, start + 6000))
        assertEquals(Phase.ENDED, ProtectionLogic.phase(t, start + 5000 + 300_000))
    }

    @Test
    fun endClearsRunningTest() {
        val t = ProtectionLogic.startTest(s0, 0)
        val e = ProtectionLogic.end(t, 10)
        assertEquals(Phase.ENDED, ProtectionLogic.phase(e, 20))
    }

    @Test
    fun rearmAfterEnd() {
        val ended = ProtectionLogic.end(s0, start + 1000)
        val r = ProtectionLogic.rearm(ended, start + 9000)
        assertEquals(Phase.ACTIVE, ProtectionLogic.phase(r, start + 9000))
    }

    @Test
    fun rescheduleClearsEverythingElse() {
        val s = ProtectionLogic.end(ProtectionLogic.panic(s0, 1), 2)
        val r = ProtectionLogic.reschedule(s, start + 86_400_000)
        assertEquals(Phase.WAITING, ProtectionLogic.phase(r, start))
    }

    @Test
    fun nextBoundary() {
        assertEquals(start, ProtectionLogic.nextBoundaryMs(s0, start - 100))
        assertNull(ProtectionLogic.nextBoundaryMs(s0, start + 100))
        val t = ProtectionLogic.startTest(s0, start - 1_000_000)
        assertEquals(start - 1_000_000 + 300_000, ProtectionLogic.nextBoundaryMs(t, start - 1_000_000))
    }

    @Test
    fun countdownSentenceMatchesRequestedExample() {
        val d = Duration.ofDays(9).plusHours(4).plusMinutes(25).plusSeconds(30)
        assertEquals("FALTAM 9 DIAS, 4 HORAS E 25 MINUTOS PARA A OPERAÇÃO ANTI-SPOILER.", CountdownFormat.countdownSentence(d))
    }

    @Test
    fun countdownSentenceSingularAndEdgeCases() {
        assertEquals("FALTA 1 DIA PARA A OPERAÇÃO ANTI-SPOILER.", CountdownFormat.countdownSentence(Duration.ofDays(1)))
        assertEquals("FALTAM 1 DIA E 5 MINUTOS PARA A OPERAÇÃO ANTI-SPOILER.", CountdownFormat.countdownSentence(Duration.ofDays(1).plusMinutes(5)))
        assertEquals("FALTA 1 MINUTO PARA A OPERAÇÃO ANTI-SPOILER.", CountdownFormat.countdownSentence(Duration.ofSeconds(90)))
        assertEquals("FALTAM MENOS DE 60 SEGUNDOS PARA A OPERAÇÃO ANTI-SPOILER.", CountdownFormat.countdownSentence(Duration.ofSeconds(30)))
    }

    @Test
    fun protectedSentence() {
        val d = Duration.ofDays(2).plusHours(3).plusMinutes(10)
        assertEquals("PROTEGIDO HÁ 2 DIAS, 3 HORAS E 10 MINUTOS", CountdownFormat.protectedSentence(d))
        assertEquals("PROTEGIDO HÁ MENOS DE 1 MINUTO", CountdownFormat.protectedSentence(Duration.ofSeconds(5)))
    }

    @Test
    fun countdownPartsNeverNegative() {
        val p = CountdownFormat.parts(Duration.ofSeconds(-50))
        assertEquals(0L, p.days + p.hours + p.minutes + p.seconds)
    }

    @Test
    fun socialAppPackagesAndDefaults() {
        assertTrue("com.instagram.android" in SocialApp.packagesFor(SocialApp.defaultIds))
        assertFalse("com.discord" in SocialApp.packagesFor(SocialApp.defaultIds))
        assertTrue("com.discord" in SocialApp.packagesFor(SocialApp.defaultIds + "discord"))
    }
}
