plugins {
    kotlin("jvm") version "1.8.21"
    id("maven-publish")
}

group = "com.github.keluaa"
version = "1.1.0"

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.jetbrains.kotlin:kotlin-reflect:1.8.21")
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("net.swiftzer.semver:semver:1.3.0")
    testImplementation(kotlin("test"))
    testImplementation("org.junit.platform:junit-platform-suite-engine:1.9.3")
}

tasks.test {
    useJUnitPlatform()
}

kotlin {
    jvmToolchain(11)
}

java {
    withSourcesJar()
    withJavadocJar()
}

publishing {
    publications {
        create<MavenPublication>("Maven") {
            from(components["java"])
        }
    }
}
