plugins {
    id("com.android.library")
    id("maven-publish")
}

// JitPack sets GROUP, VERSION (and JITPACK=true) in the build environment.
group = System.getenv("GROUP")?.takeIf { it.isNotBlank() } ?: "com.github.LukasBasiura.mmslib"
version = System.getenv("VERSION")?.takeIf { it.isNotBlank() } ?: "1.0.0-SNAPSHOT"

android {
    compileSdk = 34
    namespace = "com.klinker.android.send_message"

    defaultConfig {
        minSdk = 26
    }

    lint {
        abortOnError = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    @Suppress("DEPRECATION")
    useLibrary("org.apache.http.legacy")

    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

dependencies {
    implementation("com.klinkerapps:logger:1.0.3")
    implementation("com.squareup.okhttp:okhttp:2.5.0")
    implementation("com.squareup.okhttp:okhttp-urlconnection:2.5.0")
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = project.group.toString()
                artifactId = "library"
                version = project.version.toString()
                from(components["release"])
            }
        }
    }
}
