plugins {
    alias(libs.plugins.android.application)
}

// 签名信息从环境变量读取，密码不进仓库。
// 需要打正式包时设置：PLAINPAD_KEYSTORE / PLAINPAD_STORE_PASSWORD / PLAINPAD_KEY_ALIAS / PLAINPAD_KEY_PASSWORD
val releaseKeystore: String? = System.getenv("PLAINPAD_KEYSTORE")
val hasReleaseKeystore: Boolean = releaseKeystore != null && file(releaseKeystore).exists()

android {
    namespace = "com.sunwenqing.plainpad"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        applicationId = "com.sunwenqing.plainpad"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "1.1.3"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    signingConfigs {
        if (hasReleaseKeystore) {
            create("release") {
                storeFile = file(releaseKeystore!!)
                storePassword = System.getenv("PLAINPAD_STORE_PASSWORD")
                keyAlias = System.getenv("PLAINPAD_KEY_ALIAS")
                keyPassword = System.getenv("PLAINPAD_KEY_PASSWORD")
            }
        }
    }
    buildTypes {
        release {
            optimization {
                enable = false
            }
            if (hasReleaseKeystore) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation("androidx.viewpager2:viewpager2:1.1.0")
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
}
