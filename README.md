# 🛡️ GTA VI Spoiler Shield

Bloqueia redes sociais a partir de **18/11/2026 às 06h30 (America/Sao_Paulo)** até você encerrar, e deixa você **publicar** (TikTok/Instagram) sem abrir os apps nem ver feed.

```
android/   App Android (Kotlin + Jetpack Compose) – prioridade, projeto completo
ios/       App iPhone (SwiftUI + Screen Time APIs) – bloqueio; Estúdio pendente
docs/      Build, APIs, iOS, LIMITAÇÕES, página de redirect OAuth
```

## Leia primeiro
- `docs/LIMITACOES.md` – o que foi e o que **não** foi verificado (nenhum build foi feito ainda).
- `docs/BUILD_ANDROID.md` – compilar, instalar, primeiro uso.
- `docs/CONFIGURAR_APIS.md` – TikTok/Instagram: credenciais, aprovações, o que a API permite.
- `docs/IOS.md` – Xcode, entitlement da Apple, diferenças.

## Como funciona (Android)
| Peça | Arquivo |
|---|---|
| Fases, contagem, teste, pânico (puro, testado) | `core/ProtectionLogic.kt` |
| Detecção de app (nome do pacote) | `block/ShieldAccessibilityService.kt`, `block/ShieldMonitorService.kt` (UsageStats) |
| Tela de bloqueio | `block/BlockActivity.kt`, `ui/screens/BlockOverlayScreen.kt` |
| Filtro web (VPN local de DNS) | `block/DnsShieldVpnService.kt`, `core/DnsPacket.kt` |
| Agenda | `schedule/ShieldController.kt` (AlarmManager + WorkManager + boot/fuso/relógio) |
| Estúdio Seguro | `studio/*`, `ui/screens/StudioScreen.kt` |
| Segredos | `data/SecureStore.kt` (Keystore AES-GCM) |

O início às 06h30 é um instante absoluto; o bloqueio consulta o relógio real a cada evento, então atraso de alarme não abre brecha.

## Testes
`cd android && ./gradlew testDebugUnitTest` (29 testes do núcleo já passaram fora do Gradle; os de API ainda não rodaram).
