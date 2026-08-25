plugins {
    java
    id("com.gradleup.shadow") version "9.6.1"
    id("xyz.jpenilla.run-paper") version "3.1.0"
}

group = "com.google.gmail.philbgarner"
version = "0.1.0-SNAPSHOT"
description = "Oathbound"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(25)
    }
}

repositories {
    mavenCentral()
    maven("https://repo.papermc.io/repository/maven-public/")
    maven("https://maven.enginehub.org/repo/")
}

dependencies {
    compileOnly("io.papermc.paper:paper-api:26.2.build.112-stable")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.13") {
        exclude(group = "com.sk89q.worldguard.worldguard-libs")
        exclude(group = "com.sk89q.worldedit.worldedit-libs")
        exclude(group = "it.unimi.dsi", module = "fastutil")
        exclude(group = "com.google.guava", module = "guava")
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation("org.xerial:sqlite-jdbc:3.53.2.1")
    implementation("com.google.code.gson:gson:2.14.0")

    testImplementation(platform("org.junit:junit-bom:6.1.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks {
    compileJava {
        options.encoding = "UTF-8"
        options.release.set(25)
    }
    test {
        useJUnitPlatform()
    }
    shadowJar {
        archiveClassifier.set("")
        relocate("org.sqlite", "com.google.gmail.philbgarner.oathbound.libs.sqlite")
        relocate("com.google.gson", "com.google.gmail.philbgarner.oathbound.libs.gson")
    }
    build {
        dependsOn(shadowJar)
    }
    runServer {
        minecraftVersion("26.2")
    }
    processResources {
        filteringCharset = "UTF-8"
        val props = mapOf("version" to project.version)
        inputs.properties(props)
        filesMatching("plugin.yml") {
            expand(props)
        }
    }
}
