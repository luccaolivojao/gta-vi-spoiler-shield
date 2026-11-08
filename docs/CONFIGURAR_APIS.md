# Configurar as APIs oficiais (TikTok e Instagram)

Pesquisa feita em nov/2026 nas documentações oficiais. As plataformas mudam regras; confirme nos links.

## 0. Página de redirecionamento (necessária para os dois)
As duas plataformas exigem *redirect URI* em **https**. Hospede `docs/oauth-redirect.html`
(ex.: GitHub Pages: `https://SEUUSUARIO.github.io/shield/oauth-redirect.html`). Ela só repassa `code`/`state` ao app via `spoilershield://oauth`.

## 1. TikTok — Content Posting API
1. Crie um app em <https://developers.tiktok.com>. Adicione os produtos **Login Kit** e **Content Posting API**.
2. Em Login Kit, registre a *redirect URI* do passo 0.
3. Escopos: `user.info.basic` e **`video.publish`** (Direct Post). Solicite aprovação do escopo (revisão do app: demo em vídeo, política de privacidade, etc.).
4. No app: ESTÚDIO → CREDENCIAIS → TikTok: Client Key, Client Secret e a URL do passo 0 → CONECTAR.

**O que é fato da documentação:**
- Apps **não auditados** só publicam em modo privado (`SELF_ONLY`); contas públicas recebem `unaudited_client_can_only_post_to_private_accounts`. Publicações feitas antes da auditoria **continuam privadas** depois dela.
- Para postar publicamente é preciso passar na **auditoria** da Content Posting API (separada da aprovação do escopo; leva semanas).
- Antes de postar o app consulta `creator_info/query` (opções de privacidade, limites) e segue as diretrizes de UX (visibilidade sem padrão, comentário/dueto/stitch desmarcados, aviso de música).
- Limite: 6 requisições/min por token.
- **Fotos:** só `PULL_FROM_URL` com domínio verificado → **não implementado** (não há upload da galeria).
- **Stories:** a API **não** documenta Stories → **não existe** no app.
- Alternativa sem auditoria: modo *upload para a caixa de entrada* (`video.upload`), que deixa um rascunho que você finaliza **dentro do app TikTok** (isso quebra o objetivo de não abrir a rede; por isso não é usado).

**Limitação do OAuth no celular (a validar):** o app usa fluxo web + PKCE com a página https do passo 0. O app guarda o *client secret* no aparelho (Keystore) — aceitável só porque o APK é seu e nunca distribuído. Se o portal rejeitar `code_challenge`, remova-o em `OAuthUrls.tiktok`.

## 2. Instagram — API with Instagram Login (Meta)
1. <https://developers.facebook.com> → crie um app (tipo Business) → produto **Instagram** → *API setup with Instagram login*.
2. Permissões: `instagram_business_basic` e `instagram_business_content_publish`.
3. Adicione sua conta Instagram como testador/função no app (modo desenvolvimento permite publicar na sua própria conta sem revisão; uso por terceiros exige *App Review*/Advanced Access).
4. Registre a redirect URI do passo 0 em *Business login settings*.
5. No app: ESTÚDIO → CREDENCIAIS → Instagram: App ID, App Secret, URL do passo 0 → CONECTAR.

**Tipos de conta e publicação (doc oficial):**
| Conta | Publica via API? |
|---|---|
| Pessoal | **Não** |
| Profissional – Criador (`MEDIA_CREATOR`) | Sim |
| Profissional – Comercial (`BUSINESS`) | Sim |

| Formato | Suporte | Como |
|---|---|---|
| Reel (vídeo) | Sim | `media_type=REELS`, upload resumível do aparelho |
| Story em vídeo | Sim | `media_type=STORIES`, upload resumível |
| Story em foto | Sim, **com URL pública** | `image_url` |
| Foto no feed | Sim, **com URL pública (JPEG)** | `image_url` |
| Vídeo de feed | Publicado como Reel | — |
- Limite: 100 publicações/24h via API (a doc também cita 50 para carrossel; o app lê a cota real em `content_publishing_limit`).
- **Sem agendamento nativo**: o agendamento é do app (WorkManager) e exige o aparelho ligado e com internet.
- **Não confirmado por mim:** a documentação do upload resumível está num trecho que cita apps com Facebook Login; não pude testar se o token do Instagram Login o aceita. Se falhar, a alternativa oficial é `video_url` pública.
- Token: longo (60 dias) e renovado automaticamente quando faltam < 10 dias.
- Versão da Graph API usada: `v27.0` (a mostrada na doc em nov/2026); troque em `InstagramClient` se necessário.

## 3. Estado de cada integração nesta entrega
| Integração | Estado |
|---|---|
| TikTok vídeo (Direct Post) | Código completo; **não testado contra o TikTok real**; depende de credenciais, aprovação de `video.publish` e (para público) auditoria |
| Instagram Reel/Story vídeo | Código completo; **não testado contra a Meta real**; depende de app Meta + conta profissional |
| Instagram foto/Story foto | Código completo; exige URL pública própria |
| TikTok foto / Stories de TikTok | **Sem suporte oficial / não implementado** |
