plugins {
    java
    id("org.springframework.boot") version "3.5.16"
    id("io.spring.dependency-management") version "1.1.7"
}

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

dependencies {
    implementation(project(":engine"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-jdbc")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    annotationProcessor("org.springframework.boot:spring-boot-configuration-processor")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.test {
    useJUnitPlatform()
    // Les tests tournent sur un vrai PostgreSQL (voir src/test/resources/application-test.yml).
    systemProperty("spring.profiles.active", "test")
    // Fixtures de parité du moteur (CSV + JSON produits par la référence Python).
    systemProperty("srliq.parity.dir", project(":engine").file("src/test/resources/parity").absolutePath)
    inputs.dir(project(":engine").file("src/test/resources/parity"))
    testLogging {
        events("failed")
        exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
    }
}

// ---------------------------------------------------------------------------
// Interface web embarquée (facultatif) : ./gradlew :app:bootJar -PwithWeb
// construit le front (web/, npm ci + npm run build) et copie web/dist dans
// classpath:/static/ (servi à la racine par com.srliq.app.web.SpaWebConfig).
// Sans -PwithWeb, le build Java ne dépend ni de Node ni de npm.
// ---------------------------------------------------------------------------
if (providers.gradleProperty("withWeb").isPresent) {
    val webDir = rootProject.layout.projectDirectory.dir("web")
    val npm = if (System.getProperty("os.name").lowercase().contains("windows")) "npm.cmd" else "npm"

    val webInstall by tasks.registering(Exec::class) {
        group = "web"
        description = "Installe les dépendances du front (npm ci)."
        workingDir = webDir.asFile
        commandLine(npm, "ci", "--no-audit", "--no-fund")
        inputs.files(webDir.file("package.json"), webDir.file("package-lock.json"), webDir.file(".npmrc"))
        outputs.dir(webDir.dir("node_modules"))
    }

    val webBuild by tasks.registering(Exec::class) {
        group = "web"
        description = "Construit le front (npm run build -> web/dist)."
        dependsOn(webInstall)
        workingDir = webDir.asFile
        commandLine(npm, "run", "build")
        inputs.dir(webDir.dir("src"))
        inputs.dir(webDir.dir("public"))
        inputs.files(
            webDir.file("index.html"), webDir.file("package.json"), webDir.file("package-lock.json"),
            webDir.file("tsconfig.json"), webDir.file("vite.config.ts"),
        )
        outputs.dir(webDir.dir("dist"))
    }

    tasks.processResources {
        dependsOn(webBuild)
        from(webDir.dir("dist")) {
            into("static")
        }
    }
}
