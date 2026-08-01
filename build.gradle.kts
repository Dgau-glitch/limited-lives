import xyz.srnyx.gradlegalaxy.data.config.DependencyConfig
import xyz.srnyx.gradlegalaxy.data.config.JavaSetupConfig
import xyz.srnyx.gradlegalaxy.enums.Repository
import xyz.srnyx.gradlegalaxy.enums.repository
import xyz.srnyx.gradlegalaxy.utility.setupAnnoyingAPI


plugins {
    java
    id("xyz.srnyx.gradle-galaxy") version "2.1.0"
    id("com.gradleup.shadow") version "8.3.9"
}

setupAnnoyingAPI(
    javaSetupConfig = JavaSetupConfig(
        "xyz.srnyx",
        "4.2.2",
        "Each player has a limited number of lives. If you die, you are punished"),
    annoyingAPIConfig = DependencyConfig("5.2.1"))

repository(Repository.PLACEHOLDER_API, Repository.ENGINE_HUB)
repositories {
    maven("https://repo.papermc.io/repository/maven-public/")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

configurations.configureEach {
    // Folia API provides the Bukkit capability. Old transitive Bukkit artifacts from
    // AnnoyingAPI/WorldGuard must not compete with the selected server API.
    exclude(group = "org.bukkit", module = "bukkit")
}

dependencies {
    compileOnly("dev.folia:folia-api:1.21.11-R0.1-SNAPSHOT")
    compileOnly("me.clip:placeholderapi:2.12.2")
    compileOnly("com.sk89q.worldguard:worldguard-bukkit:7.0.0")

    testImplementation(platform("org.junit:junit-bom:5.14.3"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
}
