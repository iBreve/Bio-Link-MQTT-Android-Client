# Bio-Link MQTT Android Client

<p align="center">
  <img src="https://raw.githubusercontent.com/iBreve/Bio-Link-MQTT-Android-Client/main/FORTIS%20EU%20funding%20logo.png" alt="Bio-Link – Funded by the European Union via FORTIS" width="420"/>
</p>

> **Status:** This repository is currently private and under active development. A public open-source release is planned at project milestone M10 (February 2027). Watch this repository to be notified when it becomes available.

---

## Overview

**Bio-Link MQTT Android Client** is an Android MQTT messaging layer and accompanying documentation that forms the mobile side of the Bio-Link communication architecture. It manages session state, enforces privacy-gating logic governing when Human State Level (HSL) tokens may be shared, and handles the MQTT connection to the robot-hosted broker.

Human-state information originates from the Bio-Link Edge Hub running on the Android device. This open-source component provides the standardised MQTT interface and session logic layer — enabling structured, privacy-controlled sharing of human-state information with robotic systems over a local network.

In alignment with the [FORTIS project](https://fortis-project.eu) priorities, this component contributes to the **Human Cognition Toolkit** as a reusable Android-to-ROS2 bridge built on open standards. It supports interoperability, modular integration and reuse across FORTIS experiments and beyond.

---

## Communication Architecture

Communication operates over a local network to a robot-hosted MQTT broker (MQTT 3.1.1), ensuring all data exchange remains within the local environment. This client controls session state and governs when HSL tokens are shared; the robot publishes periodic presence updates in return.

Communication is structured around two MQTT topics:

| Topic | Direction | Description |
|---|---|---|
| `biolink/human_state` | App → Robot | HSL tokens published during active sessions |
| `biolink/robot_presence` | Robot → App | Robot status updates subscribed to by this client |

### Message Definitions

**Human State** (`biolink/human_state`) — published by this client:

```json
{
  "timestamp": "ISO8601",
  "hsl_state": "LOW | HIGH",
  "confidence": 0.0,
  "session_id": "uuid"
}
```

**Robot Presence** (`biolink/robot_presence`) — subscribed to by this client:

```json
{
  "timestamp": "ISO8601",
  "robot_id": "embrace_robot_01",
  "active": true,
  "healthy": true
}
```

The `healthy` flag indicates whether the robot is operating normally. The `active` flag indicates whether the robot intends to participate in an interaction session, allowing the robot to signal session termination (`active: false`) while remaining available for future interactions.

Payloads use a minimal UTF-8 encoded JSON structure optimised for low-latency parsing and robust operation on mobile systems. Complex nesting is avoided to ensure reliable local network communication.

> Message definitions represent the current planned communication structure and may be refined as implementation progresses.

---

## Session & Privacy Gating

Session and privacy gating logic is a first-class design concern and a key aspect of this open-source release. Key properties:

- **Session activation** — HSL token publication only begins when an active session is established. The client monitors the robot's `active` flag and aligns its own session state accordingly.
- **Session termination** — publication stops immediately when the session ends; no residual data is retained on the broker.
- **Local network scope** — the broker is robot-hosted, meaning only robots on the local network can receive HSL tokens. No data is transmitted to external services.
- **Documentation** — the complete session management and privacy-gating design will be released as `docs/session-and-privacy.md` to support audit, replication and adaptation by other FORTIS partners.

---

## Planned Features

- MQTT client for Android (MQTT 3.1.1, local broker)
- Session state management aligned with robot presence signals
- HSL token publishing with configurable topic and rate
- Robot presence subscription and status monitoring
- Privacy-gating enforcement with documented logic
- Documentation: message schema reference, session & privacy design, integration guide

---

## Planned Repository Structure

```
Bio-Link-MQTT-Android-Client/
├── app/
│   └── src/
│       ├── main/
│       │   └── java/com/ibreve/biolink/mqtt/   # MQTT client and session logic
│       └── test/                                # Unit tests
├── docs/
│   ├── message-schema.md                        # JSON message format reference
│   ├── session-and-privacy.md                   # Session management & privacy gating design
│   └── integration-guide.md                     # Integration with ROS2 Robot Bridge
├── gradle/
├── build.gradle
├── AndroidManifest.xml
└── README.md
```

---

## Dependencies (Planned)

| Dependency | Purpose |
|---|---|
| Android SDK (API 26+) | Platform |
| Eclipse Paho MQTT Android Client | MQTT communication |
| AndroidX / Jetpack | Lifecycle management |

---

## Related Component

This client publishes data consumed by the [Bio-Link ROS2 Robot Bridge](https://github.com/iBreve/Bio-Link-ROS2-Robot-Bridge), which subscribes to the MQTT topics and maps incoming human-state information to ROS 2 topics and robot behaviours.

---

## License

This project will be released under the [Apache 2.0 License](LICENSE).

```
Copyright 2027 iBreve Ltd

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```

---

## Acknowledgement

<img src=https://raw.githubusercontent.com/iBreve/Bio-Link-MQTT-Android-Client/main/FORTIS%20EU%20funding%20logo.png" alt="Funded by the European Union" width="300"/>

The Bio-Link project has received funding from EC through the FORTIS (Grant Agreement no. 101135707).

Views and opinions expressed are those of the author(s) only and do not necessarily reflect those of the European Union or the European Research Executive Agency. Neither the European Union nor the granting authority can be held responsible for them.

---

## Contact

For questions about the Bio-Link project or this repository, please open an issue or contact us at info@ibreve.com.
