# Continuidade do SoltaVoz — 09/10/2026

## Estado entregue

- SoltaVoz 1.3.0, versionCode 5. APK de teste em `dist/SoltaVoz-debug.apk`.
- Catálogo unificado: pesquisa local primeiro, YouTube quando não encontra.
- Melhorias de foco, seleção, navegação, cache e animações leves para TV box.
- Painel web com contas, ativação, vencimento, planos, aparelhos e troca de senha.
- Login Android por usuário/senha. Primeiro login exige internet.
- Uma sessão vigente: outro login substitui o anterior. Aparelho offline
  só recebe a revogação ao reconectar ou terminar sua autorização.
- Autorização offline RSA/SHA-256, até 24 horas desde a última validação,
  limitada pelo vencimento. Reinicialização e alterações grandes de relógio
  exigem nova validação. Player e controle QR encerram ao perder acesso.
- Busca YouTube protegida por sessão, chave no servidor, cache compartilhado
  de 15 minutos e orçamento de 80 chamadas por dia UTC.
- Supabase: `https://luqgrtfqlywchlyasaog.supabase.co`.
  Migrações 001–004 aplicadas e `accounts-api` publicada.

## Verificações já realizadas

19 testes de API/assinatura e 23 testes Android passaram. Compilação e lint
debug concluídos: zero erros, 50 avisos. Testes SQL e HTTP reais verificaram
contas, substituição de sessão, bloqueios, vencimento, assinatura e YouTube.
Tela de login e foco conferidos no emulador Android TV. O APK foi verificado
quanto à ausência das chaves privadas locais. Resultados em `reports/unified`.

## Começar na outra máquina

1. Clone o repositório ou execute `git pull --ff-only` em um checkout limpo.
2. Leia este arquivo e `docs/testar-login-tvbox.md`.
3. Para abrir o gerenciador: instale Node.js, entre em `admin-web`, execute
   `npm ci` e `npm start`. Abra `http://127.0.0.1:4173` e use sua conta admin.
4. Para testar o app, instale o APK debug e siga o roteiro do TV box.
5. Para compilar: JDK 17 e SDK Android 35; configure `JAVA_HOME` e
   `local.properties` com o caminho local do SDK. Execute
   `gradlew.bat assembleDebug lintDebug` no Windows.
6. Para administrar/publicar funções: autentique a CLI Supabase na nova
   máquina e vincule o projeto `luqgrtfqlywchlyasaog`. Veja `supabase/README.md`.

## Pendências, em ordem

1. Testar no TV box físico: login, controle/teclado, reprodução local e
   YouTube, reconexão, conta vencida/bloqueada e substituição por outro aparelho.
2. Recuperar o keystore Android original e `signing.properties` por transferência
   privada. Gerar release com `scripts/preparar-release.ps1`. Não criar um
   certificado novo para substituir instalações existentes. O arquivo
   `dist/KaraokeIA-release.apk` continua sendo a versão antiga 1.0.1.
3. Publicar `admin-web` em hospedagem HTTPS. Configurar `ADMIN_PANEL_ORIGINS` nos
   Secrets da API com a origem pública e republicar a função; conferir login,
   CORS e cabeçalhos de segurança na hospedagem escolhida.
4. Adicionar paginação antes de ampliar a distribuição: o painel lista até
   200 contas recentes, 200 planos e 200 aparelhos por conta.
5. Futuro: cobrança e renovação automática por plano. Hoje ativação e
   vencimento são manuais; o plano não calcula automaticamente a validade.
6. Futuro: separar/sincronizar fila, favoritos e histórico por conta. Nesta
   versão, esses dados pertencem à instalação do aparelho.

## Arquivos privados que não vão para o Git

- `.env`, tokens da CLI, `local.properties`, `signing.properties` e keystores.
- `.signing/offline-signing.env`: backup da chave de autorização offline.
  Preserve e transfira por canal privado. Ela já está no Secret
  `OFFLINE_SIGNING_KEY` do Supabase; não precisa estar na máquina para compilar.
- A chave pública correspondente está em `supabase/offline-public-key.txt`.
  Não regenere o par existente: APKs instalados dependem dessa chave pública.
- `YOUTUBE_API_KEY` permanece nos Secrets do Supabase. Não colocar no APK,
  no navegador, em documentação ou em commits.

As migrações foram aplicadas diretamente por SQL, sem reconciliar o histórico
da CLI. Antes de usar `supabase db push`, confira o estado remoto e reconcilie
o histórico; não execute novamente as migrações de criação já aplicadas.

## Referências

- `docs/gerenciamento-de-contas.md`: regras acordadas.
- `docs/testar-api-contas.md`: API de contas.
- `docs/testar-painel-web.md`: gerenciador.
- `docs/testar-login-tvbox.md`: app e tolerância offline.
- `docs/producao.md`: assinatura e release Android.
