Contributing to Pulse
==========================

PulseMC is happy you're willing to contribute to our projects. We are usually
very lenient with all submitted PRs, but there are still some guidelines you
can follow to make the approval process go more smoothly.

## Use a Personal Fork

Pulse will routinely modify your PR. Often, it's better for us to solve small
problems for you than make you go back and forth trying to fix them yourself.
Unfortunately, if you use an organization for your PR, it prevents Pulse from
modifying it. Please use personal repositories for PRs.

## Requirements

To get started, you'll need:
- `git`;
- A Java 21 or later JDK (we recommend [Adoptium](https://adoptium.net/)).

## Understanding the Paperweight Patch System

Pulse is built on top of Paper using the **paperweight fork system**.

This means:
- The Paper source code is downloaded automatically;
- Pulse does **not** directly modify upstream Paper sources;
- All changes are stored and tracked as **patches**;
- During build time, Paper is checked out and Pulse patches are applied on top.

Because of this, **you never edit generated sources directly**.
All meaningful changes must be converted into patch files.

A basic understanding of `git`, rebasing, and commits is required.

## Getting Started

Assuming you have already forked the repository:

1. Clone your fork to your local machine;
2. Run `./gradlew applyAllPatches` to:
   - Download Paper sources;
   - Apply all existing Pulse patches.
   (On Windows, remove the `./` prefix);
3. Navigate into:
   - `pulse-server` for server-side changes;
   - `pulse-api` for API changes;
   - `paper-server` for Paper-side server changes;
   - `paper-api` for Paper API changes.

**Only changes made in `pulse-server/src/minecraft` / `paper-api|server/src` are part of the patch system.**

## Workflow

### 1. Making Changes (Server)

1. Make your code changes in:
```
paper-server/src/main
pulse-server/src/minecraft/java
```
2. Once your changes compile and behave correctly, **commit them**:
```
git add .
git commit -am "Your change description"
```
This commit acts as the base for patch generation.
3. Go back to the project root and run:
```
./gradlew rebuildAllServerPatches
```
This will:
- Compare your commit against upstream Paper;
- Generate `.patch` files;
- Reset the working directory back to a clean state.

⚠️ **Never manually edit files inside `/minecraft-patches` or `/paper-patches`.**
They are always generated.

### 2. API Changes

For API-only changes:
1. Work inside `pulse-api`/`pulse-server`;
2. Commit your changes normally;
3. No patch rebuild is required unless Minecraft sources were touched.

## Formatting & Code Style (CRITICAL)

### 1. The File Marker (`// PULSE_MODIFIED`)

If you modify a vanilla or non-pulse file, you **MUST** add the
`// PULSE_MODIFIED` comment **immediately after the imports section**.

This allows us to quickly identify modified files during reviews and rebases,
even if the changes are small.

**Example:**
```java
package net.minecraft.server.network;

import java.util.List;
import net.minecraft.network.protocol.Packet;
// PULSE_MODIFIED

public class ServerCommonPacketListenerImpl {
 // ...
}
````

### 2. Marking Changes (Code Blocks)

All Pulse-specific logic must be clearly marked.

* Multi-line changes:

  ```java
  // Pulse start - <DESCRIPTION>
  server.doSomethingOptimized();
  // Pulse end - <DESCRIPTION>
  ```

* One-line changes:

  ```java
  server.doSomething(); // Pulse - <DESCRIPTION>
  ```

Descriptions should be short, clear, and explain *why* the change exists.

## Tips

* **IntelliJ IDEA:** Disable *"Sync external changes periodically"* to avoid lag
  during patch application and rebuilds.

* **Triggering a DevBuild:**
  By default, commits do **NOT** trigger a CI build (to save resources).

  To force building a release JAR (Paperclip), include:

  ```
  [pulse:devbuild:version]
  ```

  anywhere in your commit message.

  **Example:**

  ```
  [pulse:devbuild:1.21.11] Optimize chunk packet batching
  ```
