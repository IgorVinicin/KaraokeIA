# Modo online

**A partir do SoltaVoz 1.2, não há seleção de modo.** A busca procura primeiro
no aparelho e consulta o YouTube automaticamente quando não encontra músicas.
Veja a [biblioteca unificada](soltavoz-1.2.md). As seções abaixo descrevem o
fluxo anterior e as formas de configurar a pesquisa.

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

### Distribuir o SoltaVoz 1.1.0

Use o [servidor de pesquisa](../server/README.md). A chave fica em `server/.env`
ou nos segredos da hospedagem. Defina `SOLTAVOZ_SEARCH_URL` com o endereço HTTPS
público antes de compilar o APK. Esse endereço é a única configuração que entra
no app; não é uma credencial. Com o servidor configurado, o usuário pesquisa
somente pelo nome da música/artista, sem configurar chave ou fornecer link.

O campo de pesquisa sempre prioriza nomes. Links de vídeos ainda são aceitos
como opção adicional; títulos com 11 caracteres não são confundidos com IDs.
OK ou clique na capa abre diretamente o player. O serviço precisa ser hospedado
e ter uma chave válida para a pesquisa funcionar na versão distribuída.

### Uso pessoal e versões anteriores

No APK release 1.0.1, use Online → Configurações → Configurar pesquisa por nome. Informe somente o valor da chave YouTube Data API e salve; é possível removê-la pelo mesmo painel. A chave é mascarada, armazenada com gravação atômica na área privada `no_backup` e não acompanha o APK. O `.env` do computador não configura automaticamente a TV Box.

As etapas abaixo descrevem a configuração pelo computador para o APK **debug**.

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
O release oferece configuração privada por aparelho. Para provisionamento central de vários clientes, prefira um backend com autenticação e controle
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
