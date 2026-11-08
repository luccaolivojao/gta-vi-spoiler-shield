# Limitações e pendências (lista transparente)

## O que NÃO foi verificado
- **Nenhum build Android ou iOS foi feito.** Sem Android SDK/Maven/Xcode no ambiente de desenvolvimento.
- Verificado de fato: o pacote `core` (lógica de fases, contagem, DNS, matriz de capacidades, PKCE, hashtags) foi compilado com Kotlin 2.0.21 e **29 testes passaram**. Todos os demais arquivos só passaram por checagem de **sintaxe** (sem erros de parse) — erros de tipo/import podem aparecer no Android Studio.
- Testes de `TikTokClient`, `InstagramClient` e `PostJson` (MockWebServer) foram escritos mas **não foram executados**. E mesmo passando, só provam o formato das nossas chamadas, não a aceitação pelas plataformas.
- Nada foi testado em aparelho (bloqueio real, VPN, alarmes, Android 16).

## Dependem de credenciais/aprovação externa
- TikTok `video.publish` (revisão do app) e **auditoria** (sem ela, tudo fica privado).
- App da Meta com Instagram Login; conta Instagram **Profissional**; uso por terceiros exige App Review.
- Apple: entitlement **Family Controls (Distribution)** para app e extensões.

## Sem suporte oficial (não implementado, nunca simulado)
- Stories do TikTok; fotos do TikTok a partir da galeria.
- Fotos do Instagram sem URL pública; contas pessoais do Instagram.
- Agendamento nativo nas redes (o app agenda localmente: aparelho ligado + internet).

## Limites do bloqueio (não é à prova de contorno — por design)
- Você pode desativar, desinstalar, revogar permissões, mudar o relógio do aparelho ou usar outro dispositivo/computador.
- Android: Acessibilidade em APK sideload exige "configurações restritas" (13+). No **Android 17**, o Modo de Proteção Avançada pode impedir serviços de acessibilidade que não sejam ferramentas de acessibilidade — nesse caso use Acesso ao uso + Sobreposição (menos confiável, pode haver um breve instante antes da tela de bloqueio).
- Filtro web: DNS-sobre-HTTPS no navegador e "DNS privado" estrito o contornam; a lista de domínios é melhor esforço; apps com cache/IP direto podem escapar (o bloqueio de app cobre isso).
- **Notificações das redes**: o app não lê notificações (privacidade). Notificações de TikTok/Instagram etc. com spoilers só somem se você as silenciar/bloquear no sistema; o Spoiler Shield nunca exibe notificações externas.
- Spoilers vindos de WhatsApp, e-mail, sites fora da lista, outras pessoas e outros aparelhos **não** são cobertos.
- iOS: seletor de apps é manual; teste de 5 min depende do app aberto.

## Pendências sugeridas
Verificar em aparelhos reais (Samsung/One UI, Android 16+) e compilar o iOS num Mac.
