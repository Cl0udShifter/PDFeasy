# DESIGN.md - PDFeasy UI Specification (Direção 1: Papelaria Editorial)

Este documento define o sistema visual, os tokens Material 3, a tipografia offline, as matrizes de cor na GPU e as regras ergonômicas da camada de UI (`com.cl0udshifter.pdfeasy.ui`) em Jetpack Compose.

## 1. Direção Visual e Referências
- **Nome da direção:** Papelaria Editorial
- **Referências base:**
  - `design-taste-frontend` (`editorial-greeting`): superfície limpa, separação por degrau tonal sem sombras em repouso, numerais tabulares (`tnum`) e orçamento de cor de destaque restrito (`<= 2%` da tela).
  - Screenrove **Craft** (`craft-product-all-docs`, 2026-10-21): estética de papel quente (`Sunrise`), separação por contraste tonal e hairlines de baixa opacidade, contraste entre interface sans e títulos editoriais serifados.
  - Screenrove **Medium** (`medium-product-library`, `medium-product-article`, 2026-10-22) e **Glasp** (`glasp-product-pdfs`, `glasp-pdf-reader`, 2026-10-22): contraste de tinta sobre papel sem ruído visual, badges discretos de documento protegido e estado vazio direto.
- **Princípios inegociáveis:**
  1. Zero sombras (`shadowElevation = 0.dp`) em cards e linhas em repouso; profundidade vem de degraus tonais.
  2. Sombras e bordas `1.dp` aparecem apenas em controles flutuantes sobre o PDF no modo leitor (`ReaderBottomDock`, `ReaderThemePill`, `ReaderTopPill`).
  3. Zero gradientes decorativos, zero ícones dentro de quadrados coloridos, zero blocos placeholder ou emojis.
  4. Alvos de toque mínimos de `48.dp x 48.dp` em toda ação interativa, priorizando uso com uma mão na metade inferior da tela.

---

## 2. Paleta de Cores (Material 3 Customizado)

### 2.1 Light ColorScheme (Tema Padrão do App)
| Token Compose | Hex | Uso principal |
| :--- | :--- | :--- |
| `background` | `#FAF8F4` | Fundo principal da Biblioteca (papel off-white quente) |
| `onBackground` | `#1F201D` | Títulos e texto principal |
| `surface` | `#FAF8F4` | Superfície base e folhas |
| `onSurface` | `#1F201D` | Texto principal sobre superfícies |
| `surfaceVariant` | `#F2EDE3` | Cards da Biblioteca e barra de busca |
| `onSurfaceVariant` | `#636361` | Metadados secundários (páginas, tamanho, placeholders) |
| `surfaceContainerLowest` | `#FFFEFC` | Folha miniatura dentro do card da biblioteca |
| `surfaceContainerLow` | `#F2EDE3` | Cards tonais e campo de busca |
| `surfaceContainer` | `#EDE6D9` | Superfície intermediária em sheets e menus |
| `surfaceContainerHigh` | `#E8E1D3` | Topo do card (área da miniatura) e chips neutros |
| `surfaceContainerHighest` | `#DFD7C8` | Trilhas inativas (barra de progresso e scrubber) |
| `primary` | `#1B6E66` | CTA primário (`+ Abrir PDF`), barra de progresso ativa e match atual |
| `onPrimary` | `#FAF8F4` | Texto e ícone sobre `primary` |
| `primaryContainer` | `#D7ECE8` | Estado ativo do botão de marcador no dock |
| `onPrimaryContainer` | `#0E3D38` | Ícone ativo sobre `primaryContainer` |
| `secondary` | `#1F201D` | Chip de filtro ativo e opção ativa no seletor de tema |
| `onSecondary` | `#FAF8F4` | Texto sobre `secondary` |
| `outline` | `#CFC7B8` | Bordas de campos focados e controles secundários |
| `outlineVariant` | `#E2DACA` | Hairlines `1.dp`, borda do dock flutuante e miniatura |
| `error` | `#9E2A2B` | Erro de senha inválida e ações destrutivas |
| `onError` | `#FFFFFF` | Texto sobre `error` |
| `errorContainer` | `#FDECEA` | Fundo de badge `PERMISSION_REVOKED` e alerta de erro |
| `onErrorContainer` | `#7A1D14` | Texto sobre `errorContainer` |

