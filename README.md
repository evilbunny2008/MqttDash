# MQTT Dash Clone

A small, FOSS, MIT-licensed Android dashboard app for MQTT / Zigbee2MQTT, built
as a starting point to replace a closed-source app that was moving toward
encrypted/proprietary config files. No cloud, no account, no lock-in - your
whole configuration is one plain JSON file you can export and re-import
(Settings → Configuration Backup / Recovery).

## Why HiveMQ's client instead of Paho

Paho's Android MQTT client has a long-standing bug where its websocket
transport's keepalive scheduling drifts under Android's background network
throttling, silently dropping the connection (commonly reported as "disconnects
every ~5 minutes"). `hivemq-mqtt-client` is actively maintained, supports MQTT
3.1.1 and 5.0, and has its own ping/keepalive + automatic-reconnect state
machine over TCP, SSL, WS and WSS transports, independent of that bug.

See `mqtt/MqttConnection.kt` - that's the whole fix, in one file.

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

## A note on API surface

This targets `hivemq-mqtt-client:1.3.17`, Compose BOM `2024.06.00`, Kotlin
`1.9.24` and AGP `8.5.2` - current at time of writing. The HiveMQ client's
fluent builder API (`.sslConfig()...applySslConfig()`,
`.webSocketConfig()...applyWebSocketConfig()`,
`.automaticReconnect()...applyAutomaticReconnect()`) is stable across recent
1.3.x releases, but if you bump the dependency version and something doesn't
compile, check https://hivemq.github.io/hivemq-mqtt-client/docs/client/ for the
current method names - it's usually a rename, not a redesign.

## License

MIT. It's your fork - do whatever you want with it.
