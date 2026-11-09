# KaraokeIA — Sistema de Karaoke Inteligente

Aplicação Android desenvolvida para oferecer uma experiência de karaoke com reprodução de conteúdo multimídia, gerenciamento de sessões e controle remoto por dispositivos móveis.

O projeto integra uma aplicação Android com serviços externos, permitindo organizar a experiência de utilização e disponibilizar funcionalidades de controle e gerenciamento.

## Visão geral

O KaraokeIA foi desenvolvido considerando diferentes componentes de utilização:

- Aplicação principal para execução em dispositivos Android.
- Interface de reprodução e navegação de conteúdo.
- Controle remoto por dispositivo móvel.
- Comunicação por QR Code.
- Serviços auxiliares para pesquisa e gerenciamento.
- Integração com serviços externos para autenticação e gerenciamento de contas.

## Funcionalidades

### Reprodução multimídia
- Interface dedicada à experiência de karaoke.
- Reprodução de conteúdo utilizando ExoPlayer.
- Integração com Android Media3.
- Carregamento e apresentação de capas e informações visuais.

### Controle remoto
- Interface de controle por dispositivos móveis.
- Geração e leitura de QR Codes.
- Comunicação entre a aplicação principal e a interface de controle.
- Recursos de navegação e gerenciamento da experiência de reprodução.

### Integração com serviços externos
- Comunicação com serviços de backend.
- Integração com Supabase.
- Estrutura de gerenciamento de contas.
- Serviço externo de pesquisa de conteúdo.

### Administração
- Estrutura de interface administrativa.
- Organização de recursos de gerenciamento.
- Separação entre a aplicação principal e os componentes auxiliares.

## Tecnologias utilizadas

| Tecnologia | Aplicação |
|---|---|
| Kotlin | Linguagem principal da aplicação Android |
| Android SDK | Plataforma de desenvolvimento |
| Jetpack Compose | Construção da interface |
| Material 3 | Componentes visuais |
| Android Media3 | Reprodução multimídia |
| ExoPlayer | Engine de reprodução |
| ZXing | Geração e leitura de QR Codes |
| Coil | Carregamento de imagens |
| Supabase | Serviços de backend e gerenciamento de contas |
| Gradle | Automação de build |

## Arquitetura

O projeto possui uma estrutura modular, separando a aplicação Android dos serviços auxiliares.

```text
KaraokeIA/
├── app/          # Aplicação Android
├── admin-web/    # Interface administrativa
├── server/       # Serviços auxiliares
├── supabase/     # Recursos de integração
├── docs/         # Documentação
├── scripts/      # Scripts de automação
├── branding/     # Identidade visual
└── gradle/       # Configurações do Gradle
```

## Executando o projeto

### Pré-requisitos

- Android Studio.
- JDK 11 ou compatível com a configuração do projeto.
- Android SDK.
- Gradle Wrapper.
- Dispositivo Android ou emulador.

### Clonando o repositório

```bash
git clone https://github.com/IgorVinicin/KaraokeIA.git

cd KaraokeIA
```

### Configuração

Configure os serviços externos necessários para o funcionamento completo da aplicação.

O endereço público do serviço de pesquisa pode ser configurado por meio da variável:

```env
SOLTAVOZ_SEARCH_URL=https://seu-dominio.com
```

As credenciais e configurações privadas devem permanecer nos arquivos de ambiente apropriados, nunca versionadas no repositório.

### Executando a aplicação

No Windows:

```bash
gradlew.bat assembleDebug
```

No Linux ou macOS:

```bash
./gradlew assembleDebug
```

O APK de desenvolvimento será gerado em:

```text
app/build/outputs/apk/debug/
```

Também é possível importar o projeto diretamente no Android Studio e executá-lo em um dispositivo ou emulador.

## Build de produção

A geração de versões de produção utiliza configuração própria de assinatura.

É necessário configurar o arquivo `signing.properties` e o keystore correspondente, conforme as exigências definidas no Gradle.

O projeto também utiliza otimização e redução de recursos na configuração Release.

## Objetivo técnico

O projeto tem como objetivo explorar o desenvolvimento Android moderno, reprodução multimídia, integração com serviços externos, comunicação entre dispositivos e construção de interfaces utilizando Kotlin e Jetpack Compose.

## Autor

**Igor Vinicius**

[GitHub](https://github.com/IgorVinicin)
