plugins { `java-library` }
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
dependencies { testImplementation("junit:junit:4.13.2") }
if (listOf("linux", "mac").any { System.getProperty("os.name").lowercase().contains(it) }) {
    val nativeBuild = tasks.register<Exec>("buildHostChips") {
        inputs.dir(rootProject.file("synth-android/src/main/cpp"))
        inputs.file(rootProject.file("scripts/build-chips.sh"))
        outputs.dir(layout.buildDirectory.dir("host-native"))
        commandLine("sh", rootProject.file("scripts/build-chips.sh"), layout.buildDirectory.dir("host-native").get().asFile, System.getProperty("java.home"))
    }
    tasks.test { dependsOn(nativeBuild); jvmArgs("-Djava.library.path=${layout.buildDirectory.dir("host-native").get().asFile}") }
}
