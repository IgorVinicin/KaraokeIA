# Continuidade do Karaokê — 08/10/2026

## Ponto de parada

O usuário pediu para publicar o trabalho no Git e continuar em casa. O redesign está implementado e compila, mas a validação completa da nova interface ainda não terminou. Não tratar este checkpoint como release de produção.

Repositório: https://github.com/IgorVinicin/KaraokeIA — branch `main`.

Leia também `redesign-requisitos.md`, `redesign-palco.md`, `modo-online.md` e `producao.md`. As imagens em `docs/imagens/` mostram a interface executada nesta etapa. A referência principal enviada pelo usuário é um palco inspirado no PS4: fundo roxo abstrato discreto, ondas, rosa neon, marca à esquerda, menu horizontal superior e capas horizontais com destaque maior na seleção. A imagem original está no anexo da conversa; as capturas do repositório são do aplicativo implementado.

## Implementado

- Tema roxo/rosa e fontes Unbounded/Manrope compartilhados. Fundo com cinco ondas em `drawWithCache`, sem imagens pesadas, blur ou animação permanente.
- Menus de TV: Início, Pesquisar, Categorias, Ranking, Fila e Configurações. Favoritos e histórico acessíveis pelos painéis, sem seção de categorias abaixo do carrossel inicial.
- Carrosséis reais online/offline; seleção com foco, borda rosa, animação curta, título/artista e ações Cantar agora, favorito e fila. Sem músicas ou capas fictícias.
- Online: capas do YouTube via Coil, busca por nome ou link, categorias por canais reais, favoritos e histórico persistidos. Histórico registra reprodução real, não apenas a prévia.
- Pesquisa com cache de dois minutos e até 12 consultas; favoritos limitados a 100, histórico a 50 músicas distintas.
- Pódio online em ordem visual 2/1/3 com fotos; popularidade local calculada pelos últimos 50 resultados retidos. Não existe ranking global do YouTube nem metadados de gênero.
- Cantar agora e Cantar próxima configurados para iniciar em tela cheia; OK revela controles discretos, e Voltar oculta controles/sai da tela cheia/retorna à biblioteca em etapas. Estado de busca, painel e capa é preservado ao retornar.
- Ao terminar, o vídeo passa pela pontuação, pódio, foto e QR existentes antes de iniciar a próxima música. O botão Cantar próxima confirma o avanço. Cada resultado isola o estado da foto por ID.
- Faixas mantidas: 1º 96–100, 2º 90–95, 3º 87–89. A nota continua recreativa; não mede afinação.
- Recuperação conservadora de títulos antigos com UTF-8 corrompido; dados válidos permanecem iguais. Setas para cima/baixo no campo de pesquisa TV encaminham o foco para os demais controles.
- Interface móvel da fila com ações abaixo do título em telas estreitas; configurações roláveis. Resultado, seleção de modos, player local e controle web receberam a identidade compartilhada.
- Preparação anterior para produção preservada: R8 no release, exportação de fotos com moldura, melhorias de storage/cache e scripts para instalação e emulador leve.

## O que foi efetivamente validado

| Verificação | Resultado |
| --- | --- |
| Gradle clean + assembleDebug | Executados com sucesso |
| Última build assembleDebug + lintDebug | Sucesso; lint com 0 erros e 48 avisos |
| testDebugUnitTest | Executado, mas NO-SOURCE: não existe suíte de testes unitários Gradle |
| Recuperação de acentuação | 9 casos passaram contra o helper real compilado, incluindo texto válido, emoji e chinês |
| APK atual no Android TV | Instalado com sucesso e MainActivity abriu com Status: ok |
| Novo visual e capas | Inspecionados em capturas reais do emulador |
| Pesquisa por controle | Campo, teclado virtual, texto via ADB, saída do teclado, seta para Buscar e OK; vídeos reais retornados |
| Favorito | Salvo e encontrado no arquivo de preferências depois de reiniciar o app |
| Adicionar à fila | Duas entradas apareceram na tela da fila |
| Remover da fila | Ainda inconclusivo: automação tentou remover a última entrada, mas depois do reinício a fila ainda mostrava duas; confirmar ação, foco e persistência |
| Resultado/avanço/pódio | Antes deste redesign, dois vídeos foram concluídos: 63 pts, Cantar próxima, depois 98 pts e 1º no pódio. Repetir com a nova interface e seus controles |

Ambiente usado: AVD `Television_1080p`, Android TV API 34 x86_64, 960×540, dois núcleos, 1536 MB e GPU `swiftshader_indirect`. Os testes recentes rodaram sem janela e sem áudio. Isso não valida som físico, câmera, TV Box ARM ou performance de longa duração.

A fila, ranking, favoritos e histórico originais do emulador foram restaurados e verificados após o teste. O emulador temporário foi encerrado. Capturas/logs brutos continuam locais; as prévias e os helpers selecionados acompanham o commit.

## Prioridades ao continuar