### 2.2 Dark ColorScheme (Quando o Android está em Modo Escuro)
| Token Compose | Hex | Uso principal |
| :--- | :--- | :--- |
| `background` | `#161513` | Fundo carvão quente da Biblioteca |
| `onBackground` | `#EBE6DD` | Títulos e texto principal |
| `surface` | `#161513` | Superfície base |
| `onSurface` | `#EBE6DD` | Texto principal |
| `surfaceVariant` | `#201E1B` | Cards tonais da Biblioteca |
| `onSurfaceVariant` | `#A8A298` | Metadados e placeholders |
| `surfaceContainerLowest` | `#1C1A17` | Folha miniatura no escuro |
| `surfaceContainerLow` | `#201E1B` | Cards e campo de busca |
| `surfaceContainer` | `#262320` | Sheets e diálogos |
| `surfaceContainerHigh` | `#2E2A26` | Área da miniatura e chips neutros |
| `surfaceContainerHighest` | `#38332E` | Trilhas de progresso e scrubber |
| `primary` | `#5DB7AE` | CTA primário, progresso e destaque ativo |
| `onPrimary` | `#0C2926` | Texto/ícone sobre `primary` |
| `primaryContainer` | `#1B4742` | Estado ativo do marcador |
| `onPrimaryContainer` | `#BCEAE4` | Ícone sobre `primaryContainer` |
| `secondary` | `#EBE6DD` | Chip de filtro ativo e segmento selecionado |
| `onSecondary` | `#161513` | Texto sobre `secondary` |
| `outline` | `#4A443C` | Bordas de campos ativos |
| `outlineVariant` | `#332F29` | Hairlines `1.dp` |
| `error` | `#F28B82` | Mensagens de erro |
| `onError` | `#3D0A09` | Texto sobre `error` |
| `errorContainer` | `#4B1C1B` | Fundo de badge `PERMISSION_REVOKED` |
| `onErrorContainer` | `#FDECEA` | Texto sobre `errorContainer` |

### 2.3 Tokens Semânticos Estendidos (`PdfEasyColors` via `CompositionLocal`)
| Token | Light | Dark | Regra de uso |
| :--- | :--- | :--- | :--- |
| `passwordBadgeBg` / `passwordBadgeInk` | `#E8E1D3` / `#1F201D` | `#2E2A26` / `#EBE6DD` | `PdfDocumentItem.isPasswordProtected == true` |
| `movedBadgeBg` / `movedBadgeInk` | `#FBF0D9` / `#8A4B08` | `#3A2B12` / `#F3C474` | `DocumentAvailability.MISSING_OR_MOVED` |
| `revokedBadgeBg` / `revokedBadgeInk` | `#FDECEA` / `#9E2A2B` | `#421B1A` / `#F5A39E` | `DocumentAvailability.PERMISSION_REVOKED` |
| `searchMatchOther` | `#FACC15` (38% alpha) | `#EAB308` (35% alpha) | Demais ocorrências de `TextSearchMatch` |
| `searchMatchCurrentFill` | `#1B6E66` (24% alpha) | `#5DB7AE` (30% alpha) | Match selecionado atualmente |
| `searchMatchCurrentStroke` | `#1B6E66` (`1.5.dp`) | `#5DB7AE` (`1.5.dp`) | Contorno do match selecionado |
| `readerBackdropOriginal` | `#EAE5DB` | `#201E1B` | Fundo ao redor das páginas em `PageRenderTheme.ORIGINAL` |
| `readerBackdropDark` | `#111110` | `#0B0B0A` | Fundo ao redor das páginas em `PageRenderTheme.DARK` |
| `readerBackdropSepia` | `#E2D6BF` | `#29231A` | Fundo ao redor das páginas em `PageRenderTheme.SEPIA` |

---

## 3. Tipografia e Fontes Offline (`res/font/`)

### 3.1 Arquivos Bundled (Licença OFL, 100% Offline)
Armazenar em `app/src/main/res/font/`:
- `Source Serif 4`: `sourceserif4_regular.ttf` (400), `sourceserif4_semibold.ttf` (600)
- `Inter`: `inter_regular.ttf` (400), `inter_medium.ttf` (500), `inter_semibold.ttf` (600)

