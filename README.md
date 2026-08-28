## Z2M Dash

An Android dashboard app for Zigbee2MQTT, built to replace closed-source apps.
No cloud, no account, no lock-in - your whole configuration is either one plain
JSON file you can export and re-import or auto-configure settings stored in MQTT.

## Features

### Dashboard
- Groups of clusters, each cluster grouping the related tiles for one physical
  device (sensor readings, toggles/buttons) under a shared name
- Sensor tiles with configurable decimal places, units, and an optional ideal
  min/max range that colours the tile red/green/blue when out of range
- Toggle tiles reflecting live device state (not just the last command sent),
  and momentary button tiles for one-shot commands like a blind motor's STOP
- Presence/occupancy sensor tiles - recognises both Zigbee2MQTT's "occupancy"
  and "presence" conventions, shown as "Detected"/"Clear" with a tinted icon
  rather than a raw true/false
- Drag-and-drop reordering for groups, clusters within a group, and panels
  within a cluster (long-press to start dragging)
- Groups can be collapsed/expanded, and deleted along with their panels
- New-device and new-cluster prompts (see Auto-configure below) show as
  banners on the dashboard as well as system notifications

### Auto-configure via MQTT
Devices (or a small companion script) can publish their own dashboard config
to a `<topic>/app` MQTT topic - the app detects it automatically and prompts
to add the described panels, no manual per-device setup required. Supports:
- Sensor panels for any field in the device's regular state payload
- Toggle and momentary-button controls (a control without an `off_payload` is
  treated as a single-press button rather than an on/off switch)
- Per-field decimal places, custom ordering within and across clusters, and
  splitting one physical device's fields across multiple clusters
- A manual reorder of auto-configured panels/clusters republishes an updated,
  retained `/app` message reflecting the new arrangement, so the device's own
  config stays in sync rather than being silently reverted on its next update

### Smoke alerts
Watches every incoming MQTT payload, across every topic, for a `"smoke": true`
field - no per-device registration needed. Posts a high-priority notification
(with an optional loud, alarm-style sound) the moment any topic's smoke state
transitions to true, and clears it automatically once smoke is no longer
reported. Both the alert and the sound can be turned off independently in
Settings.

### Terminal
A live, filterable log of every MQTT message across all configured brokers -
useful for debugging payloads and topic structures without a separate MQTT
client.

### Backup & restore
- Export/import the whole configuration as a single gzip-compressed JSON file
- Publish/restore a backup via a retained MQTT message instead, for
  moving configuration between devices without a file transfer
- A restore can optionally preserve the brokers already configured on this
  device rather than overwriting them too

### Brokers & connections
- Multiple brokers, each over TCP, SSL, WS, or WSS (including self-signed
  certificates)
- A persistent background connection (optional, toggle in Settings) keeps
  data fresh even when the app isn't open - though since MQTT topics are
  typically retained, this isn't strictly required for correctness, just for
  live updates while backgrounded
- Manual sensor discovery, scanning a broker's traffic for numeric fields to
  help build panels for devices that don't publish their own `/app` config

## Only tested using Zigbee2MQTT

I have only tested this against Zigbee2MQTT, I have no idea how much it will work
when there is other software in the mix, like Home Assistant

## Screenshots

<img width="300px" src="https://github.com/evilbunny2008/Z2mDash/blob/main/design/Screenshot_20260823-223346.png?raw=true"> <img width="300px" src="https://github.com/evilbunny2008/Z2mDash/blob/main/design/Screenshot_20260823-231443.png?raw=true"><br><br><br>
<img width="600px" src="https://github.com/evilbunny2008/Z2mDash/blob/main/design/Screenshot_20260825-034747.png?raw=true">

## Copyright/License

As this was entirely vibe coded using [Claude.ai](https://claude.ai) the code isn't copyrightable by me.