1. Instalar o APK recém-compilado e percorrer os seis menus somente com setas, OK e Voltar. Validar foco inicial, retorno para a capa, listas roláveis e componentes fora da tela.
2. Confirmar Remover na fila, inclusive após reiniciar, sem remover outras entradas nem duplicar itens. Verificar se a tentativa inconclusiva foi problema de automação ou bug real.
3. Testar Cantar agora: iniciar automaticamente em tela cheia. OK deve abrir controles; pausa/retomada e ocultação devem preservar o tempo do mesmo WebView.
4. Testar duas músicas até o término real: resultado e eventual foto/QR primeiro; somente Cantar próxima inicia a seguinte. A última, com fila vazia, não deve oferecer próxima. Verificar eventos ended duplicados e duas notas iguais sem reutilizar foto/QR.
5. Revalidar offline: pendrive, catálogo, busca, favoritos, histórico, fila, Media3, resultado e pódio. Os módulos continuam existentes, mas o novo visual ainda precisa desse teste completo.
6. Verificar telefone/tablet e resoluções menores. A busca usa carrossel com altura mínima; conferir recorte, botões quebrados em várias linhas e teclado. Inspecionar screenshots, não apenas a existência de nós XML.
7. Ajustar a proximidade visual à referência PS4 depois dos testes funcionais; não usar emojis decorativos nem inventar dados. As miniaturas YouTube são de vídeos reais, não capas oficiais de álbum.
8. Rodar Logcat e alternar telas/player repetidamente. Auditar avisos restantes do lint e memória no equipamento real; evitar refatorações sem necessidade.
9. Testar câmera/fotos/QR, fila pelo celular na mesma rede, codecs e desempenho na TV Box real. O controle web existente seleciona o catálogo offline; a fila online é separada.
10. Para produção, configurar assinatura definitiva e provisionamento/backend da busca YouTube. O script atual usa run-as e exige APK debug; não embutir chave no APK ou Git.

## Preparar o computador de casa

```powershell
git pull origin main
```

Abra o projeto no Android Studio, configure o Android SDK e recrie `local.properties` nesse computador. O projeto compila com SDK 35 e minSdk 23. Instale/crie o AVD Android TV `Television_1080p` ou adapte os scripts para o nome local.

`app/.env` é privado e não acompanha o Git. Copie `app/.env.example` para `app/.env` e configure sua própria `YOUTUBE_KEY`, obtida por um canal privado. Links de vídeos continuam funcionando sem chave; pesquisa por nome exige configuração.

```powershell
.\gradlew.bat :app:assembleDebug :app:lintDebug --no-daemon --max-workers=1 "-Pkotlin.compiler.execution.strategy=in-process" "-Pkotlin.incremental=false" --console=plain
```

APK: `app/build/outputs/apk/debug/app-debug.apk`. É APK debug; não há assinatura de produção configurada. O APK gerado e caches de SDK/Gradle não são versionados: gere novamente em casa.

- `abrir_karaoke_leve.bat`: abre o emulador/app já instalado, sem compilar nem copiar músicas.
- `testar_capas_karaoke.bat`: compila, instala, prepara a coleção local e logs; pode demorar pela cópia de músicas. `KARAOKE_SOURCE` permite selecionar a pasta da coleção. Não executar para testar apenas online sem revisar suas operações de mídia.
- `scripts/instalar-apk.ps1`: instalação não streamed e espera pelo serviço Android, evitando o Broken pipe encontrado antes.
- `scripts/configurar-youtube.ps1`: configura a chave privada no dispositivo debug. Música, fotos, fila e AVD do computador atual não são transferidos pelo Git.
- `reports/finalization/redesign-probe/`: helpers selecionados para capturas e preservação de preferências; exigem Python para conferir restauração. Não sobrescrever baseline existente; sempre restaurar após QA. No Windows, preferir UTF-8 explícito e conferir dados, pois ADB acrescenta CR em separadores de linha.

Evite compilar com o emulador pesado aberto em máquinas com pouca RAM. Não use wipe-data, reinstalação sem preservar dados ou exclusão da coleção para resolver problemas de interface.

## Prompt pronto para a próxima sessão

```text
Continue o projeto KaraokeIA a partir da branch main. Leia primeiro docs/CONTINUAR_EM_CASA.md, docs/redesign-requisitos.md, docs/redesign-palco.md, docs/modo-online.md e docs/producao.md.

Estamos no checkpoint do redesign inspirado no PS4, com identidade roxa/rosa e carrossel horizontal. O código compila e o APK foi instalado no Android TV, mas a validação completa ainda está pendente. Preserve funcionalidades, dados reais, músicas do pendrive, fotos, fila, regras de pontuação e configuração privada. Não adicione emojis decorativos, músicas fictícias ou bibliotecas pesadas. Não embuta a chave YouTube no APK/Git.

Antes de editar, divida trabalho independente entre agentes de UI/Android, performance e QA/build, respeitando os arquivos compartilhados. Inspecione o estado atual; não refaça o redesign do zero nem afirme testes que não executou.

Priorize: (1) confirmar remoção e persistência da fila; (2) validar Cantar agora em tela cheia e controles por OK, pausa, retorno e foco na capa; (3) completar duas músicas, mostrar resultado/pódio/foto/QR antes da próxima, e validar a última com fila vazia; (4) revalidar offline, favoritos/histórico e os seis menus pelo controle; (5) conferir telefone, telas pequenas, teclado e capas; (6) corrigir problemas encontrados e registrar resultados reais. A pontuação continua recreativa: 1º 96–100, 2º 90–95, 3º 87–89. Câmera/QR, áudio físico, codecs e TV Box real ainda precisam de validação.

Use a referência PS4 original da conversa: marca à esquerda, menu superior horizontal, fundo roxo abstrato com ondas discretas, bordas rosa neon, capa selecionada maior, título/artista e Cantar agora/favorito/fila abaixo. Veja docs/imagens para o estado implementado. Categorias online usam canais reais; popularidade é local aos resultados retidos, não global YouTube.

Prepare SDK/local.properties e app/.env privados neste computador; o Git não transfere AVD, coleção completa, chave ou APK gerado. Compile, instale e teste a versão final; entregue relatório de alterações, testes, limitações e caminho do APK. Consulte o arquivo de continuidade para comandos e pendências detalhadas.
```
