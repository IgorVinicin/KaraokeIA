# Preparação para produção

O build `release` remove código e recursos não utilizados com R8. O arquivo
`app/proguard-rules.pro` preserva o método chamado pelo JavaScript do player.
As bibliotecas também fornecem suas próprias regras de preservação.

## Verificar e gerar

No Windows:

```powershell
.\gradlew.bat :app:assembleRelease :app:lintDebug --no-daemon --max-workers=1
```

O APK é gerado em `app/build/outputs/apk/release/app-release-unsigned.apk`.
Ainda não há configuração de assinatura de produção. Esse arquivo não pode
ser instalado antes de ser assinado. Guarde a chave de assinatura fora do Git;
a mesma assinatura será necessária para atualizações que preservem os dados.
Não substitua a assinatura por uma chave debug para distribuir aos clientes.

## O que ainda precisa ser concluído

- Configurar a assinatura definitiva e testar a versão otimizada na TV Box real:
  vídeo, codecs, controle remoto, pendrive, câmera e QR na mesma rede.
- Preparar a busca online para distribuição. O script `configurar-youtube.ps1`
  usa `run-as`, disponível no APK debug; ele não provisiona a versão release.
  Links do YouTube continuam disponíveis sem chave. A busca por nome requer
  um serviço ou provisionamento próprio de produção, conforme `modo-online.md`.
- Validar o acesso aos arquivos no equipamento do cliente. O acesso amplo ao
  armazenamento continua necessário no fluxo atual do pendrive; uma eventual
  distribuição pela loja precisa de uma revisão específica desse fluxo.
- Definir com o cliente como gerenciar e excluir as fotos e o histórico.

Esta etapa não altera o design, não publica o aplicativo e não migra nem
apaga músicas, filas, fotos ou pontuações existentes.
