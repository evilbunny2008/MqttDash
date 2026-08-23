# Z2M Dash

A small, FOSS, MIT-licensed Android dashboard app for Zigbee2MQTT, built
as a starting point to replace a closed-source apps. No cloud, no account,
no lock-in - your whole configuration is one plain JSON file you can export
and re-import (Settings → Configuration Backup / Recovery).

## Opening the project

1. Install **Android Studio** (Koala or newer).
2. `File → Open` and point it at this folder.
3. Let Gradle sync. If Android Studio suggests updating AGP/Gradle/Kotlin to
   newer patch versions than what's pinned here, accept it - these were current
   at the time this was written, and Android tooling moves fast.
4. Run on a device or emulator (minSdk 26 / Android 8.0+).

## What's implemented

- **Brokers**: add/edit/delete, TCP / SSL / WS / WSS, username+password auth,
  self-signed certificate support (pick a `.crt`/`.pem` file - it's parsed as an
  X.509 cert and added to a dedicated trust store, so you're not disabling
  certificate validation entirely), client ID, clean session, keep-alive,
  connection timeout, auto-connect.
- **Dashboard**: collapsible groups containing Sensor tiles (subscribe to a
  topic, optionally pull one field out of a JSON payload via a dot path like
  `state.battery`) and Toggle tiles (publish an ON/OFF payload to a command
  topic, optionally reflect real state from a separate state topic).
- **Background connections**: a foreground service keeps brokers connected
  when the app isn't in the foreground (toggle in Settings).
- **Config export/import**: plain JSON, human-readable, no proprietary format.

## What's intentionally left as a stub

- **Scripts** and **Terminal** tabs are placeholders - the reference app's
  scripting/automation and MQTT-CLI-style terminal weren't in scope here.
- No panel *editing* after creation (long-press to delete and re-add instead) -
  the JSON export/import round-trip is the intended power-user path for bulk
  changes.
- No drag-to-reorder for groups/panels.
- "Share configurations over MQTT" from the reference app isn't implemented.

## License

MIT. It's your fork - do whatever you want with it.
