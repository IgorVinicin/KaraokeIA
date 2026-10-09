# Teste no TV box — SoltaVoz 1.4

Instale `SoltaVoz-1.4.0-debug.apk`. O APK `SoltaVoz-debug.apk` sem versão no
nome pertence à entrega anterior. Primeiro login exige internet; depois a
busca local funciona durante a autorização offline vigente.

1. Conecte o HD/pendrive, autorize o acesso aos arquivos e abra o catálogo.
   Na primeira leitura, acompanhe a indicação de indexação. Pesquise uma
   música e reproduza para conferir caminho, imagem e áudio.
2. Feche e abra o app. Confira que o catálogo anterior aparece enquanto a
   verificação ocorre em segundo plano. Compare o tempo percebido com a
   primeira abertura; não há medições Android desta entrega.
3. Busque título e artista, alternando maiúsculas, minúsculas e acentos.
   Desconecte a internet e repita. Teste mais de uma página de resultados.
4. Desconecte o HD. O catálogo deve permanecer, com músicas indisponíveis.
   Reconecte e confira que elas podem ser reproduzidas novamente.
5. Adicione, remova e renomeie arquivos e altere seus metadados. Use
   Atualizar catálogo se fizer isso enquanto o app permanece aberto.
6. Reinicie o TV box e confirme a persistência do catálogo. A autorização
   de acesso pode exigir nova validação após a reinicialização.
7. Use apenas o controle: percorra abas, pesquisa, limpeza, páginas,
   favoritos, fila e player. Confira foco visível e retorno à listagem.
8. Com internet, teste YouTube: carregamento, pausa, retomada, próxima música
   da fila e uma música indisponível para incorporação.

Detalhes técnicos e limites da validação em `docs/soltavoz-1.4.md` e
`reports/local-index-qa.md`. Nenhum teste em emulador foi executado nesta
rodada, conforme solicitado.
