# Redesign do Palco Online

## Arquitetura e dados reais

A interface utiliza o catálogo local existente e os dados reais do modo online. O modelo online contém identificador do vídeo, título e canal: não contém gênero nem um catálogo global de popularidade. Categorias podem agrupar canais presentes na biblioteca; popularidade local deve ser identificada como tal.

`OnlineRepository` expõe fila, favoritos e histórico como `StateFlow`. Favoritos e histórico são persistidos em `online_library`, separado da fila `online_queue`, do ranking e do catálogo do pendrive. Não existe preenchimento automático com artistas ou vídeos fictícios.

Favoritos são únicos por vídeo, limitados a 100 músicas. O histórico mantém as 50 músicas distintas mais recentes. O registro ocorre no evento real de reprodução (`playing`) do player, não na simples abertura de uma prévia.

A chave do YouTube continua na configuração privada do dispositivo, fora do APK e dos backups. Pesquisa utiliza a integração existente; links continuam disponíveis quando a busca por nome não estiver configurada.

## Medidas de desempenho

- Cache em memória das últimas 12 pesquisas bem-sucedidas, com validade de dois minutos, evitando solicitações repetidas e consumo desnecessário de cota ao retornar às telas.
- Limites de fila, favoritos e histórico impedem crescimento indefinido dos objetos carregados na interface.
- Catálogo local mantém o índice de músicas e executa sua leitura em `Dispatchers.IO`.
- Capas locais já possuem miniaturas reduzidas, cache em disco e no máximo três solicitações de rede simultâneas.
- Player existente acompanha o ciclo de vida, pausa ao perder atividade e remove seu observador e ponte JavaScript ao ser descartado.
- Nenhuma biblioteca adicional foi necessária para as mudanças de persistência e cache.

O vídeo utiliza o player oficial incorporado do YouTube. Não há extração nem download de áudio/vídeo pelo novo catálogo online.

## Checkpoint e validação

Este documento descreve a implementação e a auditoria de código. A última compilação e o lint passaram: 0 erros e 48 avisos. O APK abriu no Android TV; pesquisa, capas, inclusão na fila e persistência de favorito foram exercitadas. Nove casos de codificação passaram contra o código compilado. A validação completa foi interrompida para publicar o checkpoint solicitado pelo usuário; consulte `CONTINUAR_EM_CASA.md` para resultados e pendências. A lista abaixo continua sendo o roteiro completo, não uma lista de testes aprovados.

1. Iniciar em Android TV e navegar por todos os menus somente com setas, OK e Voltar; verificar foco inicial e retorno ao item anterior.
2. Conferir carrossel com dados reais, prévia sem reprodução e capa ausente; nenhum histórico deve ser criado apenas por selecionar uma capa.
3. Pesquisar, repetir uma consulta e conferir comportamento de erro de internet/API sem expor credenciais.
4. Adicionar/remover favorito, reiniciar o aplicativo e verificar persistência; garantir que não altera fila nem histórico.
5. Reproduzir uma música e verificar histórico; pausar/retomar sem duplicatas; reiniciar e verificar persistência.
6. Adicionar duas músicas à fila, completar a primeira e verificar resultado antes da seguinte. Cantar próxima deve iniciar somente após a ação do usuário.
7. Verificar vídeo em tela cheia, controles por OK, pausa/retomada, ocultação dos controles e retorno sem recriar o player nem reiniciar a música.
8. Conferir pontuação, pódio, foto e QR por apresentação; duas músicas com a mesma nota não podem reutilizar a foto ou o QR de outra apresentação.
9. Alternar telas e abrir/fechar player repetidamente; observar erros de foco, `FATAL EXCEPTION` e pressão de memória no Logcat.
10. Verificar resultado da última música com fila vazia, sem botão de próxima música.

Captura física de foto, controles de TV Box, reprodução de longa duração, codecs locais e estabilidade com pouca memória dependem também de testes no hardware real. A nota existente é recreativa: este redesign não adiciona análise de afinação.

## Build

O responsável pela integração deve executar limpeza, compilação e os testes disponíveis, instalar o APK e registrar resultados reais. Na ausência de assinatura de release configurada, o APK de validação é de debug em `app/build/outputs/apk/debug/app-debug.apk`.

O carrossel inicial prioriza contagens locais de apresentações concluídas dentro da biblioteca real. A recuperação de metadados com codificação antiga corrige apenas conversões estritamente válidas; a biblioteca e as notas não são substituídas por dados de exemplo.
