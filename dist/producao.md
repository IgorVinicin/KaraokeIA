# Release para TV Box

A versão 1.0.1 (versionCode 2) utiliza assinatura de produção própria, R8 e redução de recursos. A ponte JavaScript do player YouTube permanece preservada em app/proguard-rules.pro.

Pacote: com.araujo.karaoke_ia. Android mínimo: 6.0 (API 23). Compile/target SDK: 35. O APK não inclui bibliotecas nativas próprias nem limita uma arquitetura de CPU; os codecs disponíveis dependem do Android da TV Box.

## Gerar e verificar

Requisitos locais: SDK Android, JDK 17 e Python 3. Neste computador o JDK está em .tools/jdk-17.0.20.1+1. Em outro computador, configure JAVA_HOME17.

```powershell
.\scripts\preparar-release.ps1
```

O helper compila assembleRelease, executa lintRelease, verifica assinatura com apksigner, confere pacote e ausência de debuggable com aapt, verifica arquivos privados/chave local no APK e copia o resultado para:

- dist/KaraokeIA-release.apk
- dist/KaraokeIA-release.sha256

O APK original fica em app/build/outputs/apk/release/app-release.apk. Build de release sem assinatura configurada é rejeitada; debug continua disponível separadamente. Consulte VALIDACAO_EM_CASA.md para os resultados executados.

## Assinatura e atualizações

signing.properties e .signing/karaoke-release.jks são privados e ignorados pelo Git. A chave é RSA 4096, protegida por senhas aleatórias. Faça backup privado de ambos: a mesma chave é necessária para atualizar instalações sem perder dados. Não envie esses arquivos junto com o APK.

scripts/criar-assinatura.ps1 cria uma assinatura somente quando não há outra. Não regenere a chave para futuras versões. Aumente versionCode a cada atualização distribuída.

Uma instalação debug anterior usa outra assinatura. Se o Android rejeitar a atualização por assinatura incompatível, não desinstale automaticamente: preserve os dados da instalação anterior ou reutilize sua assinatura original quando aplicável. A coleção do pendrive permanece externa ao APK.

## Configuração do YouTube em release

O .env do computador não acompanha o APK. Na TV Box, escolha Online → Configurações → Configurar pesquisa por nome, informe apenas o valor de YOUTUBE_KEY e salve. O campo é mascarado; a configuração fica na área privada no_backup. É possível removê-la pelo mesmo painel. Links funcionam sem essa configuração.

O script configurar-youtube.ps1 continua exclusivo do debug. Não é necessário run-as no release. Configure a YouTube Data API e a cota da sua chave no Google Cloud. Provisionamento central de vários clientes por backend com autenticação e controle de cota é uma etapa separada.

## Teste de aceitação

Siga TESTAR_NA_TVBOX.md. Os testes no hardware substituem o emulador neste computador, por solicitação do usuário. Build e verificações estáticas não comprovam áudio físico, câmera, controle, pendrive, codecs ou reprodução de longa duração na TV Box.