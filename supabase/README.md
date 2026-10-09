# Projeto Supabase SoltaVoz

URL pública: https://luqgrtfqlywchlyasaog.supabase.co

## Preparação do projeto

1. Abra o projeto no Dashboard e vá a SQL Editor → New query.
2. Execute `migrations/202610090001_accounts.sql` uma única vez. A migração é
   transacional e não altera tabelas existentes do app anterior.
3. Desative o cadastro público em Authentication → configuração de usuários.
   Clientes serão criados pelo administrador através do servidor.
4. Cadastre `YOUTUBE_API_KEY` em Edge Functions → Secrets, usando uma nova chave
   se a anterior tiver sido exposta. Nenhuma chave privada deve ir para o APK.

A migração prepara contas, planos, aparelhos, sessões e auditoria. Não cria
usuários, não publica funções e não conecta o APK. Execute o teste SQL da pasta
`tests` após a migração; as alterações do teste são revertidas com ROLLBACK.

## Acesso para continuar a publicação

CLI autenticada e repositório vinculado ao projeto em 2026-10-09. O acesso ao
projeto SoltaVoz foi confirmado e o projeto está ativo. A migração inicial e
seus testes foram executados com sucesso pelo usuário no SQL Editor.
Credenciais da CLI ficam no armazenamento local do Supabase; não no Git.
API `accounts-api` publicada e testada no projeto em 2026-10-09. Migração
`202610090002_accounts_api.sql` aplicada pelo ambiente autenticado. Consulte
[a conferência manual](../docs/testar-api-contas.md) para criar seu administrador
e gerenciar contas pela ferramenta de teste. Migração 003 e operações de
aparelhos/planos/senha também aplicadas; painel local em `admin-web/` conectado
à API. Consulte [o teste do painel](../docs/testar-painel-web.md).
As migrações foram aplicadas diretamente (SQL Editor/Management API); antes
de usar `db push`, sincronize o histórico da CLI com o esquema existente para
não tentar recriar essas tabelas.

## Integração atual e próxima etapa

`accounts-api` verifica o token Auth antes de chamar as funções SQL com
`service_role`; o ID do usuário é extraído da identidade verificada.
Somente administradores em `sv_admins` podem criar contas e gerenciar acesso.
Operações de cadastro e troca de senha usam Supabase Auth Admin, nunca tabelas
de senhas. Nunca coloque `service_role` no navegador ou no APK.

O login por nome usa endereço interno resolvido no servidor. A API emite
`offline_lease` assinada com RSA/SHA-256; o Android verifica assinatura,
instalação, sessão, prazo e relógio. JSON das funções SQL isolado não autoriza
uso offline. Preserve o Secret `OFFLINE_SIGNING_KEY` e a chave pública correspondente.

O endpoint `/search` valida a sessão e usa cache/orçamento persistentes no banco
(migração 004). A chave YouTube fica no Secret `YOUTUBE_API_KEY`.
O servidor Node legado não inclui essas regras. O Android 1.3 e o painel local
usam `accounts-api`; veja `docs/testar-login-tvbox.md` para a validação física.
