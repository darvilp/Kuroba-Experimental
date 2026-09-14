import com.android.build.gradle.internal.api.BaseVariantOutputImpl
import com.github.k1rakishou.buildsrc.FreeCompilerArgs
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.devtools.ksp")
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.detekt)
}

enum class KurobaBuildType {
    Stable,
    Beta,
    Dev;

    companion object {
        fun fromRaw(value: Int?): KurobaBuildType? {
            return when (value) {
                0 -> Stable
                1 -> Beta
                2 -> Dev
                else -> Dev
            }
        }
    }
}

val gitHashProvider = providers.exec {
    commandLine("git", "rev-parse", "HEAD")
}.standardOutput.asText.map { it.trim() }

val experimentalRelease = providers.gradleProperty("experimentalRelease")
    .map { it.toBooleanStrict() }
    .getOrElse(false)

if (experimentalRelease) {
    val gitStatus = providers.exec {
        commandLine("git", "status", "--porcelain")
    }.standardOutput.asText.get().trim()
    require(gitStatus.isEmpty()) {
        "Experimental APKs require a clean checkout. Commit all source changes before building."
    }
}

android {
    namespace = "com.github.k1rakishou.chan"
    compileSdk = libs.versions.compileSdk.get().toInt()

    val kurobaBuildType = KurobaBuildType.fromRaw(project.findProperty("buildType")?.toString()?.toInt())
    require(!experimentalRelease || kurobaBuildType == KurobaBuildType.Dev) {
        "Experimental releases must retain Dev runtime behavior. Use -PbuildType=2."
    }
    when (kurobaBuildType) {
        KurobaBuildType.Stable -> println("Using KurobaBuildType.Stable")
        KurobaBuildType.Beta -> println("Using KurobaBuildType.Beta")
        KurobaBuildType.Dev -> println("Using KurobaBuildType.Dev")
        else -> error("Unknown buildType: ${project.findProperty("buildType")}")
    }

    defaultConfig {
        minSdk = libs.versions.minSdk.get().toInt()
        targetSdk = libs.versions.targetSdk.get().toInt()

        applicationId = "com.github.k1rakishou.chan"
        applicationIdSuffix = ""
        buildConfigField("String", "BUILD_TYPE", "\"${kurobaBuildType.name}\"")
        buildConfigField("String", "COMMIT_HASH", "\"${gitHashProvider.get()}\"")
        manifestPlaceholders["appTheme"] = "@style/Chan.DefaultTheme"

        when (kurobaBuildType) {
          KurobaBuildType.Stable -> {
              manifestPlaceholders["appName"] = "KurobaEx"
              manifestPlaceholders["iconLoc"] = "@mipmap/ic_launcher_release"
          }
          KurobaBuildType.Beta -> {
              manifestPlaceholders["appName"] = "KurobaEx-beta"
              manifestPlaceholders["iconLoc"] = "@mipmap/ic_launcher_beta"
          }
          KurobaBuildType.Dev -> {
              applicationIdSuffix = ".personal"
              manifestPlaceholders["appName"] = "KurobaEx Personal"
              manifestPlaceholders["iconLoc"] = "@mipmap/ic_launcher_dev"
          }
        }

        //            M -> Major version
        //            m -> Minor version
        //            p -> patch
        //            MmmPP
        versionCode = 10344
        versionName = "v1.3.44"

        if (experimentalRelease) {
            val experimentalVersion = Properties().apply {
                rootProject.file("experimental-version.properties").inputStream().use { load(it) }
            }
            applicationIdSuffix = ".experimental"
            manifestPlaceholders["appName"] = "KurobaEx Experimental"
            versionCode = experimentalVersion.getProperty("versionCode").toInt()
            versionName = experimentalVersion.getProperty("versionName")
        }

        configurations.configureEach {
            resolutionStrategy {
                force(libs.emoji2)
            }
            exclude(group = "org.jetbrains.kotlin", module = "kotlin-android-extensions-runtime")
        }

        vectorDrawables.useSupportLibrary = true
    }

    // signingConfigs must come before buildTypes
    signingConfigs {
        if (experimentalRelease) {
            fun signingValue(name: String): String = providers.environmentVariable(name).orNull
                ?.takeIf { it.isNotBlank() }
                ?: error("Missing experimental signing environment variable: $name")

            create("experimental") {
                storeFile = file(signingValue("KUROBA_EXPERIMENTAL_KEYSTORE"))
                storePassword = signingValue("KUROBA_EXPERIMENTAL_STORE_PASSWORD")
                keyAlias = signingValue("KUROBA_EXPERIMENTAL_KEY_ALIAS")
                keyPassword = signingValue("KUROBA_EXPERIMENTAL_KEY_PASSWORD")
                enableV1Signing = true
                enableV2Signing = true
            }
        }

        val releasePropsFile = file("release.properties")
        if (releasePropsFile.exists()) {
            val props = Properties().apply {
                load(FileInputStream(releasePropsFile))
            }
            create("release") {
                storeFile = file(props["keystoreFile"] as String)
                storePassword = props["keystorePass"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPass"] as String
                enableV1Signing = true
                enableV2Signing = true
            }
        }

        val debugPropsFile = file("debug.properties")
        if (debugPropsFile.exists()) {
            val props = Properties().apply {
                load(FileInputStream(debugPropsFile))
            }
            getByName("debug") {
                storeFile = file(props["keystoreFile"] as String)
                storePassword = props["keystorePass"] as String
                keyAlias = props["keyAlias"] as String
                keyPassword = props["keyPass"] as String
                enableV1Signing = true
                enableV2Signing = true
            }
        }
    }

    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard.cfg")
            isDebuggable = false
            if (experimentalRelease) {
                signingConfig = signingConfigs.getByName("experimental")
            } else if (signingConfigs.names.contains("release")) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
        debug {
            isMinifyEnabled = false
            isDebuggable = true
            if (signingConfigs.names.contains("debug")) {
                signingConfig = signingConfigs.getByName("debug")
            }
        }
    }

    // APK rename
    applicationVariants.all {
        val variant = this

        variant.outputs
            .map { it as BaseVariantOutputImpl }
            .forEach { output ->
                val apkNameSuffix = if (experimentalRelease) "experimental" else when (kurobaBuildType) {
                  KurobaBuildType.Stable -> ""
                  KurobaBuildType.Beta -> "beta"
                  KurobaBuildType.Dev -> "personal"
                }
                val abi = output.getFilter("ABI") ?: ""

                output.outputFileName = buildString {
                    append("KurobaEx")

                    if (apkNameSuffix.isNotEmpty()) {
                        append("-")
                        append(apkNameSuffix)
                    }

                    if (abi.isNotEmpty()) {
                        append("-")
                        append(abi)
                    }

                    append(".")
                    append("apk")
                }
            }

        // Force Gradle to actually rebuild apk when we build it with a different buildType
        // (when nothing else was changed)
        variant.generateBuildConfigProvider.configure {
            inputs.property("buildType", kurobaBuildType.name)
        }
    }

    compileOptions {
        encoding = "UTF-8"
        sourceCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
        targetCompatibility = JavaVersion.toVersion(libs.versions.jvmTarget.get())
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.fromTarget(libs.versions.jvmTarget.get()))
            freeCompilerArgs.addAll(FreeCompilerArgs.args)
        }
    }

    java {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(libs.versions.jvmTarget.get().toInt()))
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    lint {
        error += setOf(
            "MissingPermission",
            "ProtectedPermissions",
            "InlinedApi",
            "NewApi",
            "HardcodedDebugMode",
            "PackageManagerGetSignatures",
            "UnsafeCryptoAlgorithm",
            "TrustAllX509TrustManager"
        )
        disable += setOf(
            "StopShip",
            "AppLinksAutoVerify",
            "InvalidPackage",
            "UnusedResources",
            "SetJavaScriptEnabled"
        )

        checkAllWarnings = true
        abortOnError = true
        checkReleaseBuilds = true
        checkDependencies = false
        ignoreTestSources = true
        checkGeneratedSources = false
    }
}

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config.setFrom("$projectDir/config/detekt.yml")
    baseline = file("$projectDir/config/detekt-baseline.xml")
}

