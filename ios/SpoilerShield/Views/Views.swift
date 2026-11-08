import SwiftUI
import FamilyControls

struct NeonCard<Content: View>: View {
    var glow: Color = Neon.red
    @ViewBuilder var content: Content
    var body: some View {
        VStack(alignment: .leading, spacing: 8) { content }
            .padding(16).frame(maxWidth: .infinity, alignment: .leading)
            .background(Neon.card)
            .overlay(RoundedRectangle(cornerRadius: 18).stroke(glow.opacity(0.55), lineWidth: 1))
            .clipShape(RoundedRectangle(cornerRadius: 18))
            .shadow(color: glow.opacity(0.25), radius: 8)
    }
}

struct SectionTitle: View {
    let text: String
    init(_ t: String) { text = t }
    var body: some View { Text(text).font(.caption.weight(.black)).tracking(1.5).foregroundColor(Neon.red) }
}

struct BigButton: View {
    let title: String; var color: Color = Neon.red; let action: () -> Void
    var body: some View {
        Button(action: action) {
            Text(title).font(.headline.weight(.heavy)).frame(maxWidth: .infinity).padding(.vertical, 16)
                .background(color).foregroundColor(.white).clipShape(RoundedRectangle(cornerRadius: 14))
        }
    }
}

// MARK: INÍCIO
struct HomeView: View {
    @EnvironmentObject var m: BlockingManager
    @State private var confirmPanic = false

    var body: some View {
        ScrollView {
            TimelineView(.periodic(from: .now, by: 1)) { ctx in
                let now = ctx.date
                let phase = m.state.phase(at: now)
                VStack(spacing: 16) {
                    Text("GTA VI SPOILER SHIELD").font(.subheadline.weight(.black)).tracking(3).foregroundColor(Neon.red)
                    Image(systemName: "lock.shield.fill").font(.system(size: 90))
                        .foregroundColor(phase == .active ? Neon.green : Neon.red)
                    NeonCard(glow: phase == .active ? Neon.green : Neon.red) {
                        switch phase {
                        case .waiting:
                            let secs = m.state.startAt.timeIntervalSince(now)
                            let p = CountdownText.parts(secs)
                            Text(CountdownText.countdown(secs)).font(.headline).multilineTextAlignment(.center).frame(maxWidth: .infinity)
                            Text(String(format: "%02d : %02d : %02d : %02d", p.d, p.h, p.m, p.s))
                                .font(.system(size: 30, weight: .black, design: .monospaced)).frame(maxWidth: .infinity)
                        case .active:
                            Text("🛡️ MODO ANTI-SPOILER ATIVADO").font(.title3.weight(.black)).foregroundColor(Neon.green).frame(maxWidth: .infinity)
                            if let since = m.state.protectedSince(at: now) {
                                Text(CountdownText.protected(now.timeIntervalSince(since))).font(.headline).frame(maxWidth: .infinity)
                            }
                        case .testing:
                            Text("🧪 TESTE DE BLOQUEIO ATIVO").font(.title3.weight(.black)).foregroundColor(Neon.amber).frame(maxWidth: .infinity)
                            Text("Tente abrir uma rede bloqueada.").frame(maxWidth: .infinity)
                        case .ended:
                            Text("MODO ANTI-SPOILER ENCERRADO").font(.title3.weight(.black)).foregroundColor(.gray).frame(maxWidth: .infinity)
                        }
                    }
                    NeonCard(glow: Neon.cyan) {
                        SectionTitle("SITUAÇÃO DA PROTEÇÃO")
                        Label(m.authorized ? "Tempo de Uso autorizado" : "Falta autorizar o Tempo de Uso",
                              systemImage: m.authorized ? "checkmark.circle.fill" : "exclamationmark.triangle.fill")
                            .foregroundColor(m.authorized ? Neon.green : Neon.amber)
                        let n = m.selection.applicationTokens.count + m.selection.categoryTokens.count + m.selection.webDomainTokens.count
                        Text(n == 0 ? "Nenhum app/categoria escolhido ainda (aba BLOQUEIO)." : "Itens escolhidos para bloquear: \(n)")
                            .font(.footnote).foregroundColor(.gray)
                    }
                    if phase != .active {
                        BigButton(title: "ATIVAR BLOQUEIO TOTAL AGORA") { confirmPanic = true }
                    }
                    if phase == .waiting || phase == .ended {
                        BigButton(title: "TESTAR POR 5 MINUTOS", color: Neon.cyan.opacity(0.7)) { m.startTest() }
                        Text("No iPhone o teste dura 5 min com o app aberto (a rede de segurança do sistema remove em até 16 min).")
                            .font(.caption).foregroundColor(.gray)
                    }
                }.padding()
            }
        }
        .background(Neon.black.ignoresSafeArea())
        .alert("Ativar bloqueio total agora?", isPresented: $confirmPanic) {
            Button("ATIVAR", role: .destructive) { m.panic() }
            Button("Cancelar", role: .cancel) {}
        } message: { Text("Os apps escolhidos serão bloqueados imediatamente até você encerrar em Configurações.") }
    }
}

