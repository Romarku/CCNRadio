# CCNR: Easy Tape Recorder

A Minecraft **1.20.1 Forge** mod that adds a server-backed tape recorder and a tape player.

## Features

- **Tape Recorder** (`ccnradio:tape_recorder`)
  - Right-click to open the recorder GUI.
  - Put a **Blank Tape** in the input slot, click **Browse...** to pick an audio file from your
    computer (or paste a path into the path field), press **Upload**, then select the entry from
    the shared server library and press **Burn**.
  - The burned tape stores the server-side media UUID, display name, duration and uploader in NBT.
  - All players can see and burn each other's uploaded audio.
  - An uploader can delete their own audio with the small `x` on the row (click twice to confirm).
- **Tape Player** (`ccnradio:tape_player`)
  - Has a horizontal facing and can be rotated.
  - Right-click to play the inserted tape once; sneak + right-click to stop.
  - Sneak + right-click works while holding a non-block item (a held block is still placed,
    as in vanilla) and with empty hands.
  - Right-click (or sneak + right-click) with the **Tape Player Configurator** to open its GUI.
  - The playback range can be set from **4 to 64 blocks**.
  - The range behaves like a jukebox: walking out of range stops the sound locally, but the
    server keeps the playback clock running, so walking back in resumes at the current position.
- **Chunked upload/download** for files up to 20 MB (Minecraft custom payloads are much smaller).
- **Commands** (permission level 2 / OP):
  - `/ccnradio adminuploadonly` – only OPs may upload.
  - `/ccnradio anyonecanupload` – everyone may upload (default).
  - `/ccnradio status` – show the current mode.

## Audio formats

The mod accepts:

- **OGG Vorbis** (`.ogg`) – decoded with LWJGL's bundled `stb_vorbis`.
- **WAV** (`.wav`) – decoded/converted with the JDK's Java Sound implementation.
- **MP3** (`.mp3`) – decoded with [JLayer](https://www.javazoom.net/javalayer/javalayer.html),
  which is unpacked into the mod jar (LGPL-2.1, see `LICENSE-JLAYER.txt`). No extra download or
  native library is required on the client.

Uploads are rejected when they exceed **10 minutes** or **20 MB** (server-side re-validation is
authoritative).

## Storage layout

On the server (game directory / dedicated server root):

```
ccnradio/
  settings.json
  media/
    <uploader UUID>/
      <media UUID>.ogg|wav
      <media UUID>.json
  tmp/
```

On the client, downloaded audio is cached in:

```
ccnradio/client_media/<media UUID>.ogg|wav
```

Uploaded display names are never used as file names; all files are addressed by UUID, and all
paths are resolved from the `ccnradio` root.

## Building

Requirements: JDK 17 and the included Gradle wrapper.

```bat
gradlew.bat build
```

The mod jar is written to `build/libs/ccnradio-1.1.0.jar`. It already contains the JLayer
classes needed for MP3 playback, so a single jar is enough.

Gradle 8.8 itself must run on JDK 17–21. A newer JDK on `PATH` (for example JDK 25) is not
supported by this Gradle/ForgeGradle combination and fails while compiling the build script with
`Unsupported class file major version 69`; point `JAVA_HOME` at a JDK 17 install in that case:

```bat
set JAVA_HOME=C:\Program Files\Java\jdk-17
gradlew.bat build
```

### If the build fails with a ForgeGradle certificate error

```
Failed to validate certificate for host 'https://maven.minecraftforge.net/'.
```

ForgeGradle opens a one-off HTTPS probe to the Forge Maven when the plugin is applied and
aborts the build when that certificate cannot be validated. This happens behind a
TLS-inspecting proxy/antivirus (the proxy root CA is not in the JDK trust store) or with a JDK
whose `cacerts` is outdated. `gradle.properties` therefore contains the official workaround:

```properties
systemProp.net.minecraftforge.gradle.check.certs=false
```

Only that reachability probe is skipped; Gradle still validates certificates for the real
dependency downloads. If downloads themselves fail with `PKIX path building failed`, import
the proxy root CA into the JDK trust store (or set
`-Djavax.net.ssl.trustStore=C:/path/to/truststore.jks` in `org.gradle.jvmargs`) and remove the
flag. For a single run you can also pass it on the command line:
`gradlew build -Dnet.minecraftforge.gradle.check.certs=false`.

## License

CCNR: Easy Tape Recorder is free software licensed under the **GNU General Public License,
version 3 (GPL-3.0)**. The full text is in [LICENSE](LICENSE) and is also shipped inside the
mod jar. In short:

- you may use, study, modify and redistribute the mod, including commercially;
- if you distribute a modified version (or a binary built from one) you must release its
  complete corresponding source under the GPL-3.0 as well;
- when redistributing, keep the license text and copyright notices intact and state the changes
  you made.

The mod is provided **without any warranty**, as stated in the license.

The bundled MP3 decoder **JLayer 1.0.1** is licensed under the **LGPL-2.1**; its license text is
in `LICENSE-JLAYER.txt` (also inside the jar). JLayer is an independent library and its license
does not change the GPL-3.0 licensing of this mod.

> Note: the pre-existing `LICENSE.txt` is the Forge MDK's notice for the Forge/MCP components
> used by the development environment; it does not apply to the mod's own code.

## Notes

- The tape player uses a Blockbench model (`assets/ccnradio/models/block/tape_player.json`)
  with its own 32x32 texture (`assets/ccnradio/textures/block/ccnradio_texture.png`). The block
  collision/selection shape is trimmed to that 8x12x3.5 model instead of a full cube. The
  recorder still uses vanilla textures and can be given a custom model the same way.
- The client uses Java Sound for playback so distance attenuation and stereo panning can be
  applied directly. The RECORDS and MASTER volume sliders are respected.
- A tape is announced one second before its playback clock starts. Clients use that window to
  fetch the media, so **short clips** are no longer skipped when the download finishes after the
  clip would already have ended. Downloads that fail transiently are retried instead of muting
  the tape for its whole duration.
- **Browse...** opens the operating system's file chooser on a separate thread, so the game
  keeps rendering. If the chooser cannot be created (for example on a headless JVM), the mod
  reports it in the GUI and the path field can be used to paste the file location instead.
- Playback synchronization is server-authoritative. Clients refresh their playback position
  about once per second while in range.
