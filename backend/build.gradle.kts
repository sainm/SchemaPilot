plugins {
    java
    id("org.springframework.boot") version "4.0.6" apply false
}

group = "org.sainm.schemapilot"
version = "0.1.0-SNAPSHOT"

val springBootVersion = "4.0.6"

subprojects {
    group = rootProject.group
    version = rootProject.version

    apply(plugin = "java-library")

    extensions.configure<JavaPluginExtension> {
        toolchain {
            languageVersion.set(JavaLanguageVersion.of(25))
        }
    }

    dependencies {
        "implementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "testImplementation"(platform("org.springframework.boot:spring-boot-dependencies:$springBootVersion"))
        "testImplementation"("org.junit.jupiter:junit-jupiter")
        "testImplementation"("org.assertj:assertj-core")
        "testRuntimeOnly"("org.junit.platform:junit-platform-launcher")
    }

    tasks.withType<Test>().configureEach {
        useJUnitPlatform()
    }
}
