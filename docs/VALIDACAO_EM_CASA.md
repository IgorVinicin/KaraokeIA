# Validação em casa — 08/10/2026

Continuação de `CONTINUAR_EM_CASA.md`. Durante a sessão o usuário solicitou encerrar o emulador, pois este computador não suporta o Android TV, e preparar um APK release assinado para testar na TV Box. Testes de dispositivo foram interrompidos; o foco passou para preparação e verificação do artefato de produção.

## Correções

- Resultado offline observa mudanças da fila. O avanço confirma e remove atomicamente a entrada exibida; uma fila alterada pelo celular não permite remover outra música por um clique desatualizado.
- Consultas de músicas no resultado offline executam em `Dispatchers.IO`, evitando acesso ao pendrive durante a composição da interface.
- No player online móvel, Ocultar controles passa a esconder a sobreposição, há uma ação para reabri-la e os botões quebram em linhas nas telas estreitas.
- Backup de QA grava um manifesto mesmo sem preferências existentes, verifica falhas do ADB e preserva também a pasta de músicas configurada.
- `scripts/compilar-em-casa.ps1` prepara SDK e JDK 17 locais sem incluir a chave YouTube no build.
- Release usa assinatura própria RSA 4096, R8 e redução de recursos; versão 1.0.1, versionCode 2.
- Configurações online permitem salvar/remover a chave no próprio APK release, com campo mascarado, gravação atômica em `no_backup` e atualização imediata do estado.
- A chave ausente/corrompida não interrompe a abertura; links permanecem disponíveis. Mudanças de chave invalidam o cache de pesquisa.
- Scripts verificam assinatura, pacote, ausência de `debuggable` e ausência de arquivos privados/chave local no APK.

## Ambiente

- Computador com aproximadamente 4 GB de RAM; compilar com o emulador encerrado.
- Android SDK 35 instalado. Java do Android Studio é 25; JDK Temurin 17.0.20.1 preparado em `.tools/`, ignorado pelo Git.
- AVD existente: `Television_1080p`, Android TV API 33 x86. Perfil de QA sem janela e sem áudio, 960×540, dois núcleos, 1536 MB, `swiftshader_indirect`.
- `local.properties` e `app/.env` são privados. A chave não foi exibida nem incluída no APK.

## Verificações registradas

| Verificação | Resultado |
| --- | --- |
| Regressão da fila offline | PASS: cabeça desatualizada, alteração antes do clique, duplicatas, recriação do repositório, fila vazia, observação/descarte |
| Limitação da regressão | Código real de QueueRepository com preferências em memória; não valida persistência em disco nem navegação Compose |
| Sintaxe dos helpers PowerShell | Aprovada |
| Debug: clean, assembleDebug e lintDebug | PASS, build completa em 25m44s; 0 erros, 48 avisos e 3 informações |
| testDebugUnitTest | NO-SOURCE; não existe suíte Gradle de testes unitários |
| APK debug instalado/abertura | Atualização -r com sucesso e MainActivity com Status: ok; captura da seleção de modo |
| Navegação/fila/player no Android | Não concluídos; emulador encerrado por solicitação do usuário |
| Normalização/validação da chave | PASS: 6 casos aceitos e 11 rejeitados, helper real compilado; não valida AtomicFile no Android |
| Chave local/YouTube Data API | PASS: uma pesquisa real retornou um vídeo, sem imprimir a chave; teste no computador, não no player Android |
| Compilação Kotlin release | PASS, incluindo novo painel de configuração |
| assembleRelease + lintRelease | PASS, build completa em 18m05s; 0 erros e 35 avisos |
| Assinatura | PASS: apksigner verifica v1 e v2, certificado próprio RSA 4096 |
| Manifesto do release | PASS: pacote correto, versão 1.0.1/code 2, API mínima 23/target 35, launcher TV, sem debuggable |
| Arquivos privados/chave local no APK | PASS: todas as entradas do ZIP inspecionadas; nenhum arquivo privado nem chave local em UTF-8/UTF-16 |
| Ponte YouTube após R8 | Método event(String,int) consta nas seeds preservadas |

Regressões reproduzíveis: `reports/finalization/queue-regression/run.ps1` e `reports/finalization/youtube-config-regression/run.ps1`. Evidência da abertura debug em `reports/home-qa/modes.png`. O backup da configuração de armazenamento ficou preservado em `reports/finalization/redesign-probe/baseline/` (ignorado); nenhum teste de alteração de fila/favoritos/ranking foi executado antes de encerrar o emulador.

## Artefato entregue

- APK: `dist/KaraokeIA-release.apk`, 3.990.412 bytes (aproximadamente 4 MB; debug anterior 23.087.122 bytes).
- SHA256: `14e3bff7eb9608bb5461fdb77d8b0b43ef7100cb7bc0dddfdd6fcf90411aa308`.
- Certificado SHA256: `197482aea7c0dd060e763d7d26451ea98f4aa748c4ef9bab3873130d8bbe37f5`.
- Checksum, instruções e mapa R8 acompanham o APK na pasta `dist/`. O mapa permite interpretar crashes da versão otimizada.
- Os avisos de assinatura são sobre metadados `META-INF` no esquema legado v1; a assinatura v2 do APK completo foi verificada. Não houve erro de assinatura.
- Auditoria dos 35 avisos release: 24 sobre versões de dependências, 2 sobre AGP, 6 sobre ícones, 2 sobre parâmetros Modifier e 1 sobre organização TOML; há também 3 informações sobre boxing de estado.

## Testes pendentes no hardware

Áudio físico, câmera, pendrive físico, codecs ARM, foco/teclado da TV, persistência real das configurações e estabilidade de longa duração precisam da TV Box. A assinatura foi configurada e o provisionamento por aparelho foi implementado. Backend com autenticação/cotas para provisionamento central de vários clientes continua sendo uma etapa separada. Roteiro: `TESTAR_NA_TVBOX.md`.
