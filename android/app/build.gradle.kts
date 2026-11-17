import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

val sharedSigningProperties = rootProject.file("signing/signing.properties")
val sharedSigning = Properties().takeIf { sharedSigningProperties.isFile }?.apply {
    sharedSigningProperties.inputStream().use(::load)
}
val sharedSigningFile = sharedSigning?.getProperty("storeFile")?.let(rootProject::file)

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.harroyuz.ddrchartviewer"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.harroyuz.ddrchartviewer"
        minSdk = 26
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    signingConfigs {
        if (sharedSigning != null && sharedSigningFile != null) {
            create("sharedIidx") {
                storeFile = sharedSigningFile
                storePassword = sharedSigning.getProperty("storePassword")
                keyAlias = sharedSigning.getProperty("keyAlias")
                keyPassword = sharedSigning.getProperty("keyPassword")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (sharedSigning != null) signingConfig = signingConfigs.getByName("sharedIidx")
        }
        debug {
            if (sharedSigning != null) signingConfig = signingConfigs.getByName("sharedIidx")
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    sourceSets.getByName("test").resources.srcDir("src/main/assets")
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

tasks.register<JavaExec>("auditSources") {
    dependsOn("compileDebugUnitTestKotlin", "processDebugUnitTestJavaRes")
    mainClass.set("com.harroyuz.ddrchartviewer.SourceAudit")
    doFirst {
        classpath = tasks.named<Test>("testDebugUnitTest").get().classpath
        args(providers.gradleProperty("sourceFixtures").get())
    }
}
kotlin { compilerOptions { jvmTarget.set(JvmTarget.JVM_17) } }

dependencies {
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.3")
    implementation("androidx.work:work-runtime-ktx:2.10.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jsoup:jsoup:1.23.2")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation(platform("androidx.compose:compose-bom:2025.08.00"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