// MARK: BLOQUEIO
private let startFormatter: DateFormatter = {
    let f = DateFormatter()
    f.locale = Locale(identifier: "pt_BR")
    f.timeZone = TimeZone(identifier: "America/Sao_Paulo")
    f.dateFormat = "dd/MM/yyyy 'às' HH:mm"
    return f
}()

struct BlockView: View {
    @EnvironmentObject var m: BlockingManager
    @State private var picking = false
    @State private var showDatePicker = false
    @State private var newDate = Date()

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                NeonCard {
                    SectionTitle("AGENDAMENTO")
                    Text("Início: " + startFormatter.string(from: m.state.startAt))
                        .font(.headline)
                    Text("Fuso: America/Sao_Paulo. Duração: até você encerrar.").font(.caption).foregroundColor(.gray)
                    Button("ALTERAR DATA E HORA") { newDate = m.state.startAt; showDatePicker = true }.tint(Neon.cyan)
                }
                NeonCard {
                    SectionTitle("REDES BLOQUEADAS")
                    Text("No iOS você escolhe os apps no seletor oficial da Apple (Tempo de Uso). O iOS não deixa um app listar nem pré-selecionar outros apps; marque TikTok, Instagram, YouTube, X, Facebook, Threads, Reddit, Snapchat, Twitch (e Discord, se quiser) — ou a categoria \"Redes Sociais\".")
                        .font(.footnote).foregroundColor(.gray)
                    Button("ESCOLHER APPS E CATEGORIAS") { picking = true }
                        .buttonStyle(.borderedProminent).tint(Neon.red)
                        .familyActivityPicker(isPresented: $picking, selection: $m.selection)
                    Text("Sites das redes também são filtrados no Safari (melhor esforço).").font(.caption).foregroundColor(.gray)
                }
                NeonCard(glow: Neon.cyan) {
                    SectionTitle("PERMISSÃO")
                    Text(m.authorized ? "✅ Autorizado" : "Falta autorizar").font(.headline)
                    Text("Usa FamilyControls (modo Individual). Você pode revogar a qualquer momento em Ajustes → Tempo de Uso.")
                        .font(.caption).foregroundColor(.gray)
                    if !m.authorized { Button("AUTORIZAR TEMPO DE USO") { Task { await m.requestAuthorization() } }.tint(Neon.green) }
                    if let e = m.lastError { Text(e).font(.caption).foregroundColor(Neon.amber) }
                }
            }.padding()
        }
        .background(Neon.black.ignoresSafeArea())
        .sheet(isPresented: $showDatePicker) {
            VStack {
                DatePicker("Início", selection: $newDate).datePickerStyle(.graphical)
                    .environment(\.timeZone, TimeZone(identifier: "America/Sao_Paulo")!)
                BigButton(title: "SALVAR") { m.reschedule(newDate); showDatePicker = false }
            }.padding().presentationDetents([.large])
        }
    }
}

// MARK: CONFIGURAÇÕES
struct SettingsView: View {
    @EnvironmentObject var m: BlockingManager
    @State private var confirmEnd = false

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                NeonCard(glow: Neon.cyan) {
                    SectionTitle("TESTES")
                    BigButton(title: "TESTAR BLOQUEIO POR 5 MINUTOS", color: Neon.cyan.opacity(0.7)) { m.startTest() }
                }
                NeonCard {
                    SectionTitle("ENCERRAR MODO ANTI-SPOILER")
                    Text("A decisão é sua. Ao encerrar, os escudos são removidos na hora.").font(.footnote).foregroundColor(.gray)
                    switch m.state.phase() {
                    case .active, .testing: BigButton(title: "ENCERRAR MODO ANTI-SPOILER") { confirmEnd = true }
                    case .ended: BigButton(title: "REATIVAR PROTEÇÃO", color: Neon.red.opacity(0.7)) { m.rearm() }
                    case .waiting: Text("A proteção ainda não começou.").font(.footnote).foregroundColor(.gray)
                    }
                }
                NeonCard(glow: .gray) {
                    SectionTitle("PRIVACIDADE")
                    Text("• Não lê mensagens nem o conteúdo das telas.\n• Não vê quais apps você escolheu (a Apple entrega tokens opacos).\n• Não acessa feeds, vídeos ou notícias de terceiros.\n• Não pede nem guarda senhas.\n• Tudo fica neste aparelho.")
                        .font(.footnote).foregroundColor(.gray)
                }
            }.padding()
        }
        .background(Neon.black.ignoresSafeArea())
        .alert("Encerrar o modo anti-spoiler?", isPresented: $confirmEnd) {
            Button("ENCERRAR", role: .destructive) { m.endMode() }
            Button("Continuar protegido", role: .cancel) {}
        }
    }
}
