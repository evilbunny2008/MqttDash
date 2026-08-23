plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.compose")
}

android {
    namespace = "com.odiousapps.z2mdash"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.odiousapps.z2mdash"
        minSdk = 26
        targetSdk = 37
        versionCode = 1
        versionName = "0.0.1"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
            excludes += "/META-INF/INDEX.LIST"
            excludes += "/META-INF/*.SF"
            excludes += "/META-INF/*.DSA"
            excludes += "/META-INF/*.RSA"
            excludes += "META-INF/io.netty.versions.properties"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.19.0")
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")

    // XML theme resources (Theme.Material3.*) used by AndroidManifest, separate from
    // the Compose Material3 Kotlin artefact below.
    implementation("com.google.android.material:material:1.14.0")

    val composeBom = platform("androidx.compose:compose-bom:2026.08.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.animation:animation")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.navigation:navigation-compose:2.9.8")

    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")

    // MQTT client. Actively maintained, MQTT 3.1.1 + 5.0, robust TCP/SSL/WS/WSS
    // transports with real automatic-reconnect handling - this is what replaces
    // Paho's flaky websocket keepalive.
    implementation("com.hivemq:hivemq-mqtt-client:1.4.0")
    implementation("io.netty:netty-common:4.1.133.Final")
    implementation("io.netty:netty-handler:4.1.133.Final")
    implementation("io.netty:netty-codec:4.1.133.Final")
    implementation("io.netty:netty-codec-http:4.1.133.Final")
    implementation("io.netty:netty-transport:4.1.133.Final")
    implementation("io.netty:netty-buffer:4.1.133.Final")
    implementation("io.netty:netty-resolver:4.1.133.Final")
}