### 3.2 Escala Tipográfica Compose
| Role Material 3 | Família | Peso | Tamanho / Altura de Linha | Tracking / Recursos | Uso |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `headlineMedium` | Source Serif 4 | SemiBold (600) | `28.sp` / `32.sp` | `-0.02.em` | Título `Biblioteca` |
| `titleLarge` | Source Serif 4 | SemiBold (600) | `22.sp` / `28.sp` | `-0.015.em` | Estado vazio e título de diálogos |
| `titleMedium` | Source Serif 4 | SemiBold (600) | `15.sp` / `20.sp` | `-0.01.em` | Título do documento no card (máx. 2 linhas) |
| `titleSmall` | Source Serif 4 | SemiBold (600) | `14.sp` / `18.sp` | `0.em` | Título do documento no HUD do leitor e sheets |
| `bodyLarge` | Inter | Regular (400) | `15.sp` / `22.sp` | `0.em` | Campo de busca e corpo de diálogo |
| `bodyMedium` | Inter | Regular (400) | `13.sp` / `19.sp` | `0.em` | Subtítulo de estado vazio e snippets de busca |
| `labelLarge` | Inter | Medium (500) | `13.sp` / `18.sp` | `0.em` | Botões primários e chips de filtro |
| `labelMedium` | Inter | Medium (500) | `12.sp` / `16.sp` | `tnum` | Contadores `42 / 248`, `3/19`, seletores de tema |
| `labelSmall` | Inter | Medium (500) | `11.sp` / `14.sp` | `tnum` | Metadados do card (`248 págs`, `42%`), badges |

Regra de figuras: aplicar `fontFeatureSettings = "tnum"` em todos os textos que exibem paginação, porcentagem, tamanho de arquivo ou contagem de ocorrências para evitar deslocamento horizontal durante o scrub.

---

## 4. Geometria, Espaçamento e Grid

- **Shapes (`MaterialTheme.shapes`):**
  - `extraSmall`: `6.dp` (destaques de busca)
  - `small`: `12.dp` (botões internos do dock)
  - `medium`: `18.dp` (cards da biblioteca)
  - `large`: `20.dp` (dock principal e modal bottom sheets)
  - `extraLarge`: `999.dp` (pílulas: barra de busca, chips, botão `+ Abrir PDF`, seletor de tema, HUD superior)
- **Margens e Ritmo:**
  - Gutter único horizontal e vertical na grade da biblioteca: `12.dp`.
  - Margem externa lateral simétrica: `18.dp` em telas compactas (`< 600.dp`), `24.dp` em telas médias/expandidas (`>= 600.dp`).
  - Colunas da Biblioteca: `GridCells.Fixed(2)` abaixo de `600.dp`, `GridCells.Adaptive(180.dp)` a partir de `600.dp`.
  - Espaçamento entre páginas no Leitor 120FPS: `10.dp`.

---

## 5. Especificação de Telas e Mapeamento ao Core

### 5.1 Tela 1: Biblioteca Local (`LibraryScreen`)
Consome `LibraryRepository.observeLibrary(searchQuery)` e `LibraryRepository.refreshAvailability()` na entrada/retorno de foco.

1. **Cabeçalho:** Título `Biblioteca` à esquerda + botão pílula preenchido `Abrir PDF` (`primary`, altura mínima `40.dp` visual com área tátil `48.dp`) à direita acionando `ActivityResultContracts.OpenDocument(arrayOf("application/pdf"))`.
2. **Busca:** Pílula tonal (`surfaceContainerLow`, `44.dp` de altura) com ícone de lupa, placeholder `"Buscar por nome do arquivo..."` e ação limpar quando `query.isNotEmpty()`. Oculta quando a biblioteca está completamente vazia e sem busca ativa.
3. **Filtros rápidos:** Chips `Todos (N)` e `Em leitura` (`lastOpenedAtEpochMillis != null` ou `progress != null`). Exibir chip `Indisponíveis` apenas se houver ao menos um item com `availability != DocumentAvailability.AVAILABLE`.
4. **Card de Documento (`PdfDocumentItem` + `ReadingProgress?`):**
   - **Bloco superior (`108.dp`):** Fundo `surfaceContainerHigh` contendo uma folha esquemática em `surfaceContainerLowest` com borda `1.dp` (`outlineVariant`) simulando proporção editorial do documento (sem decodificar PDF na listagem para manter abertura instantânea).
   - **Badge único no canto superior direito (prioridade exclusiva):**
     1. `PERMISSION_REVOKED`: `"Sem acesso"` (`revokedBadgeBg` / `revokedBadgeInk`)
     2. `MISSING_OR_MOVED`: `"Movido"` (`movedBadgeBg` / `movedBadgeInk`)
     3. `isPasswordProtected`: `"Senha"` (`passwordBadgeBg` / `passwordBadgeInk`)
   - **Bloco inferior:** Título (`displayName` limpo sem `.pdf` no final, máx. 2 linhas com ellipsis) + linha de rodapé (`pageCount` ou `"--"` à esquerda; `completionFraction` em `%` se aberto, ou `sizeBytes` formatado se nunca aberto).
   - **Barra de progresso:** Trilha `3.dp` na borda inferior do card (`surfaceContainerHighest` + preenchimento `primary` proporcional a `progress?.completionFraction ?: 0f`).
   - **Interações:**
     - Toque em documento `AVAILABLE` abre o Leitor.
     - Toque em `MISSING_OR_MOVED` / `PERMISSION_REVOKED` (ou toque longo em qualquer card) abre `ModalBottomSheet` com ações: `Re-vincular PDF` (SAF), `Remover da biblioteca` (`LibraryRepository.removeFromLibrary`).
