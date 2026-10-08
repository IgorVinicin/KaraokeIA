# Redesign Completo do KARAOKE — Interface Inspirada no PS4 + Otimização + Build

## 1. Preparação e acionamento dos agentes

Antes de iniciar qualquer alteração, carregue todas as skills, superpowers, instruções e agentes disponíveis que possam contribuir para esta tarefa.

Acione, preferencialmente, os agentes responsáveis por:

- **UI/UX Design:** reformulação visual, navegação e identidade do aplicativo.
- **Android/Kotlin:** implementação da nova interface e integração com as funcionalidades existentes.
- **Performance:** otimização de renderização, memória e carregamento.
- **QA/Testes:** validação de funcionalidades, navegação e estabilidade.
- **Build/Release:** compilação, verificação e geração do APK final.

Caso não existam agentes específicos, assuma essas responsabilidades e execute as etapas de forma coordenada.

## 2. Objetivo principal

Realizar um **redesign completo do aplicativo Palco Online**, utilizando a imagem de referência anexada como base visual principal.

A nova interface deve ser inspirada no menu inicial do PlayStation 4, adaptada para um aplicativo de karaokê moderno, com identidade própria.

O objetivo é substituir o design atual por uma interface premium, fluida, organizada e otimizada para televisores.

**Importante:** não quero apenas trocar cores ou adicionar componentes visuais. Quero uma reformulação completa da experiência visual e da navegação, preservando as funcionalidades existentes.

## 3. Identidade visual

Utilize a imagem anexada como referência para:

- Layout horizontal inspirado no PS4.
- Fundo em tons de roxo escuro com ondas abstratas e gradientes suaves.
- Detalhes em rosa neon.
- Elementos translúcidos e efeitos de profundidade discretos.
- Capas de músicas organizadas horizontalmente.
- Destaque visual para o item selecionado.
- Tipografia moderna, limpa e legível em televisores.

Evite fundos de discoteca com pessoas, multidões ou iluminação excessiva.

O fundo deve ser elegante, abstrato e discreto, semelhante ao da referência final.

Não utilize elementos gráficos desnecessários que prejudiquem a legibilidade ou o desempenho.

## 4. Nova tela inicial

A tela inicial deverá seguir a organização da imagem.

### Menu superior

Implementar uma navegação horizontal contendo:

- Início
- Pesquisar
- Categorias
- Ranking
- Fila
- Configurações

Utilizar ícones consistentes e animações discretas ao selecionar cada opção.

### Carrossel principal de músicas

Exibir as músicas populares em uma sequência horizontal de capas, semelhante à biblioteca de jogos do PS4.

Comportamento esperado:

- A capa selecionada aumenta suavemente.
- Uma borda rosa neon destaca a seleção.
- As demais capas permanecem menores.
- A navegação ocorre pelas setas do controle remoto.
- As capas devem ser carregadas dinamicamente a partir do catálogo existente.
- Caso uma música não possua capa, utilizar uma imagem padrão elegante.
- Evitar carregamentos repetidos e manter cache das imagens.

### Informações da música

Ao selecionar uma capa, apresentar abaixo:

- Nome da música.
- Nome do artista.
- Botão "Cantar agora".
- Botão para adicionar à fila.
- Botão para adicionar aos favoritos.

As informações devem atualizar automaticamente conforme a seleção.

Não adicionar a seção de categorias abaixo do carrossel. As categorias devem permanecer acessíveis exclusivamente pelo menu superior.

## 5. Reformulação das demais telas

Aplicar a mesma identidade visual em todo o aplicativo, incluindo:

- Pesquisa de músicas.
- Categorias e filtros.
- Ranking de músicas populares.
- Fila de reprodução.
- Favoritos.
- Histórico.
- Configurações.
- Tela de reprodução do karaokê.
- Tela de pontuação final.
- Demais telas existentes.

A interface deve parecer um único produto, com padrões consistentes de cores, espaçamento, componentes e animações.

### Tela de reprodução

A reprodução deve priorizar o vídeo, com opção de tela cheia.

