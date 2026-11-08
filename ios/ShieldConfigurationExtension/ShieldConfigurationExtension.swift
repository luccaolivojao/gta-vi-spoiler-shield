import ManagedSettings
import ManagedSettingsUI
import UIKit

/// Tela exibida pelo iOS no lugar do app/site bloqueado. Texto estático: o iOS não informa e este
/// código não usa o nome do app bloqueado nem qualquer conteúdo externo.
class ShieldConfigurationExtension: ShieldConfigurationDataSource {
    private func config() -> ShieldConfiguration {
        ShieldConfiguration(
            backgroundBlurStyle: .systemUltraThinMaterialDark,
            backgroundColor: UIColor(red: 0.02, green: 0.02, blue: 0.03, alpha: 1),
            icon: UIImage(systemName: "lock.shield.fill"),
            title: .init(text: "🛑 CUIDADO! ÁREA COM RISCO DE SPOILERS DO GTA VI!", color: UIColor(red: 1, green: 0.09, blue: 0.27, alpha: 1)),
            subtitle: .init(text: "Seu modo anti-spoiler está ativado.\n\nVocê chegou até aqui sem spoilers. Não estrague a surpresa!\n\n🔒 Acesso temporariamente bloqueado.", color: .white),
            primaryButtonLabel: .init(text: "VOLTAR À SEGURANÇA", color: .white),
            primaryButtonBackgroundColor: UIColor(red: 1, green: 0.09, blue: 0.27, alpha: 1)
        )
    }
    override func configuration(shielding application: Application) -> ShieldConfiguration { config() }
    override func configuration(shielding application: Application, in category: ActivityCategory) -> ShieldConfiguration { config() }
    override func configuration(shielding webDomain: WebDomain) -> ShieldConfiguration { config() }
    override func configuration(shielding webDomain: WebDomain, in category: ActivityCategory) -> ShieldConfiguration { config() }
}
