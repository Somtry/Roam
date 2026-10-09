// Roam 检测引擎 — 纯 Kotlin JVM 模块,不依赖 android.*
// 与 Python 原型 engine/scan.py 同构;未来可平移 KMP/iOS
plugins {
    alias(libs.plugins.kotlin.jvm)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

dependencies {
    testImplementation(kotlin("test"))
}
