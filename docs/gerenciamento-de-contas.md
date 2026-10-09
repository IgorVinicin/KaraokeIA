# Gerenciamento de contas SoltaVoz

## Requisitos definidos

- Administrador instala o app no TV box e cria usuário e senha no painel web.
- Não há cadastro público de clientes.
- Administrador atribui plano, ativa, bloqueia, renova e gerencia aparelhos.
- Acesso pode ter data de vencimento ou prazo indeterminado.
- Login no app por usuário e senha.
- Uma conta tem somente uma sessão vigente no servidor; login em outro
  aparelho substitui a sessão anterior.
- Primeiro login/ativação de cada instalação exige internet.
- Após validação online, músicas locais podem ser usadas offline por até
  24 horas desde a última validação bem-sucedida, ou até o vencimento da conta,
  o que ocorrer primeiro. Esta duração adota a opção de 24 horas proposta.

## Estrutura proposta

Supabase Auth gerencia credenciais; as senhas não serão armazenadas em tabelas
do aplicativo nem exibidas no painel. O painel permite redefinir credenciais.
O nome de usuário será resolvido no servidor, sem exigir e-mail na TV.

PostgreSQL armazena perfis, planos, permissões de acesso, aparelhos, sessões e
auditoria de alterações administrativas. A validade da conta é independente
do vencimento do token de autenticação: estar autenticado não basta para ter
acesso ao aplicativo.

Funções no servidor verificam identidade do administrador para criar contas,
atribuir planos, bloquear, renovar e liberar aparelhos. As credenciais de
administração do Supabase nunca acompanham o APK nem o JavaScript do painel.
As políticas do banco impedem clientes de alterar validade, plano ou bloqueios.

A abertura de sessão usa operação atômica no banco: revoga a sessão vigente,
registra sua substituição e cria a nova sessão, inclusive quando o aparelho
anterior está offline. Cada sessão tem identificador próprio e geração;
renovar um token de autenticação não reativa uma sessão substituída.
O app identifica sua instalação
por um identificador aleatório persistido; não depende de MAC/IMEI.
Reinstalação e restauração podem exigir liberação pelo administrador.

O app valida a sessão ao abrir, ao retornar ao primeiro plano e periodicamente.
Busca de YouTube também valida acesso no servidor antes de consumir a API.
Bloqueios não podem depender apenas de uma tela de login ou de um valor salvo
no aparelho. Expiração é calculada com horário do servidor.

Ao reconectar, o aparelho valida o identificador e a geração da sessão antes
de continuar o uso, renovar a autorização offline ou pesquisar no YouTube.
Se a sessão foi substituída, o app encerra o acesso, descarta suas credenciais
locais e retorna ao login com a mensagem de acesso em outro aparelho.
Falha de rede não equivale a uma resposta de sessão válida; rejeição explícita
por substituição, bloqueio ou vencimento não admite tolerância offline.

Sem internet, somente uma autorização offline emitida pelo servidor após
validação bem-sucedida permite acesso local. Ela identifica conta, instalação,
sessão e prazo, tem assinatura verificável e não pode ser prolongada por abrir
o app ou por mudar a hora do aparelho. A implementação deve verificar relógio
e reinicialização; se não puder comprovar o prazo, exige nova validação online.
Após reinstalar, apagar dados ou perder a autorização, exige internet.

O servidor revoga a sessão anterior imediatamente, mas não consegue entregar
um logout a um aparelho desconectado. Esse aparelho pode continuar músicas
locais até reconectar ou encerrar a tolerância. Portanto, existe uma sessão
vigente no servidor, com possível sobreposição temporária de uso offline.
Exclusividade imediata entre aparelhos exigiria internet obrigatória.

O painel propõe telas de contas, planos, aparelhos e registros de alterações.
Contas mostram estado, plano, vencimento e aparelho autorizado. Operações de
bloqueio, renovação, troca de aparelho e redefinição de senha ficam auditadas.

## Configuração pendente

- Organização informada: `powujxxbfomzvexpkkht`. O link do painel da
  organização não é o endereço da API de um projeto.
- Projeto informado: `https://luqgrtfqlywchlyasaog.supabase.co`.
- Autenticar o ambiente de publicação ou executar a migração pelo painel.

## Estado

Migrações de contas/sessões e API executadas no projeto remoto. API de login,
validação, refresh, logout e operações administrativas publicada e testada.
13 testes locais, checagem Deno, testes SQL e teste HTTP real passaram.
Ferramenta de conferência em `scripts/gerenciar-contas.ps1`; instruções em
`docs/testar-api-contas.md`.
Painel web local conectado à API; gestão de aparelhos, planos e redefinição
de senha implementadas na etapa 2. Migração 003 e API atualizada publicadas.
17 testes de API, teste SQL/HTTP real e 3 testes de navegador passaram.
Instruções em `docs/testar-painel-web.md`. Hospedagem pública do painel pendente.
SoltaVoz 1.3 conecta login Android, autorização offline RSA/SHA-256 e busca
YouTube protegida por sessão. Migração 004 e API publicadas, com cache e
orçamento compartilhados no banco. 19 testes de API/assinatura e 23 testes
Android passaram; teste HTTP real verificou assinatura e busca YouTube.
Roteiro do aparelho em `docs/testar-login-tvbox.md`. Release de produção
depende do keystore original; teste no TV box físico continua necessário.
