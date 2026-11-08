# Compilar e instalar o APK (Android)

> **Aviso honesto:** o código foi escrito num ambiente sem Android SDK nem acesso ao Maven do Google,
> então **eu não compilei o app Android nem rodei em aparelho**. Só o núcleo lógico (pacote `core`) foi
> compilado e testado de verdade. Espere pequenos ajustes de compilação na primeira vez (versões, imports).

## Requisitos
- Android Studio (versão recente) com Android SDK 36 (Android 16) instalado
- JDK 17+ (o Android Studio já traz)

## Passo a passo
1. Abra a pasta `android/` no Android Studio → *Sync*. Se pedir, aceite atualizar versões do `gradle/libs.versions.toml`.
2. Rodar testes: `./gradlew testDebugUnitTest`
3. APK de debug (instalável direto): `./gradlew assembleDebug`
   → `app/build/outputs/apk/debug/app-debug.apk`
4. Instalar: copie o APK para o celular e abra, ou `adb install -r app-debug.apk`
   (ative "Instalar apps desconhecidos" para o app usado para abrir o arquivo).
5. APK de release: crie uma keystore (`keytool -genkey -v -keystore meu.jks -alias shield -keyalg RSA -keysize 2048 -validity 10000`),
   configure `signingConfigs` no `app/build.gradle.kts` e rode `./gradlew assembleRelease`.

## Primeiro uso (ordem recomendada, bem antes do dia 18)
1. Aba **BLOQUEIO** → autorize **Acessibilidade**. Android 13+ com APK instalado fora da Play Store: *Informações do app → ⋮ → Permitir configurações restritas* e volte.
2. Autorize **Alarmes exatos**, **Notificações** e **Sem restrição de bateria**.
3. (Opcional) **Filtro web** → aceite o diálogo de VPN do Android.
4. Aba **CONFIG.** → *Testar bloqueio por 5 minutos* → abra o Instagram/TikTok e confirme que a tela vermelha aparece.
5. Aba **ESTÚDIO** → configure credenciais e conecte as contas (veja `CONFIGURAR_APIS.md`). **Faça isso antes do dia 18.**

## Por que não Play Store
O serviço de Acessibilidade usado para detectar apps não é uma "ferramenta de acessibilidade"; a política do Google Play não permite esse uso. Distribua só como APK próprio.
