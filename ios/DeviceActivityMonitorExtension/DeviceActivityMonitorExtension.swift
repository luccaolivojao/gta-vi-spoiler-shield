import DeviceActivity
import ManagedSettings
import FamilyControls

/// Roda em segundo plano (mesmo com o app fechado) quando a janela agendada começa.
class DeviceActivityMonitorExtension: DeviceActivityMonitor {
    private let store = ManagedSettingsStore()

    override func intervalDidStart(for activity: DeviceActivityName) {
        super.intervalDidStart(for: activity)
        guard SharedStore.loadState().isEnforcing() else { return }
        let sel = SharedStore.loadSelection()
        store.shield.applications = sel.applicationTokens.isEmpty ? nil : sel.applicationTokens
        store.shield.applicationCategories = sel.categoryTokens.isEmpty ? nil : .specific(sel.categoryTokens)
        store.shield.webDomains = sel.webDomainTokens.isEmpty ? nil : sel.webDomainTokens
        store.webContent.blockedByFilter = .specific(Set(Shared.blockedDomains.map { WebDomain(domain: $0) }))
    }

    override func intervalDidEnd(for activity: DeviceActivityName) {
        super.intervalDidEnd(for: activity)
        // Só o TESTE é desfeito aqui. A proteção real nunca é removida por uma extensão:
        // somente o usuário, ao encerrar o modo anti-spoiler no app.
        if activity.rawValue == "spoilerTest" {
            var s = SharedStore.loadState()
            if let t = s.testUntil, t <= Date() { s.testUntil = nil; SharedStore.saveState(s) }
            if !SharedStore.loadState().isEnforcing() { store.clearAllSettings() }
        }
    }
}
