# iPhone (SwiftUI)

> **Não compilado:** este ambiente é Linux; o código Swift nunca foi compilado nem rodado.

## Gerar e rodar
1. Mac com Xcode 15+; `brew install xcodegen`; em `ios/`: `xcodegen generate`.
2. Em cada um dos 4 targets, escolha seu **Team**. Ajuste os bundle IDs/App Group (`group.br.com.spoilershield`) se já existirem na sua conta.
3. Rode em **iPhone real** (o Simulador não suporta Screen Time/FamilyControls).

## Aprovação da Apple (importante)
- A capability **Family Controls** em *desenvolvimento* funciona com conta paga.
- Para **TestFlight/App Store/Ad Hoc** é preciso pedir o entitlement **Family Controls (Distribution)** em
  <https://developer.apple.com/contact/request/family-controls-distribution> — **para o app e também para cada extensão** (monitor, shield, action). Fóruns de 2026 relatam semanas sem resposta; peça cedo.
- Sem esse entitlement de distribuição, só dá para instalar via Xcode com cabo (certificado de desenvolvimento, expira em 7 dias na conta gratuita — que nem tem Family Controls; é preciso conta paga).

## Frameworks usados
`FamilyControls` (autorização `.individual` + `FamilyActivityPicker`), `ManagedSettings` (escudo em apps, categorias, domínios), `DeviceActivity` (início agendado em extensão), `ManagedSettingsUI` (`ShieldConfiguration` = tela de bloqueio), `ShieldAction` (botão "VOLTAR À SEGURANÇA" → fecha o escudo).

## Diferenças em relação ao Android
| Recurso | Android | iPhone |
|---|---|---|
| Escolher redes | Lista fixa de pacotes | Seletor oficial da Apple; o app não vê nem pré-seleciona (tokens opacos) |
| Tela de bloqueio | Tela própria com o texto pedido | `ShieldConfiguration` (texto, ícone e cores customizáveis; layout é da Apple) |
| Início às 06h30 | Alarme + relógio real | Extensão `DeviceActivityMonitor` na data/hora; ao abrir o app ele também reaplica |
| Teste de 5 min | Exato | Janela mínima do sistema = 15 min; o app remove em 5 min com ele aberto, senão a extensão em ~16 min |
| Navegador | Filtro DNS local (VPN) | `webContent.blockedByFilter` (Safari/WebKit) |
| Revogar | Desligar permissões | Ajustes → Tempo de Uso (modo Individual permite revogar) |
| Estúdio Seguro | Implementado | **Pendente** (aba mostra aviso honesto; nada finge publicar) |