5. **Estado Vazio:**
   - Sem documentos catalogados: ícone linear de folha (`36.dp`, `1.5.dp` stroke), título `"Sua estante está vazia"`, subtítulo `"Abra um PDF do dispositivo para ler sem anúncios ou distração."`, botão pílula `primary` `"Escolher PDF"`.
   - Busca sem resultados: mensagem `"Nenhum PDF encontrado para \"$query\""` com botão secundário `"Limpar busca"`.

### 5.2 Tela 2: Leitor de PDF a 120FPS (`ReaderScreen`)
Consome `PdfEngine`, `PdfSession`, `ReadingProgressRepository` e `BookmarkRepository`.

1. **Viewport Fullscreen e Pipeline 120FPS:**
   - Modo `edge-to-edge` puro. Um toque simples na área da página alterna a visibilidade do HUD (`AnimatedVisibility` com `fadeIn`/`fadeOut` + `slideInVertically`/`slideOutVertically` em `180ms`, `FastOutSlowInEasing`, sem realocar bitmaps).
   - **Zero alocações no frame loop:**
     - Renderizar páginas visíveis + pré-renderização de 1 página adjacente (`currentPage - 1 .. currentPage + 1`) via `PdfSession.renderTile`, liberando bitmaps fora da janela de pré-carga com `PdfSession.recycleBitmap`.
     - Em zoom alto (`zoomScale > 1.35f`), solicitar recorte visível de alta resolução via `TileRequest(cropNormalized = ...)` sobrepondo o tile nítido sobre a página base.
     - O objeto `ColorMatrixColorFilter` de cada `PageRenderTheme` é pré-alocado e passado diretamente ao `Paint` / `drawImage(..., colorFilter = ...)` na GPU, garantindo troca de tema instantânea sem chamar `renderTile` novamente.
   - **Fórmulas `ColorMatrix` para GPU:**
     - `ORIGINAL`: `null` (sem filtro).
     - `DARK`: inversão suave preservando leitura noturna sem brilho estourado (fundo branco vira `#141414`, texto preto vira `#ECECEC`):
       `R' = -0.86*R + 236`, `G' = -0.86*G + 236`, `B' = -0.86*B + 236`, `A' = A` (escala de RGB em `-0.86f` e offset `+236f`).
     - `SEPIA`: matriz quente de baixo contraste azul mantendo legibilidade de figuras e diagramas:
       Mistura `65%` original + `35%` tom sépia quente com redução de ganho azul (`0.84f` no canal B e leve atenuação de luminância para `#F4EAD5` no branco):
       - Linha R: `[0.95f, 0.08f, 0.02f, 0f, 0f]`
       - Linha G: `[0.04f, 0.88f, 0.03f, 0f, 0f]`
       - Linha B: `[0.02f, 0.06f, 0.72f, 0f, 0f]`
       - Linha A: `[0f, 0f, 0f, 1f, 0f]`
   - **Gestos:** Pinch-to-zoom (`1.0f..4.0f`), pan livre quando ampliado, double-tap alterna `1.0f` e `2.2f`, suporte a `ScrollMode.CONTINUOUS_VERTICAL` e `ScrollMode.CONTINUOUS_HORIZONTAL`.

2. **Ergonomia do HUD Flutuante (Mão Única):**
   - **Topo (`statusBarsPadding` + `8.dp`):**
     - Pílula esquerda: título truncado do PDF (`titleSmall`).
     - Pílula direita (`tnum`): `"${currentPageIndex + 1} / $pageCount"` (toque abre diretamente o `ModalBottomSheet` de **Marcadores**).
   - **Dock Inferior (`navigationBarsPadding` + `12.dp`, largura máxima `440.dp` centralizada para tablets):**
     - **Nível 1 (Pílula de Tema e Rolagem):**
       - 3 opções com swatch circular real: `Original` (`#FFFFFF`), `Escuro` (`#18181B`), `Sépia` (`#EFE2C8`). Troca atualiza `ReadingProgress.renderTheme` imediatamente e salva com debounce.
       - Divisor vertical `1.dp` + botão `40.dp` (área tátil `48.dp`) para alternar `ScrollMode` (`Vertical` / `Horizontal`).
     - **Nível 2 (Barra Principal de Polegar, `rounded 20.dp`):**
       - Estado padrão: `[Voltar]` + **Scrubber contínuo (`Slider`)** de `0 .. pageCount - 1` (exibe bolha indicadora flutuante `"Pág. X / N"` acima do polegar durante `onValueChange`) + `[Buscar]` + `[Marcador]` (estado preenchido em `primaryContainer` quando `observeIsBookmarked` é `true`; toque longo abre lista de marcadores).
       - Estado Busca Ativa (`searchOpen == true`): substitui o Nível 2 por campo de busca focado + contador `"${idx + 1}/$total"` (`tnum`) + botões `48.dp` `Anterior` e `Próximo` à direita para o polegar navegar entre `TextSearchMatch`, mais botão `Fechar`.

