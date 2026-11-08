import SwiftUI

@main
struct SpoilerShieldApp: App {
    @StateObject private var manager = BlockingManager()
    @Environment(\.scenePhase) private var scenePhase

    var body: some Scene {
        WindowGroup {
            RootView()
                .environmentObject(manager)
                .preferredColorScheme(.dark)
                .onChange(of: scenePhase) { phase in
                    if phase == .active { manager.syncEnforcement(); manager.expireTestIfNeeded() }
                }
        }
    }
}

enum Neon {
    static let black = Color(red: 0.02, green: 0.02, blue: 0.03)
    static let card = Color(red: 0.08, green: 0.08, blue: 0.10)
    static let red = Color(red: 1, green: 0.09, blue: 0.27)
    static let cyan = Color(red: 0, green: 0.9, blue: 1)
    static let green = Color(red: 0, green: 0.9, blue: 0.46)
    static let amber = Color(red: 1, green: 0.7, blue: 0)
}

struct RootView: View {
    var body: some View {
        TabView {
            HomeView().tabItem { Label("INÍCIO", systemImage: "house.fill") }
            BlockView().tabItem { Label("BLOQUEIO", systemImage: "lock.fill") }
            SettingsView().tabItem { Label("CONFIG.", systemImage: "gearshape.fill") }
        }
        .tint(Neon.red)
        .background(Neon.black)
    }
}
