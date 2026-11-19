plugins { id("com.android.library") version "8.9.2" }
android {
    namespace = "io.github.gretchycat.infinisynth.android"
    compileSdk = 36
    defaultConfig { minSdk = 26; consumerProguardFiles("consumer-rules.pro") }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    if (System.getenv("TERMUX_VERSION") == null) {
        ndkVersion = "27.2.12479018"
        externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    } else sourceSets.getByName("main").jniLibs.srcDir(layout.buildDirectory.dir("chip-jni"))
    sourceSets.getByName("main").assets.srcDir(layout.buildDirectory.dir("notices"))
}
dependencies { api(project(":synth-core")) }
val copyNotices = tasks.register<Copy>("copyNotices") {
    from("src/main/cpp/third_party/Nuked-OPL3-LICENSE", "src/main/cpp/third_party/SAA1099-LICENSE", "src/main/cpp/third_party/m6581.h", rootProject.file("docs/THIRD_PARTY.md"))
    into(layout.buildDirectory.dir("notices/third-party/infinisynth"))
}
tasks.named("preBuild").configure { dependsOn(copyNotices) }
if (System.getenv("TERMUX_VERSION") != null) {
    val nativeBuild = tasks.register<Exec>("buildChips") {
        inputs.dir("src/main/cpp"); inputs.file(rootProject.file("scripts/build-chips.sh"))
        outputs.dir(layout.buildDirectory.dir("chip-jni/arm64-v8a"))
        commandLine("sh",rootProject.file("scripts/build-chips.sh"),layout.buildDirectory.dir("chip-jni/arm64-v8a").get().asFile,System.getProperty("java.home"))
    }
    tasks.named("preBuild").configure { dependsOn(nativeBuild) }
}
