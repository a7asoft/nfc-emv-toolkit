import io.gitlab.arturbosch.detekt.Detekt
import org.jlleitschuh.gradle.ktlint.KtlintExtension

plugins {
    // this is necessary to avoid the plugins to be loaded multiple times
    // in each subproject's classloader
    alias(libs.plugins.androidApplication) apply false
    alias(libs.plugins.androidLibrary) apply false
    alias(libs.plugins.composeMultiplatform) apply false
    alias(libs.plugins.composeCompiler) apply false
    alias(libs.plugins.kotlinMultiplatform) apply false
    alias(libs.plugins.kotlinAndroid) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
}

// Lint plugins apply only to Kotlin/Android modules; future docs/static modules opt out.
subprojects {
    val kotlinPluginIds = listOf(
        "org.jetbrains.kotlin.multiplatform",
        "org.jetbrains.kotlin.android",
        "com.android.application",
        "com.android.library",
    )
    kotlinPluginIds.forEach { id ->
        plugins.withId(id) {
            apply(plugin = rootProject.libs.plugins.detekt.get().pluginId)
            apply(plugin = rootProject.libs.plugins.ktlint.get().pluginId)

            configure<KtlintExtension> {
                version.set("1.4.0")
                android.set(false)
                ignoreFailures.set(false)
                filter {
                    exclude { it.file.path.contains("build/") }
                    exclude { it.file.path.contains("generated/") }
                }
            }

            tasks.withType<Detekt>().configureEach {
                config.setFrom(rootProject.files("detekt.yml"))
                baseline.set(rootProject.file("detekt-baseline.xml"))
                parallel = true
                buildUponDefaultConfig = true
                autoCorrect = false
                // KMP / Android subprojects don't auto-populate the default `detekt` task's source set.
                // Point it at the conventional kotlin source roots so the task actually has work to do.
                setSource(
                    files(
                        "src/commonMain/kotlin",
                        "src/commonTest/kotlin",
                        "src/androidMain/kotlin",
                        "src/androidUnitTest/kotlin",
                        "src/iosMain/kotlin",
                        "src/iosTest/kotlin",
                        "src/main/kotlin",
                        "src/test/kotlin",
                    ),
                )
                include("**/*.kt", "**/*.kts")
                exclude("**/build/**", "**/generated/**", "**/resources/**")
                reports {
                    html.required.set(true)
                    xml.required.set(true)
                    txt.required.set(false)
                    sarif.required.set(false)
                    md.required.set(false)
                }
            }

            tasks.withType<io.gitlab.arturbosch.detekt.DetektCreateBaselineTask>().configureEach {
                config.setFrom(rootProject.files("detekt.yml"))
                baseline.set(rootProject.file("detekt-baseline.xml"))
                parallel = true
                buildUponDefaultConfig = true
                setSource(
                    files(
                        "src/commonMain/kotlin",
                        "src/commonTest/kotlin",
                        "src/androidMain/kotlin",
                        "src/androidUnitTest/kotlin",
                        "src/iosMain/kotlin",
                        "src/iosTest/kotlin",
                        "src/main/kotlin",
                        "src/test/kotlin",
                    ),
                )
                include("**/*.kt", "**/*.kts")
                exclude("**/build/**", "**/generated/**", "**/resources/**")
            }
        }
    }
}
