# Dependencies & Licenses — Infinite Studio

All dependency versions are pinned explicitly in `gradle/libs.versions.toml` (zero dynamic `+` versions, zero credential-injection plugins, zero analytics/ad SDKs, zero bundled unlicensed third-party fonts or stock art).

## 1. Gradle & Toolchain Pins

| Component | Version | License / Source |
|---|---|---|
| Android Gradle Plugin (`agp`) | `9.1.1` | Apache-2.0 (Google) |
| Kotlin (`kotlin`) | `2.2.21` | Apache-2.0 (JetBrains) |
| Java / JVM Target | JDK `21` | GPL-2.0 w/ CPE |
| Compile / Target SDK | Android API `36` (`minSdk = 26`) | Android SDK |

*(Note: `secrets-gradle-plugin` was explicitly removed so no `.env` secrets are ever compiled into `BuildConfig`.)*

## 2. Runtime Libraries (`app/build.gradle.kts`)

| Artifact | Group:Name | Version | Purpose | License |
|---|---|---|---|---|
| AndroidX Core KTX | `androidx.core:core-ktx` | `1.16.0` | `IntentCompat`, `FileProvider`, core extensions | Apache-2.0 |
| Lifecycle Runtime KTX / Compose | `androidx.lifecycle:lifecycle-runtime-*` | `2.9.0` | `collectAsStateWithLifecycle`, ViewModel scopes | Apache-2.0 |
| Lifecycle ViewModel Compose | `androidx.lifecycle:lifecycle-viewmodel-compose` | `2.9.0` | MVVM state holder integration | Apache-2.0 |
| Activity Compose | `androidx.activity:activity-compose` | `1.10.1` | `enableEdgeToEdge()`, `BackHandler`, Photo Picker / SAF launchers | Apache-2.0 |
| Jetpack Compose BOM | `androidx.compose:compose-bom` | `2025.05.00` | Aligned Compose UI, Graphics, Tooling | Apache-2.0 |
| Material 3 | `androidx.compose.material3:material3` | `1.3.2` | Floating docks, dialogs, sliders, switches, tabs | Apache-2.0 |
| Material Icons Extended | `androidx.compose.material:material-icons-extended` | `1.7.8` | Vector toolbar & inspector icons | Apache-2.0 |
| Kotlinx Serialization JSON | `org.jetbrains.kotlinx:kotlinx-serialization-json` | `1.8.1` | `.iastudio` v3 manifest, recipes, provider JSON wire format | Apache-2.0 |
| OkHttp | `com.squareup.okhttp3:okhttp` | `4.12.0` | `LoopbackCompanionAdapter` HTTP client (`127.0.0.1:8787`) | Apache-2.0 |

## 3. Test Libraries (`testImplementation`)

| Artifact | Group:Name | Version | Purpose | License |
|---|---|---|---|---|
| JUnit 4 | `junit:junit` | `4.13.2` | JVM unit test runner & assertions | Eclipse Public License 1.0 |
| Robolectric | `org.robolectric:robolectric` | `4.14.1` | Local Android SDK 35 + Native Graphics (`@GraphicsMode.NATIVE`) execution | MIT |
| AndroidX Test Core | `androidx.test:core` | `1.6.1` | `ApplicationProvider` context for `ProjectStorageManager` & credential boundary tests | Apache-2.0 |
| AndroidX Test Ext JUnit | `androidx.test.ext:junit` | `1.2.1` | Android JUnit test annotations | Apache-2.0 |
| Compose UI Test JUnit4 | `androidx.compose.ui:ui-test-junit4` | `1.8.1` *(BOM `2025.05.00`)* | Jetpack Compose UI click-through Robolectric assertions | Apache-2.0 |
| Compose UI Test Manifest *(debug)* | `androidx.compose.ui:ui-test-manifest` | `1.8.1` *(BOM `2025.05.00`)* | Host `ComponentActivity` manifest for Robolectric UI tests | Apache-2.0 |

## 4. Companion Service Runtime (`companion/`)

- Uses **Python 3 Standard Library only** (`http.server`, `json`, `base64`, `hashlib`, `hmac`, `zlib`, `struct`, `threading`, `unittest`) — zero third-party `pip` dependencies required.

## 5. Assets & Typography Provenance

- **Fonts (`app/src/main/res/font/`):** `inter.ttf` (SIL Open Font License 1.1) and `jetbrains_mono.ttf` (SIL Open Font License 1.1), installed locally via Google Fonts CLI (`font-util`).
- **Launcher & Preview Icons (`app/src/main/res/drawable/`, `mipmap-*`):** Custom adaptive icon (`ic_launcher_background.xml`, `ic_launcher_foreground.xml`, generated badge `ic_studio_launcher_1794797719546.jpg`) produced cleanly inside the build environment without third-party copyrighted stock assets.
- **Sample Project (`SampleProjectFactory.kt`):** Deterministic offline project synthesized purely via `TileBrushEngine` and Android `Canvas` primitives.
