
plugins {
    id("com.android.library")
    id("kotlin-android")
}

android {
    compileSdk = 31
    defaultConfig {
        minSdk = 16
        targetSdk = 31
    }
    buildTypes {
        get("release").consumerProguardFile("proguard-rules.pro")
    }
}

dependencies {
    api("androidx.annotation:annotation:1.3.0")
    api("com.otaliastudios.opengl:egloo:0.6.1")
    testImplementation("org.junit.jupiter:junit-jupiter-api:5.7.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.7.0")
}
