import Foundation
import FamilyControls
import ManagedSettings
import DeviceActivity

/// Bloqueio via APIs oficiais Screen Time: FamilyControls (autorização), ManagedSettings (escudo),
/// DeviceActivity (agenda). O usuário escolhe os apps no seletor oficial; o app NUNCA vê quais são
/// (só recebe "tokens" opacos) e não consegue ler conteúdo.
@MainActor
final class BlockingManager: ObservableObject {
    @Published var state = SharedStore.loadState()
    @Published var selection = SharedStore.loadSelection() { didSet { SharedStore.saveSelection(selection); reapplyIfNeeded() } }
    @Published var authorized = AuthorizationCenter.shared.authorizationStatus == .approved
    @Published var lastError: String?

    private let store = ManagedSettingsStore()
    private let center = DeviceActivityCenter()

    static let startActivity = DeviceActivityName("spoilerStart")
    static let testActivity = DeviceActivityName("spoilerTest")

    // MARK: Autorização (o usuário aprova e pode revogar em Ajustes → Tempo de Uso)
    func requestAuthorization() async {
        do {
            try await AuthorizationCenter.shared.requestAuthorization(for: .individual)
            authorized = AuthorizationCenter.shared.authorizationStatus == .approved
        } catch {
            authorized = false
            lastError = "Autorização de Tempo de Uso não concedida."
        }
    }

    // MARK: Estado
    private func commit(_ s: ShieldState) {
        state = s; SharedStore.saveState(s)
        syncEnforcement()
    }

    func panic() { var s = state; s.manualStart = Date(); s.endedAt = nil; commit(s) }
    func endMode() { var s = state; s.endedAt = Date(); s.testUntil = nil; commit(s) }
    func rearm() { var s = state; s.endedAt = nil; s.manualStart = Date(); commit(s) }
    func reschedule(_ date: Date) { var s = ShieldState(); s.startAt = date; commit(s) }

    /// Teste: escudo agora. O iOS só aceita janelas de DeviceActivity de ≥ 15 min, então a rede de segurança
    /// que remove o teste é de 15 min; com o app aberto, ele remove em 5 min.
    func startTest() {
        var s = state; s.testUntil = Date().addingTimeInterval(300); commit(s)
        let cal = Calendar.current
        let now = Date(), end = now.addingTimeInterval(16 * 60)
        let sched = DeviceActivitySchedule(
            intervalStart: cal.dateComponents([.hour, .minute, .second], from: now),
            intervalEnd: cal.dateComponents([.hour, .minute, .second], from: end), repeats: false)
        try? center.startMonitoring(Self.testActivity, during: sched)
        DispatchQueue.main.asyncAfter(deadline: .now() + 300) { [weak self] in self?.expireTestIfNeeded() }
    }

    func expireTestIfNeeded() {
        guard let t = state.testUntil, t <= Date() else { return }
        var s = state; s.testUntil = nil; commit(s)
        center.stopMonitoring([Self.testActivity])
    }

    /// Chamado ao abrir o app e quando algo muda: garante que o escudo reflete a fase atual.
    func syncEnforcement() {
        guard authorized else { return }
        if state.isEnforcing() { applyShield() } else { store.clearAllSettings() }
        scheduleStartIfWaiting()
    }

    private func reapplyIfNeeded() { if state.isEnforcing() && authorized { applyShield() } }

    private func applyShield() {
        let apps = selection.applicationTokens, cats = selection.categoryTokens, webs = selection.webDomainTokens
        store.shield.applications = apps.isEmpty ? nil : apps
        store.shield.applicationCategories = cats.isEmpty ? nil : .specific(cats)
        store.shield.webDomains = webs.isEmpty ? nil : webs
        store.webContent.blockedByFilter = .specific(Set(Shared.blockedDomains.map { WebDomain(domain: $0) }))
    }

    /// A extensão DeviceActivityMonitor aplica o escudo no horário, mesmo com o app fechado.
    private func scheduleStartIfWaiting() {
        center.stopMonitoring([Self.startActivity])
        guard state.phase() == .waiting else { return }
        var cal = Calendar(identifier: .gregorian); cal.timeZone = TimeZone(identifier: "America/Sao_Paulo")!
        let startC = cal.dateComponents([.year, .month, .day, .hour, .minute], from: state.startAt)
        let endC = cal.dateComponents([.year, .month, .day, .hour, .minute], from: state.startAt.addingTimeInterval(23 * 3600))
        let sched = DeviceActivitySchedule(intervalStart: startC, intervalEnd: endC, repeats: false)
        do { try center.startMonitoring(Self.startActivity, during: sched) }
        catch { lastError = "Não foi possível agendar o início automático." }
    }
}