Ao pressionar OK no controle remoto, exibir controles discretos sobrepostos ao vídeo, sem comprometer a visualização da letra.

Preservar a reprodução, a sincronização do conteúdo, a pontuação e os demais recursos já implementados.

## 6. Navegação Android TV

A experiência deve funcionar integralmente pelo controle remoto.

Garantir:

- Navegação pelas setas direcionais.
- Seleção pelo botão OK.
- Retorno pelo botão Voltar.
- Destaque visual claro do componente em foco.
- Transições suaves entre os elementos.
- Foco inicial adequado ao abrir cada tela.
- Restauração do foco ao retornar de uma tela.
- Ausência de componentes inacessíveis pelo controle.

Evitar qualquer dependência de mouse ou tela sensível ao toque.

## 7. Performance e otimização

Após implementar o novo design, realizar uma auditoria completa do código.

Identificar e corrigir:

- Componentes e funções sem utilização.
- Código duplicado.
- Recursos gráficos desnecessários.
- Imagens carregadas sem otimização.
- Operações pesadas na thread principal.
- Possíveis vazamentos de memória.
- Recriações excessivas de componentes.
- Carregamentos desnecessários.
- Problemas de gerenciamento do player.
- Dependências não utilizadas, após verificar seu impacto.

Priorizar desempenho em TV Boxes com hardware limitado.

Utilizar cache de imagens, carregamento sob demanda e animações leves sempre que apropriado.

**Não remover funcionalidades existentes durante a limpeza.**

Evitar refatorações desnecessárias em módulos que já funcionam corretamente.

## 8. Testes e validação

Após finalizar o redesign e a otimização, executar os testes necessários.

Validar principalmente:

1. Inicialização do aplicativo sem travamentos.
2. Navegação completa pelo controle remoto.
3. Carregamento do catálogo e das capas.
4. Pesquisa e seleção de músicas.
5. Funcionamento da fila.
6. Reprodução dos vídeos e músicas.
7. Entrada e saída do modo tela cheia.
8. Funcionamento dos favoritos e histórico.
9. Funcionamento da pontuação.
10. Estabilidade ao alternar entre telas repetidamente.

Realizar testes no emulador Android TV e, quando disponível, em TV Box real.

Não considerar a tarefa concluída apenas porque o aplicativo compilou.

## 9. Build final

Após a conclusão das alterações:

- Executar a limpeza do projeto Gradle.
- Verificar e corrigir os erros de compilação.
- Executar os testes disponíveis.
- Gerar uma nova build APK.
- Verificar a compatibilidade com Android TV.
- Instalar e testar o APK no emulador disponível.
- Confirmar que o aplicativo abre e permite navegar normalmente.
- Informar o caminho exato do APK gerado.

Caso exista configuração de assinatura de release, utilizá-la conforme as configurações já existentes, sem expor credenciais. Caso contrário, gerar uma build de debug para validação e informar que ela não é uma release assinada.

## 10. Entrega e relatório

Ao finalizar, apresentar:

- Resumo das alterações visuais realizadas.
- Principais melhorias de desempenho.
- Código removido ou refatorado.
- Resultados dos testes.
- Problemas encontrados e corrigidos.
- Limitações que ainda dependam de testes em hardware real.
- Localização do APK final.

## Regras obrigatórias

- A imagem anexada é a principal referência visual.
- Preservar todas as funcionalidades existentes.
- Não alterar desnecessariamente as regras de negócio.
- Não substituir dados reais por dados fictícios.
- Não deixar telas com o design antigo.
- Não adicionar bibliotecas pesadas sem necessidade.
- Não realizar alterações destrutivas no catálogo ou nos arquivos de músicas.
- Não afirmar que testes passaram sem executá-los.
- Não afirmar que o APK está funcionando sem validá-lo.

**Resultado esperado:** um aplicativo de karaokê totalmente reformulado, inspirado na experiência visual e de navegação do PlayStation 4, com identidade roxa e rosa, interface consistente, navegação fluida pelo controle remoto, código otimizado e APK compilado e testado.