dependencies {
    implementation(project(":core-logger"))
    implementation(project(":core-settings"))
    implementation(project(":core-themes"))
    implementation(project(":core-spannable"))
    implementation(project(":core-common"))
    implementation(project(":core-model"))
    implementation(project(":core-parser"))

    implementation(libs.appcompat)
    implementation(libs.androidx.preferences.ktx)
    implementation(libs.constraintlayout)
    implementation(libs.slidingpanelayout)
    implementation(libs.swiperefreshlayout)
    implementation(libs.core.ktx)
    implementation(libs.work.runtime.ktx)
    implementation(libs.lifecycle.runtime.ktx)
    implementation(libs.material)
    implementation(libs.androidx.window)
    implementation(libs.compose.ui)
    implementation(libs.compose.material)
    implementation(libs.compose.ui.tooling)
    implementation(libs.compose.animation.graphics)
    implementation(libs.lifecycle.viewmodel.compose)
    implementation(libs.activity.compose)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.ui)
    implementation(libs.okhttp)
    implementation(libs.okhttp.dnsoverhttps)
    implementation(libs.okhttp.logging.interceptor)
    implementation(libs.jsoup)
    implementation(libs.gif.drawable)
    implementation(libs.subsampling.scale.image.view)
    implementation(libs.autolink)
    implementation(libs.gson)
    implementation(libs.kotlin.stdlib)
    implementation(libs.kotlin.reflect)
    implementation(libs.kotlin.collections.immutable)
    implementation(libs.joda.time)
    implementation(libs.kotlin.coroutines.core)
    implementation(libs.kotlin.coroutines.android)
    implementation(libs.coroutines.rx2)
    implementation(libs.coil)
    implementation(libs.coil.compose)
    implementation(libs.voyager.core)
    implementation(libs.voyager.navigator)
    implementation(libs.voyager.transitions)
    implementation(libs.fsaf)

    implementation(libs.room.runtime)
    ksp(libs.room.compiler)

    implementation(libs.dagger)
    ksp(libs.dagger.compiler)

    implementation(libs.moshi.kotlin)
    ksp(libs.moshi.codegen)

    implementation(libs.epoxy)
    ksp(libs.epoxy.processor)

    detektPlugins(libs.detekt.formatting)

    testImplementation(libs.junit)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.mockito.core)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.powermock.module.junit4)
    testImplementation(libs.powermock.api.mockito2)
    testImplementation(libs.kotlin.coroutines.test)
}
