# Painel web SoltaVoz — etapa 2

O painel usa a API publicada no seu Supabase e o administrador criado na etapa
anterior. Não é necessário instalar banco ou copiar chaves para o navegador.

## Abrir

Na raiz do projeto:

```powershell
node admin-web/serve.mjs
```

Abra http://127.0.0.1:4173 e entre com o e-mail e a senha do administrador.
Mantenha o terminal aberto; Ctrl+C encerra o servidor. Se ele já estiver rodando,
abra apenas o endereço. O servidor atende somente neste computador.

Para futuras hospedagens estáticas, publique apenas `index.html`, `app.mjs`,
`style.css` e `brand.svg`. O servidor local e as dependências de testes não
fazem parte do site. Configure os cabeçalhos de segurança equivalentes aos de
`serve.mjs` e cadastre a origem HTTPS em `ADMIN_PANEL_ORIGINS` nos Secrets da
Edge Function. Atualmente estão permitidos apenas localhost/127.0.0.1:4173.
O painel ainda não tem endereço público de hospedagem.

## Conferência

1. Crie um plano em Planos. Edite seu nome e estado.
2. Em Contas, crie uma conta: usuário, nome, senha inicial, plano e vencimento.
   A opção de acesso começa desativada; marque para liberar.
3. Pesquise o usuário e filtre por estado.
4. Edite acesso para bloquear, ativar, renovar ou remover o vencimento.
5. Redefina a senha e confirme que a nova senha precisa ser digitada duas vezes.
6. Abra Aparelhos. Contas sem login pela API mostrarão uma lista vazia.
   Quando houver aparelhos, bloqueie/desbloqueie e confira o estado.
7. Saia do painel e confirme o retorno ao login. Recarregar a página também
   exige novo login: os tokens ficam somente na memória da aba.

Alterar acesso ou senha revoga as sessões anteriores. Bloquear um aparelho
revoga sua sessão e impede novos logins naquela instalação; desbloquear não
recupera a sessão antiga. Aparelhos offline recebem o bloqueio ao reconectar
ou ao terminar sua tolerância no SoltaVoz 1.3.

Desativar um plano apenas o retira das novas escolhas no painel. Não bloqueia
contas que já o utilizam. Planos nesta etapa são categorias administrativas;
cobrança e duração automática por plano não foram implementadas.

## Verificações

- 17 testes locais de API e checagem Deno passaram.
- Migração 003 aplicada e teste SQL no projeto real passou.
- Teste HTTP real passou com criação/edição de plano, login administrativo,
  criação de conta, substituição de sessão, bloqueio/desbloqueio de aparelho,
  troca de senha, refresh, bloqueio de conta e vencimento.
- 3 testes de navegador Chrome passaram: estados, fluxo desktop e fluxo móvel.
  Interface usa dados simulados nesses testes; API real foi testada separadamente.
- Usuários, plano e auditoria temporários do teste HTTP foram removidos.

## Limites desta etapa

O painel lista até 200 contas recentes, 200 planos e 200 aparelhos por conta;
paginação para volumes maiores virá antes de ampliar a distribuição.
O SoltaVoz 1.3 integra login, autorização offline assinada e busca protegida
por sessão. Confira `docs/testar-login-tvbox.md`. APKs anteriores continuam
sem esse controle; atualize os aparelhos. Hospedagem pública do painel e
release com o certificado de produção original continuam pendentes.
