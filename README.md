<div align="center">

<img src="assets/banner.png" alt="Pulse Banner" width="100%">

# Pulse
### The Heartbeat of High-Performance Networking

**Pulse** is a high-performance Minecraft server implementation designed to redefine network efficiency. By replacing standard packet handling with sophisticated smart batching, Pulse reduces system call overhead and lowers network throughput, providing a smoother experience for large-scale player counts.

[![Website](https://img.shields.io/badge/Website-pulsemc.dev-333333?style=for-the-badge&logo=google-chrome&logoColor=007bff)](https://pulsemc.dev)
[![Discord](https://img.shields.io/discord/1458504248215212145.svg?label=&logo=discord&logoColor=ffffff&color=403e3e&labelColor=5865F2&style=for-the-badge)](https://dsc.gg/Pulse-MC)
[![Documentation](https://img.shields.io/badge/Java-Docs-333333?style=for-the-badge&logo=openjdk&logoColor=white&labelColor=ED8B00)](https://jd.pulsemc.dev)

</div>

## Project Overview

Pulse is a vertically integrated networking software that replaces standard protocol handling with a smart batching system. By grouping packets into compressed "pulses", we drastically reduce CPU syscall overhead and improve packet throughput.

---

## Server Administrators

Pulse is a drop-in replacement for Purpur. Download the latest JAR from our website and run it just like you would run a standard Purpur server.

[//]: # (* Documentation: [docs.pulsemc.dev]&#40;https://docs.pulsemc.dev&#41;)
* Latest Builds: [pulsemc.dev/releases](https://pulsemc.dev)

---

## Plugin Developers

Pulse provides a specialized API for advanced networking control and virtual entity/block management.

### Maven Repository
Add this to your project to access the Pulse API:

```xml
<repository>
    <id>pulse-repo</id>
    <url>https://maven.pulsemc.dev/snapshots</url>
</repository>

<dependency>
    <groupId>dev.pulsemc.pulse</groupId>
    <artifactId>pulse-api</artifactId>
    <version>1.21.11-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

### Gradle (Kotlin DSL)
```kotlin
repositories {
    maven("https://maven.pulsemc.dev/snapshots")
}

dependencies {
    compileOnly("dev.pulsemc.pulse:pulse-api:1.21.11-SNAPSHOT")
}
```

* API Javadocs: [jd.pulsemc.dev](https://jd.pulsemc.dev)

---

## Performance Innovations

| Feature | Description |
| :--- | :--- |
| **Logical Batching** | Groups packets within a tick to prevent network congestion. |
| **Virtual Blocks** | Persistent client-side blocks that survive chunk updates. |
| **Hybrid Protocol** | Optimized network transport for high-density environments. |
| **Smart Throttling** | Prioritizes combat and critical data over background updates. |
| **Chunk Optimization** | Replaces rapid block changes with full chunk packets under load. |

---

## Compiling from Source

To compile Pulse, you need JDK 21 and an active internet connection.

1. Clone this repository.
2. Run `./gradlew applyAllPatches`.
3. Run `./gradlew createMojmapPaperclipJar`.

The compiled jar will be located in the `pulse-server/build/libs` directory.

---

Creating Pull Request
------
See [Contributing](CONTRIBUTING.md)

---

## Support and Contributions

Pulse is an open-source project. We welcome contributions regarding Netty, NMS, and protocol-level optimizations.

* Discord: [Join our community](https://dsc.gg/Pulse-MC)
* Issues: [Report bugs on GitHub](https://github.com/Pulse-MC/Pulse/issues)

---

## Support the Project

Pulse is an open-source project maintained by the community. Hosting costs (Maven repo, build servers, website) are covered by donations. If Pulse has helped your server's performance, consider supporting us on Boosty:

[**Support PulseMC on Boosty**](https://boosty.to/pulsedevs)

Your support keeps the project active and allows us to focus on building new, low-level networking optimizations.

---

<div align="center">
  <b>Pulse: The Heartbeat of High-Performance Networking.</b>
</div>