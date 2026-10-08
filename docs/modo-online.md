# Modo online

Ao iniciar, escolha offline (pendrive) ou online (YouTube). Voltar na tela principal
offline retorna à seleção de modo; no online use Configurações → Trocar modo.
As filas são independentes e persistem entre sessões.

## Testar sem chave

No modo online, abra Pesquisar, cole um link de vídeo público do YouTube,
pressione Buscar músicas e escolha Cantar agora ou Adicionar à fila.
Links watch, youtu.be, shorts e live são aceitos.
O título e o canal são obtidos pelo oEmbed do YouTube. O vídeo é transmitido pelo
player oficial incorporado; não é copiado para o aparelho nem extraído em áudio.
Cantar agora inicia em tela cheia quando o player estiver pronto. Em uma prévia,
use Reproduzir em tela cheia. Ao terminar, a música é removida da fila online
e a tela de resultado permite confirmar a reprodução da próxima.

## Ativar busca por música/artista

1. Crie um projeto no Google Cloud e habilite YouTube Data API v3.
2. Crie uma chave e configure restrições apropriadas para o aplicativo/API.
3. No arquivo app/.env, ignorado pelo Git, acrescente YOUTUBE_KEY=SUA_CHAVE.
4. Instale o APK debug e execute scripts/configurar-youtube.ps1 com o aparelho
   conectado ao ADB (use -Serial para escolher outro dispositivo). O app reinicia
   e o campo passa a aceitar busca por nome.
   O testar_capas_karaoke.bat também faz essa configuração automaticamente
   quando app/.env está presente.

A chave fica somente na área privada do aparelho, excluída dos backups, e não
é incluída no APK. A configuração do teste usa run-as e requer o APK debug.
Para distribuição a clientes, prefira um backend com autenticação e controle
de cota. Não coloque a chave no Git nem em logs.
As buscas são feitas apenas ao pressionar Buscar, com até 15 resultados de vídeos
incorporáveis e o termo karaoke. Cotas e bloqueios são definidos pelo YouTube.

## Limites do teste

É necessário acesso à internet e Android System WebView funcional/atualizado.
Vídeos privados, removidos ou com incorporação bloqueada exibem erro; é possível
tentar novamente ou abrir no YouTube. A disponibilidade depende do vídeo/região.
Ao terminar o vídeo, o modo online apresenta a mesma pontuação recreativa do
offline, salva o resultado no ranking compartilhado e oferece foto e QR para
quem entrar no pódio, quando houver câmera disponível. A pontuação não mede voz
ou afinação. A próxima música aguarda a tela de resultado; pressione Cantar
próxima para iniciar sua reprodução em tela cheia. Escolher outra música ou
Voltar retorna à biblioteca sem iniciar automaticamente o próximo vídeo.
O controle QR da fila existente continua selecionando o catálogo offline.

Referências: https://developers.google.com/youtube/iframe_api_reference e
https://developers.google.com/youtube/terms/required-minimum-functionality.