3. **Fluxos Auxiliares e Erros (`PdfCoreError`):**
   - `PasswordRequired` / `InvalidPassword`: exibe `AlertDialog` editorial com `OutlinedTextField` de senha, alternador de visibilidade, erro inline no caso de `InvalidPassword` e ações `Voltar` / `Abrir`.
   - `FileNotFoundOrMoved` / `PermissionDenied` / `CorruptedOrUnsupportedPdf`: exibe tela de estado de erro clara com explicação, opção de voltar à biblioteca ou re-selecionar arquivo via SAF.
   - `Intent.ACTION_VIEW` (`application/pdf` / `content://` / `file://`): `MainActivity` registra o documento via `LibraryRepository.registerOrUpdateDocument` e navega diretamente para o Leitor, preservando o histórico na volta para a Biblioteca.

---

### 5.3 Tela 3: Configurações (`SettingsScreen`)
Consome `SettingsRepository` (`AppSettings`) e `AppUpdateRepository` (`AppUpdateStatus`, `AppUpdateDownloadState`, `AppUpdateError`).

1. **Pontos de acesso:**
   - **Biblioteca (`LibraryHeader`):** botão `48.dp` com ícone de engrenagem discreto à esquerda de `Abrir PDF` (exibe ponto indicador em `primary` quando há atualização disponível).
   - **Leitor (`ReaderTopBar`):** pílula circular `38.dp` (área tátil `48.dp`) com ícone de engrenagem ao lado do contador de páginas; retornar das Configurações devolve o usuário ao leitor aberto.
2. **Cabeçalho e ritmo da página:**
   - Barra superior limpa (`0.dp` de sombra em repouso) com botão voltar `48.dp` e título `"Configurações"` (`titleLarge`, `Source Serif 4`).
   - Lista vertical rolável com largura máxima `560.dp` centralizada em telas expandidas, margem horizontal de `18.dp` / `24.dp` e blocos agrupados em superfícies `surfaceContainerLow` (`18.dp`, separadores `1.dp` `outlineVariant`, zero sombras).
3. **Seções e controles:**
   - **Aparência (`appTheme`):** seletor segmentado em pílula (`Sistema`, `Claro`, `Escuro`), aplicado imediatamente no `MainActivity` com ajuste automático de contraste das barras do sistema.
   - **Padrões de Leitura** (aplicados ao abrir novos documentos sem histórico salvo):
     - **Tema da página (`defaultRenderTheme`):** opções `Original`, `Escuro` e `Sépia` com swatches reais idênticos aos do leitor.
     - **Rolagem padrão (`defaultScrollMode`):** seletor segmentado `Vertical` / `Horizontal`.
     - **Zoom inicial (`defaultZoomScale`):** slider editorial compacto (`4.dp x 20.dp`, trilho `6.dp`) entre `1.0x` e `3.0x` com leitura numérica em `tnum`.
     - **Manter tela acesa (`keepScreenOn`):** switch aplicado continuamente enquanto a viewport de leitura estiver ativa.
   - **Sobre e atualizações (`AppUpdateRepository`):**
     - Versão instalada (`v${currentVersionName}`, build em `tnum`) e data/hora da última verificação.
     - Switches para `Incluir pre-releases` e `Verificar atualizações ao iniciar`.
     - Ação `Verificar atualizações` + painel de resultado inline: estado atualizado, cartão de nova versão com notas recolhíveis, barra de progresso durante `Downloading`, verificação/solicitação de `ACTION_MANAGE_UNKNOWN_APP_SOURCES`, disparo do instalador Android via `FileProvider` em `ReadyToInstall`, botão `Limpar cache de atualizações` e mensagens tipadas para cada `AppUpdateError`.
