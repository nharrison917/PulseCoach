# Project Structure (generated 2026-04-03)
# Android project -- package: com.pulsecoach -- Room v5

app/
  src/  Source code
    main/
      kotlin/com/pulsecoach/
        ble/  BLE scan, connect, HR stream
        data/  Room entities, DAOs, database
        model/  Pure data classes
        repository/  Data access layer
        ui/  Compose screens and components
        util/  Calculators, helpers, exporters
        viewmodel/  ViewModels (one per screen)
      res/  Android resources (layouts, strings, drawables)
      AndroidManifest.xml  Permissions, activities, services
    test/  Unit tests
  build.gradle.kts  Module build config and dependencies
  proguard-rules.pro  R8/ProGuard keep rules
gradle/
  wrapper/
.gitignore  Git ignore rules
build.gradle.kts  Module build config and dependencies
CLAUDE.md  Instructions for Claude Code
gradle.properties  Gradle and JVM flags
README.md  Project readme
settings.gradle.kts  Module declarations

## Dependencies
  - Compose
  - Room
  - Polar SDK
  - Navigation
  - Vico charts
  - Coroutines

## Navigation Routes
  - evaluation
  - live_session
  - profile_edit
  - profile_setup
  - session_history
  - settings