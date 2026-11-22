# PDFeasy - AGENTS.md

## Stack
- **Linguagem & UI:** Kotlin 2.0, Jetpack Compose (Material 3), Coroutines & Flow
- **SDK:** `minSdk 26`, `targetSdk 34`, Manual DI (`AppContainer`, zero overhead no cold start)
- **Motor PDF:** AndroidX PDF / PDFium compat (renderização de tiles em pool de Bitmaps a 120FPS, busca de texto e suporte a senha)
- **Persistência:** Room (SQLite local para biblioteca, progresso e marcadores) + Preferences DataStore
- **Qualidade & Testes:** JUnit 5, Coroutines Test, Room Testing, Detekt

## Comandos
- **Build Debug:** `./gradlew assembleDebug`
- **Build Release (R8):** `./gradlew assembleRelease`
- **Lint & Análise Estática:** `./gradlew detekt lintDebug`
- **Testes Unitários (JUnit 5):** `./gradlew testDebugUnitTest`
- **Verificação Completa:** `./gradlew check`

## Mapa de Pastas
```text
app/src/main/java/com/cl0udshifter/pdfeasy/
├── core/
│   ├── model/   # Entidades de domínio imutáveis, estados, temas e erros selados
│   ├── pdf/     # Motor de renderização de páginas/tiles, busca de texto e sessão do PDF
│   ├── data/    # Room DAOs, entidades SQLite, DataStore e implementação de repositórios
│   └── domain/  # Contratos (interfaces) de repositórios e casos de uso expostos para a UI
└── ui/          # Telas Compose, ViewModels, Canvas 120FPS com ColorMatrix e navegação
docs/
└── core-contract.md # Contrato formal de modelos e interfaces entre Core e UI
```
