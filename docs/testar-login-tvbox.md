# SoltaVoz 1.3 — login e acesso no TV box

## Preparar a conta

No painel web, crie uma conta de cliente com usuário/senha e ative seu acesso.
Informe um vencimento ou marque prazo indeterminado. O login do TV box usa o
nome de usuário do cliente, não o e-mail do administrador.

## Conferência no aparelho

1. Instale `dist/SoltaVoz-debug.apk` para teste e abra o aplicativo.
2. O primeiro login exige internet, mesmo que existam músicas no pendrive.
3. Entre com uma conta ativa. Catálogo e busca ficam disponíveis.
4. Pesquise uma música ausente no aparelho. A busca consulta o YouTube através
   do Supabase e não exige chave no aparelho.
5. Desconecte a internet: a autorização já validada permite músicas locais
   por até 24 horas desde a última validação, limitada pelo vencimento da conta.
6. Entre com a mesma conta em outro aparelho. A sessão anterior é revogada.
   Ao reconectar, o primeiro retorna ao login. Se ele estiver offline, ainda
   pode usar músicas locais até encerrar a autorização que já recebeu.
7. Bloqueie a conta, o aparelho ou altere a senha no painel; confira o retorno
   ao login no aparelho conectado. A validação ocorre ao abrir, retornar ao
   app, mudar a conectividade e a cada 15 segundos com o app visível. Requisições
   de busca também validam a sessão no servidor antes de usar o YouTube.
8. Use Sair da conta e confirme: o app apaga a autorização local. O próximo
   login precisa de internet. Player e controle QR são encerrados ao perder acesso.

Uma atualização de APK exige a mesma assinatura Android da instalação atual.
Não desinstale automaticamente diante de uma assinatura diferente: preserve
seus dados. O APK de teste usa a assinatura debug, e a release assinada ainda
depende do keystore de produção original.

## Autorização offline

O servidor assina conta, sessão, instalação e prazo com RSA/SHA-256. O APK
contém somente a chave pública e verifica a assinatura antes de autorizar.
Credenciais e autorização ficam na área privada `no_backup`, fora de backups.
O app usa tempo monotônico, referência do relógio e contador de inicializações
para evitar prolongar o prazo por mudar a hora ou reiniciar.

Mudanças de relógio superiores a cinco minutos exigem nova validação. Uma
reinicialização também exige internet. Em aparelhos sem contador de boot
compatível, encerrar o processo pode exigir internet novamente. Esses casos
priorizam não conceder um prazo que o app não consegue comprovar.

A chave privada de assinatura offline fica nos Secrets do Supabase e em backup
privado `.signing/offline-signing.env`; ela é diferente do keystore usado para
assinar releases Android. Preserve-a e a chave pública correspondente em
`supabase/offline-public-key.txt`. Não gere outra em atualizações rotineiras.

## Busca e dados locais

A API protege a busca com identidade verificada e sessão vigente. Cache de
15 minutos (até 200 consultas) e orçamento de 80 chamadas ao YouTube por dia
UTC ficam no banco, compartilhados entre instâncias. Limites de tentativas
também são persistentes. Vídeos continuam no player oficial do YouTube.

Fila, favoritos, histórico e músicas locais continuam pertencendo à instalação
do aparelho nesta etapa; não foram sincronizados ou separados por conta.
O identificador de aparelho identifica a instalação, não um número físico
imutável: reinstalar/apagar os dados cria uma nova instalação. Bloquear a conta
impede novos logins em qualquer instalação.

## Distribuição

O painel ainda é servido localmente. Esta etapa não publica um site público
nem gera uma release de produção com outro certificado. Valide também no
TV box real: controle remoto, teclado, codecs, áudio, internet e reconexão.

## Verificações executadas

- APK debug 1.3.0, versionCode 5, atualizado em `dist/SoltaVoz-debug.apk`.
- Compilação e lint concluídos: zero erros e 50 avisos (incluindo o singleton
  que guarda apenas o contexto da aplicação).
- 23 testes Android passaram no APK final: autorização, sessão, offline,
  catálogo local, busca híbrida e busca online.
- 19 testes de API/assinatura passaram. Testes SQL e HTTP no projeto real
  verificaram troca de sessão, bloqueios, vencimento, assinatura e YouTube.
- Tela de entrada e foco entre campos conferidos no emulador Android TV;
  teclado aberto permite rolagem. O desempenho no TV box físico precisa da
  rodada de teste descrita acima.
- Verificador do APK confirmou ausência de arquivos privados e das chaves
  locais de YouTube e assinatura offline.
