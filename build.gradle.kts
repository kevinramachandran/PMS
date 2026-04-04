plugins {
    id("java")
    id("org.springframework.boot") version "3.2.0"
    id("io.spring.dependency-management") version "1.1.4"
}

group = "org.example"
version = providers.gradleProperty("appVersion").orElse("1.0.0").get()

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

tasks.register<Copy>("copyJar") {
    description = "Copies the executable JAR into the project dist/ folder."
    dependsOn("bootJar")
    from(tasks.named<org.springframework.boot.gradle.tasks.bundling.BootJar>("bootJar").map { it.archiveFile })
    into(distDir)
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