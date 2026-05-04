plugins {
    id("org.springframework.boot")
}

dependencies {
    implementation(project(":common"))
    implementation(project(":project"))
    implementation(project(":input"))
    implementation(project(":model"))
    implementation(project(":parser"))
    implementation(project(":dependency"))
    implementation(project(":risk"))
    implementation(project(":convert"))
    implementation(project(":report"))
    implementation(project(":review"))
    implementation(project(":export"))
    implementation(project(":ai"))

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.flywaydb:flyway-core")
    runtimeOnly("org.postgresql:postgresql")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
}
