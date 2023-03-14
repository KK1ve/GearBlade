plugins {
    val kotlinVersion = "1.7.10"
    kotlin("jvm") version kotlinVersion
    kotlin("plugin.serialization") version kotlinVersion
    id("net.mamoe.mirai-console") version "2.14.0"
}

group = "net.kkive.gearblade"
version = "0.3.0"

repositories {
    maven("https://maven.aliyun.com/repository/public")
    maven("https://jitpack.io")
    mavenCentral()
}

dependencies {
    implementation("com.alibaba:fastjson:2.0.23")
    implementation("com.github.tekgator:JAVA-QueryMinecraftServer:1.2")
    implementation("cn.hutool:hutool-all:5.8.15")
}


tasks.jar.configure {
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest.attributes["Main-Class"] = "net.kkive.gearblade"
    from(configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) })
}
