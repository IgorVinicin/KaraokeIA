# SoltaVoz 1.2 — biblioteca unificada

O app abre direto no catálogo. A seleção de modo online/offline foi removida.
TV e celular usam a mesma busca, fila, favoritos, histórico e ranking.

## Pesquisa

1. Digite o nome da música, artista ou código.
2. A busca local ignora acentos e usa o catálogo carregado do aparelho.
3. Se houver resultados locais, o app os mostra e não consulta o YouTube.
4. Se não houver resultados e a consulta tiver pelo menos dois caracteres,
   o app aguarda uma pausa de 550 ms após o filtro local e pesquisa karaokês
   no YouTube. O filtro local já aguarda 180 ms para agrupar a digitação.
5. As músicas do YouTube mostram a origem e que exigem internet. Clique/OK
   abre o player adequado à origem; não há nova escolha de modo.

Apagar ou mudar a pesquisa cancela a busca anterior. Respostas atrasadas não
substituem os resultados atuais. Erros de internet/configuração aparecem na
mesma tela, com opção de tentar novamente e acesso às configurações.
O cache de busca existente continua sendo utilizado.

## Fila e biblioteca

Músicas locais e vídeos usam a mesma fila, na ordem em que foram adicionados.
Ao concluir a música atual, ela é retirada somente se ainda for a primeira da
fila. A tela de resultado pode iniciar a próxima música de qualquer origem.
Vídeos escolhidos mantêm seus metadados para reabrir favoritos/fila após reiniciar.

Fila, favoritos e histórico da versão antiga do YouTube são importados uma vez;
remover músicas depois da importação não faz com que elas reapareçam.
Os dados antigos são preservados. O controle QR acompanha a fila compartilhada.

O pendrive é opcional para pesquisar no YouTube. Para acessar músicas locais
na primeira instalação, use Configurações → Acessar músicas do aparelho.
Sem músicas locais, a tela inicial oferece acesso à pesquisa.

## Serviço do YouTube

A chave continua fora do APK. Para distribuir o app, publique o
[servidor de pesquisa](../server/README.md) e compile com `SOLTAVOZ_SEARCH_URL`.
Sem esse endereço, a configuração pessoal da chave está em Configurações →
Configurar pesquisa do YouTube. Sem internet ou serviço configurado, o catálogo
local continua funcionando; a busca complementar mostra o erro na própria tela.

Versão Android: 1.2.0, código 4, pacote `com.araujo.karaoke_ia`.
APK de teste: `dist/SoltaVoz-debug.apk`. A release para atualizar instalações
distribuídas depende do certificado original e de `signing.properties`.

## Validação

- Build debug, APK de testes e lint concluídos: zero erros de lint (49 avisos).
- 16 testes Android passaram, incluindo busca local sem rede, fallback,
  respostas atrasadas, repetição da pesquisa e migração da fila/favoritos/histórico.
- No emulador TV, a abertura vai direto ao catálogo. A pesquisa `Evidências`,
  ausente no catálogo de teste, retornou 15 karaokês reais do YouTube usando
  a configuração pessoal já existente no emulador.
- Scanner do APK passou: sem arquivos privados ou chave do `.env`.
- Houve um ANR na primeira abertura do emulador: a pilha ficou bloqueada no
  renderizador Android (`HardwareRenderer.setStopped`) com CPU do emulador
  saturada. Após reabrir, catálogo e buscas responderam. Desempenho final deve
  ser conferido no TV box físico; o teste não comprova fluidez nesse aparelho.

Evidências da conferência estão em `reports/unified/`. O servidor de produção
não foi publicado e a release assinada continua dependendo do certificado.
