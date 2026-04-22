plugins {
    id("java")
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "org.example"
version = providers.gradleProperty("appVersion").orElse("2.0.0").get()

val appJarName = providers.gradleProperty("appJarName").orElse("brewery-pms-be")

if (System.getProperty("os.name").startsWith("Windows", ignoreCase = true)) {
    val localBuildBase = System.getenv("LOCALAPPDATA") ?: System.getProperty("java.io.tmpdir")
    layout.buildDirectory.set(file("$localBuildBase/${rootProject.name}/gradle-build"))
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-thymeleaf")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-mail")
    implementation("org.springframework.security:spring-security-crypto")
    implementation("org.apache.commons:commons-csv:1.10.0")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.3.0")
    runtimeOnly("com.mysql:mysql-connector-j")
    compileOnly("org.projectlombok:lombok:1.18.32")
    annotationProcessor("org.projectlombok:lombok:1.18.32")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
}

tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar") {
    archiveBaseName.set(appJarName)
    archiveVersion.set(project.version.toString())
    archiveClassifier.set("")
    launchScript()
}

tasks.named<Jar>("jar") {
    enabled = false
}

val distDir = rootProject.layout.projectDirectory.dir("dist")
val windowsServiceDir = rootProject.layout.projectDirectory.dir("dist/windows-service")
val windowsReleaseDir = rootProject.layout.projectDirectory.dir("dist/releases")

tasks.register<Copy>("copyJar") {
    description = "Copies the executable JAR into the project dist/ folder."
    dependsOn("bootJar")
    from(tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").map { it.archiveFile })
    into(distDir)
}

tasks.register<Copy>("bundleWindowsService") {
    description = "Builds a Windows service deployment bundle with the application JAR and WinSW assets."
    dependsOn("bootJar")
    from(tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").map { it.archiveFile }) {
        into("app")
        rename { "app.jar" }
    }
    from("deployment/windows") {
        include("*.ps1", "*.bat", "*.xml")
        into("service")
    }
    from("deployment/windows/brewery-pms.env.example") {
        into("config")
    }
    into(windowsServiceDir)
}

tasks.register<Zip>("zipWindowsService") {
    description = "Creates a versioned ZIP archive for the Windows service deployment bundle."
    dependsOn("bundleWindowsService", "copyJar")
    from(windowsServiceDir)
    archiveBaseName.set(appJarName.map { "$it-windows-service" })
    archiveVersion.set(project.version.toString())
    destinationDirectory.set(windowsReleaseDir)
}

tasks.register("packageWindowsService") {
    description = "Runs a clean build and produces the versioned Windows service ZIP artifact."
    dependsOn("clean", "build", "zipWindowsService")
}

tasks.named("bootJar") {
    finalizedBy("copyJar")
}

tasks.named("build") {
    finalizedBy("copyJar")
}

tasks.test {
    useJUnitPlatform()
}
