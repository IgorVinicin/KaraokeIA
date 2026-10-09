# SoltaVoz 1.1.0

- Nome, logo vetorial e banner Android TV novos. A identidade mantém magenta e violeta.
- Foco com contorno branco e escala de 130 ms; transições de tela de até 160 ms.
  Sem animações infinitas no catálogo. Botões clicáveis têm um único alvo de foco.
- Clique/OK nas capas e resultados abre o player. Mover o foco atualiza o destaque;
  os botões de fila e favoritos continuam disponíveis.
- Cards de resumo de fila e melhores pontuações removidos da tela inicial.
  As abas Fila e Ranking continuam disponíveis.
- Cache persistente do catálogo com gravação atômica. Mudanças no caminho do USB,
  nomes, tamanhos e datas dos arquivos invalidam o cache. Atualizar catálogo força
  a leitura dos TXT mesmo quando o tamanho e a data não mudam.
- Busca local aguarda 180 ms e filtra fora da thread da interface. Carregamentos
  simultâneos do mesmo catálogo são evitados.
- Capas locais são exibidas a partir do cache. O botão Buscar capas faz a consulta
  explícita; a navegação e o player não iniciam buscas de imagens na internet.
- Capas ausentes usam cores e iniciais geradas localmente, sem imagens adicionais.
- Player local inicia com buffer de 250 ms, carrega entre 5 e 10 segundos e usa
  alvo de 12 MB para o buffer. Esse valor não é um limite da memória total do app:
  o decoder, a interface e o Android também usam memória.
- Busca online usa um servidor configurável por endereço HTTPS público. A chave
  fica exclusivamente no servidor e consultas incluem automaticamente `karaoke`.

## Entrega

O identificador Android permanece `com.araujo.karaoke_ia`, preservando biblioteca,
fila e pontuações ao atualizar com o mesmo certificado. A versão é 1.1.0, código 3.
A release assinada é gerada em `dist/SoltaVoz-release.apk` pelo script existente.
É necessário fornecer o `signing.properties` e o keystore correspondente.

Para a busca distribuída, publique o [servidor](../server/README.md), configure a
chave na hospedagem e compile com `SOLTAVOZ_SEARCH_URL` definido. Sem esse endereço,
o APK permite a configuração pessoal no aparelho; a pesquisa não fica pronta para
os usuários da distribuição.

## Validação na TV box

1. Use as setas em navegação, paginação, fila, favoritos e controles do player.
   Confirme que o botão focado tem borda branca visível e que OK executa a ação.
2. Clique na capa da música e confirme a abertura direta do player.
3. Abra o catálogo duas vezes, altere um TXT e retire/reconecte o pendrive.
   Confira a atualização dos dados; use Atualizar catálogo para forçar releitura.
4. Pesquise um nome online, abra a capa e teste o retorno à biblioteca.
5. Teste vídeos com bitrate alto no dispositivo real. Ajuste o buffer se o USB
   ou decoder da TV box apresentar pausas. Tempos do emulador não equivalem ao
   desempenho de um pendrive na TV box.

## Verificação realizada em 09/10/2026

- `assembleDebug`, `assembleDebugAndroidTest` e `lintDebug`: concluídos.
  Lint sem erros; 49 avisos e 3 informações, incluindo dependências antigas.
- 7 testes Android pelo `AndroidJUnitRunner` no emulador: passaram.
  Cobrem persistência, alteração dos TXT, remoção de vídeos, cache corrompido,
  troca de raiz, atualização forçada e identificação de links de vídeos.
- 7 testes do servidor com `node --test`: passaram. Cobrem cache, concorrência,
  validação, limites de cota/IP, expiração e ausência de vazamento da chave.
- APK inspecionado: nome SoltaVoz, versão 1.1.0/código 3, modo debug;
  scanner sem arquivos privados ou chave da `.env`.
- Controle remoto no emulador: contorno branco da capa conferido visualmente,
  OK na capa abriu e reproduziu o vídeo local; a busca online apresenta
  “Nome da música ou artista”. Nenhum crash observado durante essas verificações.

APK de teste: `dist/SoltaVoz-debug.apk`, com SHA-256 ao lado. A release anterior
não foi sobrescrita. Não foi gerada uma release nova porque `signing.properties`
e o keystore original não estão configurados neste ambiente.

O servidor está preparado, mas não foi publicado. A consulta real pelo servidor
ainda depende de hospedagem, chave válida e compilação com o endereço HTTPS.
Desempenho em TV box física e vídeos de bitrate alto ainda requer validação.
