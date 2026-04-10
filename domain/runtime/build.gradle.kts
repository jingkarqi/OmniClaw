plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.sora.omniclaw.domain.runtime"

    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 28
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(project(":bridge:api"))
    implementation(project(":core:common"))
    implementation(project(":core:model"))
    implementation(project(":core:storage"))
    implementation(project(":runtime:api"))
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(project(":testing:fake"))
    testImplementation(libs.junit)
}
