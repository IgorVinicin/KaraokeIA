# SoltaVoz 1.4 — índice local e Android TV

Versão 1.4.0, versionCode 6. APK de teste: `dist/SoltaVoz-1.4.0-debug.apk`.
SHA-256 ao lado, em `SoltaVoz-1.4.0-debug.apk.sha256`. O APK sem versão no
nome foi mantido na entrega anterior porque o Windows bloqueou sua substituição.

## Catálogo

O catálogo fica em SQLite nativo, no armazenamento privado `no_backup`, e não
no cache descartável. Não há uma dependência nova de Room. Na abertura, a Home
carrega o índice sem consultar o USB e verifica os volumes em `Dispatchers.IO`.
A primeira indexação publica resultados em lotes de até 1.000 músicas.

As raízes conhecidas de músicas internas e os volumes externos conectados são
verificados sem reler os metadados de arquivos inalterados. Vídeos MP4, MKV e
AVI são encontrados também em subpastas. Metadados JSON `.txt` ao lado do vídeo
ou em `dados` têm prioridade; título, artista e duração embutidos são usados
quando disponíveis. Um arquivo alterado é reconhecido por caminho, tamanho e
data de modificação do vídeo e do arquivo de metadados.

Busca por código, nome ou artista consulta apenas SQLite: normalização de
maiúsculas e acentos, parâmetros SQL vinculados e lotes de 200 resultados.
O catálogo geral usa os componentes de lista/paginação existentes; a busca não
reabre o HD. O controle QR também recebe o catálogo pelo índice.

Desconectar um volume preserva seus registros como indisponíveis. Reprodução
e inclusão na fila ficam desabilitadas. Reconectar restaura os registros após
verificação. Remoções são reconciliadas somente após uma enumeração completa
do volume acessível. Raízes ausentes ou com erro de leitura não são apagadas.
Quando dois volumes possuem o mesmo código, a cópia conectada é priorizada,
mantendo a identidade por código usada pelas filas e favoritos existentes.

Verificação ocorre ao abrir/retomar o app, em eventos de montagem/desmontagem
e pelo botão Atualizar catálogo. Não há uma varredura contínua. Alterações
feitas enquanto o app permanece aberto podem exigir esse botão. Ele força a
releitura, inclusive quando um editor mantém tamanho e data do arquivo.

## Interface e reprodução

- Foco roxo, borda e brilho discreto; barra de pesquisa com foco e limpeza.
- Páginas preservadas durante navegação, mais resultados carregados no fim da lista.
- Catálogo permanece visível enquanto o índice é verificado.
- Controles locais permanecem visíveis ao pausar ou em erro; QR gerado fora da main.
- YouTube mostra conexão e buffering, com avisos de tempo excedido e erros distintos.
- Correção do estado de término ao trocar músicas online na fila.
- Player oficial preservado, com logs de tempo de inicialização e estados.

## Validação

Os testes e o benchmark ficam em `app/src/androidTest/.../data`.
O benchmark foi preparado para comparar a implementação anterior e a atual,
com 3.000 pares de arquivos sintéticos. Não foi executado: o usuário pediu
pular emuladores devido à capacidade do PC. Não há números de desempenho
antes/depois nesta rodada.

Resultados de compilação e revisão são registrados em `reports/local-index*`.
Passaram 26 testes de servidor/API, o teste simulado dos eventos YouTube e
sete testes de SQL extraído do repositório no SQLite do computador. O lint
de integração terminou sem erros, com 50 avisos.
As fontes dos testes Android foram compiladas; execução, reprodução, D-pad,
reboot e medições Android ficaram pendentes. O teste de reprodução preparado
gera um MP4 AAC localmente, consulta o índice após reabrir o repositório e
reproduz até o término.

Ainda é necessário conferir no TV box físico: latência do HD e pendrive,
reinicialização do aparelho, controles, áudio, codecs, WebView, tempo real do
YouTube, memória e uma sessão prolongada. Emulação não comprova esses resultados.

Referências de implementação:
[SQLiteDatabase](https://developer.android.com/reference/android/database/sqlite/SQLiteDatabase),
[MediaMetadataRetriever](https://developer.android.com/reference/android/media/MediaMetadataRetriever),
[player oficial YouTube](https://developers.google.com/youtube/iframe_api_reference).
