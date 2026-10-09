# Busca SoltaVoz

O APK envia apenas o nome pesquisado a `GET /search?q=...`. Este serviço adiciona
`karaoke` à busca e consulta a YouTube Data API. A chave fica exclusivamente no
servidor. As músicas continuam sendo reproduzidas pelo player oficial do YouTube.
No SoltaVoz 1.2, a busca consulta este serviço automaticamente apenas quando
não encontra resultados no catálogo local, sem seleção de modo online/offline.

## Rodar e publicar

1. Instale Node.js 22 ou superior.
2. Copie `.env.example` para `.env` **nesta pasta** e preencha `YOUTUBE_API_KEY`.
3. Ative a YouTube Data API v3 no projeto Google Cloud. Restrinja a chave a essa
   API e, se a hospedagem tiver IP de saída fixo, a esse IP.
4. Execute `npm test` e `npm start` nesta pasta. Teste `http://127.0.0.1:8080/health`.
5. Hospede atrás de um proxy com HTTPS. Em hospedagem de contêiner, use o Dockerfile
   e injete a variável como segredo do provedor; não copie `.env` para a imagem.
   Configure `HOST=0.0.0.0` quando a plataforma precisar acessar a porta do contêiner.
6. Se houver proxy, configure `TRUST_PROXY=true` **somente** quando ele substituir
   `X-Forwarded-For` e impedir acesso direto ao serviço. Sem isso, o limite por IP
   será aplicado ao endereço do proxy, compartilhado por todos os usuários.

Não há hospedagem ou chave provisionada por este código. `/health` confirma que
o processo está ativo; não consome cota nem verifica a chave no Google.

## Conectar o APK

Na raiz do projeto, antes de compilar, defina somente o endereço público:

```powershell
$env:SOLTAVOZ_SEARCH_URL = 'https://busca.seu-dominio.com'
.\scripts\preparar-release.ps1
```

`SOLTAVOZ_SEARCH_URL` não é um segredo. Não inclua `YOUTUBE_API_KEY` no Gradle,
BuildConfig, assets ou `.env` do app. Quando o endereço está configurado, o app
pesquisa pelo nome sem pedir chave ao usuário. O build valida que o endereço usa
HTTPS e não contém credenciais. Sem endereço, a configuração local é mantida para
uso pessoal e a pesquisa exige uma chave salva no aparelho.

A release também exige o `signing.properties` e o keystore já utilizados pelo
projeto. Preserve o mesmo certificado para atualizar instalações existentes.

## Limites

- Cache de 15 minutos com até 200 consultas; buscas simultâneas iguais compartilham a chamada.
- Até 20 requisições por IP/minuto e 4 consultas diferentes em processamento.
- Até 80 chamadas ao YouTube por dia UTC por padrão, configurável na `.env`.
- Timeout de 10 segundos na API; erros não revelam a chave nem a URL da consulta.
- Sem autenticação compartilhada no APK: o endereço é público e o orçamento global limita o uso.

Execute **uma instância**. Cache e limites ficam na memória e reiniciam com o
processo. Para múltiplas instâncias ou reinícios frequentes, use um armazenamento
compartilhado para orçamento/cache e rate limiting no gateway. Ajuste o limite
conforme a cota efetiva do seu projeto no Google Cloud; o servidor não mede a
cota restante. Configure também alertas de uso no provedor.

Referências: [busca oficial](https://developers.google.com/youtube/v3/docs/search/list),
[configuração da API](https://developers.google.com/youtube/v3/getting-started).
