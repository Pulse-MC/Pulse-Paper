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

## Understanding Patches

Modifications to Minecraft source files are done through patches.
Because this entire structure is based on patches and git, a basic understanding
of how to use git is required.

Assuming you have already forked the repository:

1. Clone your fork to your local machine;
2. Run `./gradlew applyPatches` in a terminal to apply the patches.
   (On Windows, remove the `./` at the beginning);
3. Navigate into `paper-server` for server changes, and `paper-api` for API changes.

**Only changes made in `paper-server/src/minecraft` have to deal with the patch system.**

## Workflow

### 1. Making Changes
1. Modify the files in `paper-server/src/minecraft`;
2. Run `./gradlew rebuildPatches` in the root directory.
   This will convert your changes into `.patch` files in the `patches` directory.

### 2. Resolving Conflicts (If git complains)
If you run into conflicts, you can fix them manually via rebase:
1. `cd` into `paper-server/src/minecraft/java`;
2. Run `git rebase -i base`;
3. Edit the conflicting commits, fix code, run `git add .` and `git rebase --continue`;
4. Go back to root and run `./gradlew rebuildPatches`.

## Formatting & Code Style (CRITICAL)

### 1. The File Marker (`// PULSE_MODIFIED`)
If you modify a vanilla or non-pulse file, you **MUST** add the `// PULSE_MODIFIED` comment immediately after the imports section.
This flag allows us to quickly identify modified files, even if the changes are small.

**Example:**
```java
package net.minecraft.server.network;

import java.util.List;
import net.minecraft.network.protocol.Packet;

// PULSE_MODIFIED

public class ServerCommonPacketListenerImpl {
    // ...
}
```

### 2. Marking Changes (Blocks)
Specific code changes inside the file should be marked with comments.

- Multi-line changes:
  ```java
  // Pulse start - <DESCRIPTION>
  server.doSomethingOptimized();
  // Pulse end - <DESCRIPTION>
  ```
- One-line changes:
  ```java
  server.doSomething(); // Pulse - <DESCRIPTION>
  ```


## Tips

- **IntelliJ IDEA:** Disable "Sync external changes periodically" to avoid lag during patching.
- **Triggering a Build:** By default, commits do **NOT** trigger a CI build (to save resources).
  If you want to build a release JAR (Paperclip), you must include `[pulse:buildclip]` in your commit message.

  *Example:* `[pulse:buildclip] Optimized chunk sending logic`
