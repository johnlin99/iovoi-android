// 解決某些沒有指定 buildscript 解析倉庫而找不到依賴的問題
buildscript {
    repositories {
        google()
        mavenCentral()
    }
    dependencies {
        classpath("com.android.tools.build:gradle:8.2.0")
        classpath("org.jetbrains.kotlin:kotlin-gradle-plugin:1.9.20")
    }
}

plugins {
    id("com.android.application") version "8.2.0" apply false
    id("org.jetbrains.kotlin.android") version "1.9.20" apply false
}

// 補上全域專案的 repository
allprojects {
    repositories {
        google()
        mavenCentral()
    }
}
