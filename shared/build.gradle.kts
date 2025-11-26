plugins {
    kotlin("multiplatform")
    kotlin("plugin.serialization")
    id("com.android.library")
    id("app.cash.sqldelight")
}

kotlin {
    androidTarget {
        compilations.all {
            kotlinOptions {
                jvmTarget = "17"
            }
        }
    }
    
    // Future iOS support
    // iosX64()
    // iosArm64()
    // iosSimulatorArm64()
    
    sourceSets {
        val commonMain by getting {
            dependencies {
                // Kotlin Coroutines
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.3")
                
                // Ktor Client
                implementation("io.ktor:ktor-client-core:2.3.7")
                implementation("io.ktor:ktor-client-content-negotiation:2.3.7")
                implementation("io.ktor:ktor-serialization-kotlinx-json:2.3.7")
                
                // Kotlinx Serialization
                implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
                
                // Kotlinx DateTime
                implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
                
                // SQLDelight
                implementation("app.cash.sqldelight:runtime:2.0.1")
                implementation("app.cash.sqldelight:coroutines-extensions:2.0.1")
            }
        }
        
        val commonTest by getting {
            dependencies {
                implementation(kotlin("test"))
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                
                // Kotest for property-based testing
                implementation("io.kotest:kotest-framework-engine:5.8.0")
                implementation("io.kotest:kotest-assertions-core:5.8.0")
                implementation("io.kotest:kotest-property:5.8.0")
                
                // SQLDelight JDBC driver for in-memory testing
                implementation("app.cash.sqldelight:sqlite-driver:2.0.1")
            }
        }
        
        val androidMain by getting {
            dependencies {
                // Android-specific dependencies
                implementation("androidx.core:core-ktx:1.12.0")
                
                // Ktor Android Engine
                implementation("io.ktor:ktor-client-android:2.3.7")
                
                // SQLDelight Android Driver
                implementation("app.cash.sqldelight:android-driver:2.0.1")
                
                // JSch for SSH (using maintained fork with RSA signature fixes)
                implementation("com.github.mwiede:jsch:0.2.16")
                
                // Android Security
                implementation("androidx.security:security-crypto:1.1.0-alpha06")
            }
        }
        
        val androidUnitTest by getting {
            dependencies {
                implementation("junit:junit:4.13.2")
                implementation("io.mockk:mockk:1.13.8")
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.7.3")
                
                // Kotest for property-based testing
                implementation("io.kotest:kotest-framework-engine:5.8.0")
                implementation("io.kotest:kotest-assertions-core:5.8.0")
                implementation("io.kotest:kotest-property:5.8.0")
                
                // Robolectric for Android unit testing
                implementation("org.robolectric:robolectric:4.11.1")
            }
        }
    }
}

android {
    namespace = "com.sshtunnel.shared"
    compileSdk = 34
    
    defaultConfig {
        minSdk = 26
    }
    
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

sqldelight {
    databases {
        create("SSHTunnelDatabase") {
            packageName.set("com.sshtunnel.db")
            srcDirs.setFrom("src/commonMain/sqldelight")
        }
    }
}
