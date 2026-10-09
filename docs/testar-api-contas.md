# Testar a API de contas (etapa 1)

API publicada em:
`https://luqgrtfqlywchlyasaog.supabase.co/functions/v1/accounts-api`

## Primeiro administrador

No projeto Supabase, abra Authentication → Users → Add user → Create new user.
Use seu e-mail e uma senha forte; confirme o usuário (Auto Confirm).
A conta do Dashboard Supabase não é automaticamente uma conta do aplicativo.
Copie o UUID desse usuário e execute no SQL Editor, substituindo o exemplo:

```sql
insert into public.sv_admins(user_id)
values ('UUID-DO-SEU-USUARIO-AUTH')
on conflict do nothing;
```

Somente usuários nessa tabela têm permissão de administração. Não envie a
senha na conversa. Desative cadastros públicos no painel de Authentication;
a API administrativa cria os usuários pelo servidor.

## Conferência manual

Na raiz do repositório, execute em um terminal PowerShell local:

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File scripts/gerenciar-contas.ps1
```

O Bypass vale somente para esse processo. O script pede e-mail e senha de admin
com entrada mascarada; credenciais não são gravadas em arquivos ou impressas.

1. Liste contas e planos (inicialmente podem estar vazios).
2. Crie um plano e anote o ID.
3. Crie uma conta de teste com nome de usuário e senha de pelo menos 12 caracteres.
   Ela começa bloqueada para evitar ativação acidental.
4. Altere o acesso: ative, informe dias de validade e o ID do plano.
5. Liste novamente para conferir plano, vencimento e estado.
6. Bloqueie a conta e confirme seu estado na listagem.

Se aparecer `admin_required`, confirme que o UUID colocado em `sv_admins`
pertence ao mesmo usuário usado no login. O token de administrador tem prazo;
se expirar, encerre o script e entre novamente.

## Contrato da API

Todas as rotas abaixo usam POST e JSON. Rotas protegidas exigem
`Authorization: Bearer <access_token>` de Supabase Auth.

| Rota | Dados | Resultado |
| --- | --- | --- |
| `/admin/login` | email, password | tokens, somente para admin |
| `/admin/me` | objeto vazio | identidade verificada do admin |
| `/admin/accounts` | objeto vazio | até 200 contas mais recentes |
| `/admin/create-account` | username, display_name, password, enabled, expires_at, plan_id | conta criada |
| `/admin/set-access` | user_id, enabled, expires_at, plan_id | altera acesso e revoga sessões |
| `/admin/plans` | objeto vazio | até 200 planos |
| `/admin/create-plan` | name | novo plano |
| `/admin/devices` | user_id | até 200 aparelhos da conta |
| `/login` | username, password, installation_id (UUID), device_label | tokens e sessão nova |
| `/validate` | session_id, installation_id | sessão validada |
| `/refresh` | refresh_token, session_id, installation_id | tokens renovados sem criar nova sessão |
| `/logout` | session_id, installation_id | revoga somente a sessão indicada |

`expires_at: null` significa prazo indeterminado; datas usam ISO 8601 com fuso.
`plan_id: null` significa sem plano. Nas operações de acesso esses dois campos
devem ser enviados explicitamente. O nome de usuário é convertido para
minúsculas; clientes não precisam fornecer e-mail. O endereço interno em
`accounts.soltavoz.invalid` é exclusivo da autenticação, sem envio de mensagens.

O servidor recusa explicitamente sessões substituídas, contas bloqueadas e
contas vencidas. Renovar tokens não reativa uma sessão substituída.
Limites persistentes: 10 tentativas/minuto por identidade e 120/minuto no
projeto, compartilhadas entre login e refresh. Erros não revelam credenciais.

## Verificações executadas

13 testes locais do handler, checagem Deno e dois testes SQL transacionais no
projeto real. Teste HTTP completo com contas temporárias: admin cria conta,
login em dois aparelhos substitui o primeiro, refresh preserva a sessão,
logout antigo não encerra a nova, cliente não administra, bloqueio e vencimento
impedem uso. Os usuários/planos/auditoria temporários são removidos ao final.

## Atualização: painel web

O painel web local foi implementado na etapa 2. Veja
[como testar o painel](testar-painel-web.md). A API também oferece
`/admin/refresh`, `/admin/set-plan`, `/admin/set-device` e `/admin/reset-password`.
As rotas de mutação exigem identidade de administrador verificada.

## Limites da integração Android

O SoltaVoz 1.3 conecta o login Android. Login, validação e refresh retornam
`offline_lease`, assinada pelo servidor e verificada no APK. Os prazos JSON
isolados não autorizam uso offline. Consulte `docs/testar-login-tvbox.md`.

A busca YouTube protegida por sessão será conectada na próxima etapa. A gestão
de aparelhos e redefinição de senha estão disponíveis na API/painel web.
CORS está configurado para o painel local na porta 4173; uma origem pública
precisa ser cadastrada em `ADMIN_PANEL_ORIGINS` antes de hospedar o painel.
