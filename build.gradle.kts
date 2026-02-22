buildscript {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
    dependencies {
        // 從 8.2.0 降級到 8.1.1，這個版本最為穩定
        classpath("com.android.tools.build:gradle:8.1.1")
        // 從 1.9.20 降級到 1.9.0，避免太新的 API 造成編譯器 class not found
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.0")
    }
}
plugins {
    id("com.android.application") version "8.1.1" apply false
    id("org.jetbrains.kotlin.android") version "1.9.0" apply false
}

allprojects {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
