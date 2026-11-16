package com.example

import android.app.Application
import android.graphics.PointF
import androidx.test.core.app.ApplicationProvider
import com.example.model.StudioTool
import com.example.services.ProviderErrorCode
import com.example.services.ProviderJobRequest
import com.example.services.ProviderMode
import com.example.services.ProviderRouter
import com.example.viewmodel.StudioViewModel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.util.zip.ZipFile

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class CredentialBoundaryAndBuildArtifactTest {

    private fun resolveRepoRoot(): File {
        val cwd = File(System.getProperty("user.dir") ?: ".")
        return when {
            File(cwd, "settings.gradle.kts").exists() -> cwd
            cwd.parentFile != null && File(cwd.parentFile, "settings.gradle.kts").exists() -> cwd.parentFile!!
            else -> cwd
        }
    }

    @Test
    fun `BuildConfig and ProviderMode contain zero OpenAI credentials or client remote endpoints`() {
        val fields = BuildConfig::class.java.declaredFields
        for (f in fields) {
            val upperName = f.name.uppercase()
            assertFalse("Forbidden credential field in BuildConfig: ${f.name}", "OPENAI" in upperName)
            assertFalse("Forbidden API key field in BuildConfig: ${f.name}", "API_KEY" in upperName)
            assertFalse("Forbidden secret field in BuildConfig: ${f.name}", "SECRET" in upperName)
            if (f.type == String::class.java) {
                f.isAccessible = true
                val v = (f.get(null) as? String).orEmpty()
                assertFalse("Forbidden OpenAI key prefix in BuildConfig.${f.name}", v.contains("sk-", ignoreCase = true))
                assertFalse("Forbidden OpenAI placeholder in BuildConfig.${f.name}", v.contains("OPENAI_API_KEY"))
            }
        }

        val modeNames = ProviderMode.entries.map { it.name }
        assertFalse("Client ProviderMode must never contain direct OpenAI mode", modeNames.any { "OPENAI" in it })
        assertEquals(listOf("OFFLINE_LOCAL_ONLY", "OFFLINE_MOCK_PROVIDER", "LOOPBACK_COMPANION"), modeNames)
    }

    @Test
    fun `Android source resources Gradle config and generated BuildConfig exclude OpenAI keys and secrets plugin`() {
        val root = resolveRepoRoot()
        assertTrue("Expected settings.gradle.kts in repo root ${root.absolutePath}", File(root, "settings.gradle.kts").exists())

        // 1. Ensure root .env and .env.example do not exist to prompt or leak into APK
        assertFalse("Root .env must not exist", File(root, ".env").exists())
        assertFalse("Root .env.example must be removed so AI Studio never prompts for OPENAI_API_KEY", File(root, ".env.example").exists())

        // 2. Ensure secrets-gradle-plugin is completely removed from Gradle configs
        val gradleFiles = listOf(
            File(root, "build.gradle.kts"),
            File(root, "app/build.gradle.kts"),
            File(root, "gradle/libs.versions.toml")
        )
        for (gf in gradleFiles) {
            val content = gf.readText(Charsets.UTF_8)
            assertFalse("${gf.name} must not apply secrets-gradle-plugin", content.contains("secrets-gradle-plugin"))
            assertFalse("${gf.name} must not mention OPENAI_API_KEY", content.contains("OPENAI_API_KEY"))
        }

        // 3. Scan all Android app/src/main source, manifest, and resources
        val mainDir = File(root, "app/src/main")
        val forbiddenClientTokens = listOf("OPENAI_API_KEY", "api.openai.com", "sk-proj-", "Bearer \${BuildConfig")
        mainDir.walkTopDown().filter { it.isFile }.forEach { file ->
            if (file.extension in setOf("kt", "java", "xml", "json", "txt", "properties")) {
                val txt = file.readText(Charsets.UTF_8)
                for (tok in forbiddenClientTokens) {
                    assertFalse("Forbidden client credential token '$tok' found in ${file.relativeTo(root)}", txt.contains(tok))
                }
            }
        }

        // 4. Scan generated BuildConfig directory if compiled
        val genDir = File(root, "app/build/generated/source/buildConfig")
        if (genDir.exists()) {
            genDir.walkTopDown().filter { it.isFile && it.extension == "java" }.forEach { genFile ->
                val txt = genFile.readText(Charsets.UTF_8)
                assertFalse("Generated ${genFile.name} must not contain OPENAI_API_KEY", txt.contains("OPENAI_API_KEY"))
            }
        }

        // 5. Inspect built APK artifact if already assembled on disk
        val debugApk = File(root, "app/build/outputs/apk/debug/app-debug.apk")
        if (debugApk.exists()) {
            ZipFile(debugApk).use { zf ->
                val entries = zf.entries().asSequence().toList()
                assertFalse("APK must never bundle .env", entries.any { it.name.endsWith(".env") || it.name.endsWith(".env.example") })
                entries.filter { it.name.endsWith(".dex") || it.name.startsWith("assets/") || it.name == "resources.arsc" }
                    .forEach { entry ->
                        val rawText = zf.getInputStream(entry).use { it.readBytes() }.toString(Charsets.ISO_8859_1)
                        assertFalse("APK ${entry.name} must not contain api.openai.com", rawText.contains("api.openai.com"))
                        assertFalse("APK ${entry.name} must not contain OPENAI_API_KEY", rawText.contains("OPENAI_API_KEY"))
                    }
            }
        }
    }

    @Test
    fun `Offline startup painting undo and redacted diagnostics operate with zero network or API keys`() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val vm = StudioViewModel(app)

        assertEquals(ProviderMode.OFFLINE_LOCAL_ONLY, vm.uiState.value.providerMode)
        val diag = vm.buildRedactedDiagnosticsReport()
        assertTrue(diag.contains("Client Secrets: NONE"))
        assertFalse(diag.contains("sk-"))

        // Offline job dispatch stays strictly blocked by default
        val router = ProviderRouter()
        val blocked = router.submitCandidateJob(
            ProviderJobRequest(
                documentId = "doc_offline",
                baseRevision = 1L,
                branchLabel = "Salt",
                directionSuffix = "warm grain",
                prompt = "study",
                cropPngBase64 = "AAAA"
            ),
            currentDocumentRevision = 1L
        )
        assertEquals(ProviderErrorCode.OFFLINE_MODE_ACTIVE, blocked.errorCode)

        // Painting + undo remain 100% functional offline
        vm.selectTool(StudioTool.PENCIL)
        vm.beginPointerAction(PointF(0f, 0f), 0.7f)
        vm.movePointerAction(PointF(24f, 16f), 0.8f)
        vm.endPointerAction()
        assertTrue(vm.uiState.value.canUndo)
    }
}
