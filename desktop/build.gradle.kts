import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    id("org.jetbrains.kotlin.jvm")
    id("org.jetbrains.compose")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

kotlin {
    jvmToolchain(17)
}

// Shared with the Android app: models, AI clients, agent tools, GitHub, Claude Code parser, cookie shapes, markdown.
sourceSets {
    main {
        kotlin {
            srcDir("../app/src/main/java")
            include(
                "com/ultracode/desktop/**",
                "com/ultracode/app/data/Models.kt",
                "com/ultracode/app/data/JsonConfig.kt",
                "com/ultracode/app/ai/**",
                "com/ultracode/app/agent/Tools.kt",
                "com/ultracode/app/agent/WorkspaceCore.kt",
                "com/ultracode/app/agent/WebTools.kt",
                "com/ultracode/app/github/**",
                "com/ultracode/app/ui/Cookie.kt",
                "com/ultracode/app/ui/Markdown.kt",
            )
        }
    }
}

dependencies {
    implementation(compose.desktop.currentOs)
    implementation(compose.material3)
    implementation(compose.materialIconsExtended)
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-swing:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    // Dark / colored window title bar on Windows (DWM API).
    implementation("net.java.dev.jna:jna-platform:5.15.0")
    // Modern native file dialogs (Windows Explorer dialog, GTK on Linux).
    val lwjgl = "3.3.4"
    implementation("org.lwjgl:lwjgl:$lwjgl")
    implementation("org.lwjgl:lwjgl-nfd:$lwjgl")
    for (n in listOf("natives-windows", "natives-linux", "natives-macos", "natives-macos-arm64")) {
        runtimeOnly("org.lwjgl:lwjgl:$lwjgl:$n")
        runtimeOnly("org.lwjgl:lwjgl-nfd:$lwjgl:$n")
    }
}

val appVersion = "1.0." + (System.getenv("GITHUB_RUN_NUMBER") ?: "0")

compose.desktop {
    application {
        mainClass = "com.ultracode.desktop.MainKt"
        jvmArgs += listOf("-Dfile.encoding=UTF-8")
        nativeDistributions {
            // Windows .exe installer is built with Inno Setup (desktop/installer/UltraCode.iss).
            targetFormats(TargetFormat.Msi, TargetFormat.Dmg, TargetFormat.Deb)
            packageName = "UltraCode"
            packageVersion = appVersion
            description = "UltraCode - AI coding agent"
            vendor = "UltraCode"
            modules("java.naming", "jdk.crypto.ec", "java.net.http", "jdk.unsupported")
            windows {
                menuGroup = "UltraCode"
                shortcut = true
                dirChooser = true
                perUserInstall = true
                upgradeUuid = "5b0c6a1e-2f7d-4c55-9b7e-8f3c1a9d2e41"
                iconFile.set(project.file("icons/icon.ico"))
            }
            macOS {
                bundleID = "com.ultracode.desktop"
                iconFile.set(project.file("icons/icon.icns"))
            }
            linux {
                shortcut = true
                iconFile.set(project.file("icons/icon.png"))
            }
        }
    }
}
