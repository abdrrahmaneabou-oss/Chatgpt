plugins {
    kotlin("jvm")
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java/com/pixeltrigger/app/engine")
        kotlin.include("DetectionEngine.kt")
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
