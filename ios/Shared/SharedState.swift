import Foundation
import FamilyControls

/// Estado compartilhado entre o app e as extensões via App Group. Espelha ProtectionLogic.kt (Android).
enum Shared {
    static let groupID = "group.br.com.spoilershield"
    static var defaults: UserDefaults { UserDefaults(suiteName: groupID) ?? .standard }

    /// 18/11/2026 06:30 em America/Sao_Paulo (instante absoluto).
    static let defaultStart: Date = {
        var c = DateComponents()
        c.calendar = Calendar(identifier: .gregorian)
        c.timeZone = TimeZone(identifier: "America/Sao_Paulo")
        c.year = 2026; c.month = 11; c.day = 18; c.hour = 6; c.minute = 30
        return c.date!
    }()

    /// Domínios para o filtro de conteúdo web do Safari (melhor esforço).
    static let blockedDomains = [
        "tiktok.com", "instagram.com", "youtube.com", "youtu.be", "x.com", "twitter.com",
        "facebook.com", "threads.net", "threads.com", "reddit.com", "snapchat.com", "twitch.tv",
    ]
}

enum Phase { case waiting, active, testing, ended }

struct ShieldState: Codable {
    var startAt: Date = Shared.defaultStart
    var manualStart: Date?
    var testUntil: Date?
    var endedAt: Date?

    func phase(at now: Date = Date()) -> Phase {
        let testing = (testUntil ?? .distantPast) > now
        if endedAt != nil { return testing ? .testing : .ended }
        if (manualStart ?? .distantFuture) <= now || startAt <= now { return .active }
        return testing ? .testing : .waiting
    }
    func isEnforcing(at now: Date = Date()) -> Bool { let p = phase(at: now); return p == .active || p == .testing }
    func protectedSince(at now: Date = Date()) -> Date? {
        guard phase(at: now) == .active else { return nil }
        return [startAt, manualStart].compactMap { $0 }.filter { $0 <= now }.min()
    }
}

enum SharedStore {
    private static let stateKey = "shield_state_v1"
    private static let selectionKey = "family_selection_v1"

    static func loadState() -> ShieldState {
        guard let d = Shared.defaults.data(forKey: stateKey),
              let s = try? JSONDecoder().decode(ShieldState.self, from: d) else { return ShieldState() }
        return s
    }
    static func saveState(_ s: ShieldState) {
        if let d = try? JSONEncoder().encode(s) { Shared.defaults.set(d, forKey: stateKey) }
    }
    static func loadSelection() -> FamilyActivitySelection {
        guard let d = Shared.defaults.data(forKey: selectionKey),
              let s = try? JSONDecoder().decode(FamilyActivitySelection.self, from: d) else { return FamilyActivitySelection() }
        return s
    }
    static func saveSelection(_ s: FamilyActivitySelection) {
        if let d = try? JSONEncoder().encode(s) { Shared.defaults.set(d, forKey: selectionKey) }
    }
}

enum CountdownText {
    static func parts(_ seconds: TimeInterval) -> (d: Int, h: Int, m: Int, s: Int) {
        let t = max(0, Int(seconds))
        return (t / 86_400, (t % 86_400) / 3_600, (t % 3_600) / 60, t % 60)
    }
    private static func unit(_ n: Int, _ one: String, _ many: String) -> String { "\(n) \(n == 1 ? one : many)" }
    private static func join(_ p: [String]) -> String {
        p.count <= 1 ? (p.first ?? "") : p.dropLast().joined(separator: ", ") + " E " + p.last!
    }
    private static func items(_ x: (d: Int, h: Int, m: Int, s: Int)) -> [String] {
        var r: [String] = []
        if x.d > 0 { r.append(unit(x.d, "DIA", "DIAS")) }
        if x.h > 0 { r.append(unit(x.h, "HORA", "HORAS")) }
        if x.m > 0 || r.isEmpty { r.append(unit(x.m, "MINUTO", "MINUTOS")) }
        return r
    }
    static func countdown(_ seconds: TimeInterval) -> String {
        let x = parts(seconds)
        if x.d == 0 && x.h == 0 && x.m == 0 { return "FALTAM MENOS DE 60 SEGUNDOS PARA A OPERAÇÃO ANTI-SPOILER." }
        let it = items(x)
        let verb = (it.count == 1 && it[0].hasPrefix("1 ")) ? "FALTA" : "FALTAM"
        return "\(verb) \(join(it)) PARA A OPERAÇÃO ANTI-SPOILER."
    }
    static func protected(_ seconds: TimeInterval) -> String {
        let x = parts(seconds)
        if x.d == 0 && x.h == 0 && x.m == 0 { return "PROTEGIDO HÁ MENOS DE 1 MINUTO" }
        return "PROTEGIDO HÁ \(join(items(x)))"
    }
}
