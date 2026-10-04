import java.util.Properties

plugins { id("com.android.application") }

val releaseVersion = Properties().apply {
    rootProject.file("version.properties").inputStream().use { load(it) }
}
val signingVariables = listOf("QINGLAN_STORE_FILE", "QINGLAN_STORE_PASSWORD", "QINGLAN_KEY_ALIAS", "QINGLAN_KEY_PASSWORD")
val signingValues = signingVariables.associateWith { providers.environmentVariable(it).orNull }
val hasReleaseSigning = signingValues.values.all { !it.isNullOrBlank() }
require(signingValues.values.all { it.isNullOrBlank() } || hasReleaseSigning) {
    "Release signing requires all four QINGLAN_* signing environment variables."
}
require(providers.environmentVariable("QINGLAN_REQUIRE_SIGNING").orNull != "true" || hasReleaseSigning) {
    "Release signing is required for publication."
}

android {
    namespace = "dev.qinglan.browser"
    compileSdk = 36
    defaultConfig {
        applicationId = "dev.qinglan.browser"
        minSdk = 26
        targetSdk = 36
        versionCode = releaseVersion.getProperty("VERSION_CODE").toInt()
        versionName = releaseVersion.getProperty("VERSION_NAME")
        testInstrumentationRunner = "dev.qinglan.browser.CookieInstrumentation"
    }
    buildFeatures { buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(signingValues.getValue("QINGLAN_STORE_FILE")!!)
                storePassword = signingValues.getValue("QINGLAN_STORE_PASSWORD")
                keyAlias = signingValues.getValue("QINGLAN_KEY_ALIAS")
                keyPassword = signingValues.getValue("QINGLAN_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("release")
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
}
kotlin { compilerOptions { jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17 } }
dependencies {
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.webkit:webkit:1.15.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
