import com.diffplug.gradle.spotless.SpotlessExtension

plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.aboutLibraries) apply false
    alias(libs.plugins.spotless) apply false
}

// 统一 Kotlin 代码格式；规则见 .editorconfig 与下方 ktlint 配置。格式化为 ./gradlew spotlessApply，检查为 spotlessCheck
val ktlintVersion = "1.8.0"

subprojects {
    pluginManager.withPlugin("com.diffplug.spotless") {
        extensions.configure<SpotlessExtension> {
            kotlin {
                target("**/*.kt")
                targetExclude("**/build/**")
                ktlint(ktlintVersion).editorConfigOverride(
                    mapOf(
                        "ktlint_code_style" to "ktlint_official",
                        "ktlint_function_naming_ignore_when_annotated_with" to "Composable",
                        "ktlint_standard_filename" to "disabled",
                    ),
                )
            }
        }
    }
}
