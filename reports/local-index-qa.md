# QA da indexação local

Data: 09/10/2026.

## Validação preparada

A suíte Android tem 36 testes, incluindo 13 novos cenários nesta melhoria:

- Nove cenários de índice: catálogo persistente sem acesso ao HD; busca por nome/artista com normalização e paginação; desconexão e reconexão; fallback sem armazenamento; falha de leitura de subdiretório; pesquisa literal por `%`/`_`; volumes simultâneos; metadados parcialmente gravados; inclusão, exclusão, renomeação e atualização incremental.
- Um cenário de Home carregando o cache e pesquisando sem internet enquanto a verificação do armazenamento está bloqueada.
- Um cenário com MP4 AAC válido gerado no próprio teste, extração de duração, reabertura do índice, resolução do caminho e reprodução Android até o final.
- Um cenário Compose com Home e tema reais, navegação D-pad entre abas, saída do campo de pesquisa pelo direcional, pesquisa local e limpeza da busca. O teste gera uma captura da tela quando executado.
- Um benchmark comparativo com 3.000 pares de arquivos, utilizando a implementação anterior preservada em `BaselineMusicRepository`. Mede primeira indexação, leitura em cache, reabertura, verificação incremental, pesquisa e alteração de um único metadado. Reabertura e verificações repetidas usam mediana de cinco amostras; pesquisas usam nove.

Os cinco testes existentes de `MusicRepositoryTest` foram adaptados para o banco SQLite e a retenção do catálogo de diferentes volumes.

## Resultado e limites

As fontes da primeira versão da suíte compilaram durante o build de integração. A execução por instrumentação, o benchmark e as capturas de tela **não foram realizados**: o usuário pediu para pular emuladores porque o computador não suporta a carga. O emulador iniciado nesta tarefa foi encerrado, as esperas adb foram canceladas e nenhum APK foi instalado nele nesta rodada.

Não existem medições antes/depois desta rodada. Não há teste confirmado em TV Box físico, reinicialização real, conexão USB física ou reprodução na TV. Os arquivos de teste estão preservados para execução posterior num aparelho conectado.

Uma validação leve de SQL foi executada no computador: **sete testes passaram** usando Python/SQLite. O script `reports/local-index/test_sql_from_repository.py` extrai diretamente do código Kotlin o esquema, os índices, a consulta de pesquisa e as substituições de escape. As consultas extraídas validaram parâmetros normalizados de nome/artista, paginação, pesquisa literal por `%`/`_`/barra invertida, prioridade ao volume disponível quando há códigos repetidos, desempate determinístico e retenção dos caminhos/duração/disponibilidade. O resultado está em `reports/local-index/host-sql-results.json`, com hash da fonte testada. Essa execução valida o SQL no host; não executa o normalizador Kotlin ou o comportamento Android, nem mede o desempenho no TV Box.

Para executar os testes instrumentados posteriormente, com aparelho Android disponível, use `gradlew.bat connectedDebugAndroidTest`. O benchmark gera `files/local-index-benchmark.txt` e o teste de Home gera `files/home-navigation-search.png` no armazenamento privado da aplicação alvo.
