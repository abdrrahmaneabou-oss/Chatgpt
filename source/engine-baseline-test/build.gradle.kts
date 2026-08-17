plugins {
    kotlin("jvm") version "2.0.21"
}

kotlin {
    jvmToolchain(17)
}

sourceSets {
    main {
        kotlin.srcDir("../app/src/main/java/com/pixeltrigger/app/engine")
    }
}

dependencies {
    testImplementation(kotlin("test"))
}

tasks.test {
    useJUnitPlatform()
}
