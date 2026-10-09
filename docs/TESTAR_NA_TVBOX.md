# Instalar e testar na TV Box

Copie somente `dist/KaraokeIA-release.apk` para um pendrive ou para a TV Box. Instale pelo gerenciador de arquivos e permita a instalação por essa origem quando o Android solicitar. Android mínimo: 6.0.

Se usar ADB em uma TV Box conectada e identificada:

```powershell
adb -s SERIAL_DA_TVBOX install -r dist/KaraokeIA-release.apk
```

Não use desinstalação, limpeza de dados ou formatação do pendrive para resolver erros de assinatura. A assinatura release é diferente da debug; consulte `producao.md` se já houver outra instalação.

## Primeiro uso

1. Abra Karaokê e escolha Offline ou Online.
2. No offline, conecte o pendrive e permita o acesso aos arquivos quando solicitado. Mantenha os vídeos e seus metadados originais. O catálogo aceita os metadados existentes junto dos vídeos ou na subpasta `dados`.
3. No online, use internet e Android System WebView funcional. Para pesquisa por nome, abra Configurações → Configurar pesquisa por nome e informe o valor da chave de `app/.env`, sem o prefixo `YOUTUBE_KEY=`. A chave não está embutida no APK. Pesquisa por link não exige chave.
4. Para controle e foto por QR, telefone e TV Box devem estar na mesma rede; mantenha o app aberto na TV Box.

## Roteiro de aceitação

| Teste | Resultado esperado |
| --- | --- |
| Setas, OK e Voltar | Os seis menus e as ações ficam acessíveis; o foco aparece claramente |
| Pesquisa e retorno | Resultados reais; voltar do player preserva a seleção |
| Favoritos/histórico | Favorito permanece após fechar/reabrir; histórico registra reprodução real |
| Fila online | Adicionar duas músicas, remover uma por OK, reiniciar e confirmar somente a entrada escolhida foi removida |
| Fila offline | Adicionar/remover, reiniciar e confirmar persistência; mudar a fila pelo celular durante o resultado atualiza a próxima música |
| Tela cheia online | Cantar agora inicia o vídeo; OK abre controles, pausa/retomada preserva o tempo, ocultar não reinicia o vídeo |
| Término | Resultado vem antes da próxima; Cantar próxima confirma avanço; última música não oferece próxima |
| Ranking/pódio | Faixas 1º 96–100, 2º 90–95, 3º 87–89; resultado/foto correspondem à apresentação atual |
| Pendrive/Media3 | Os vídeos reproduzem com áudio, sincronização e codecs disponíveis na TV Box |
| Câmera e QR | Quando houver câmera disponível, foto e download pelo telefone funcionam para a apresentação correta |
| Estabilidade | Alternar biblioteca/player e cantar por uma sessão prolongada sem travar |

A pontuação é recreativa, aleatória; não mede voz nem afinação. Se um vídeo YouTube bloquear incorporação, escolha outro ou utilize a ação YouTube; disponibilidade é definida pelo canal/plataforma.

## Registrar um problema

Informe modelo da TV Box, versão Android, modo online/offline, passos e música usada. Se houver ADB disponível, colete o crash depois de reproduzir o problema:

```powershell
adb -s SERIAL_DA_TVBOX logcat -b crash -d > tvbox-crash.txt
```

Não envie o `.env`, a chave YouTube, o keystore ou `signing.properties` junto do relato.
