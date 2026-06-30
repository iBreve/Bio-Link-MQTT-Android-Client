# Bio-Link MQTT Android Client

<p align="center">
  <img src="https://raw.githubusercontent.com/iBreve/Bio-Link-MQTT-Android-Client/main/FORTIS%20EU%20funding%20logo.png" alt="Bio-Link – Funded by the European Union via FORTIS" width="420"/>
</p>

> **Status:** This repository is currently private and under active development. A public open-source release is planned at project milestone M10 (February 2027). Watch this repository to be notified when it becomes available.

---

## Overview

**Bio-Link MQTT Android Client** is a reusable Android MQTT client library. It provides a single coroutine-friendly API over established MQTT engines, so application code can connect, publish, and subscribe without depending directly on a specific MQTT implementation.

The library is a general-purpose MQTT transport layer for Android — it is not tied to any specific application, message schema, or robotics framework. It is used as the MQTT transport layer within the Bio-Link Android application, and is released openly so that it can be reused independently by other FORTIS partners and by third-party Android developers building MQTT-connected applications.

In alignment with the [FORTIS project](https://fortis-project.eu) priorities, this component contributes to the **Human Cognition Toolkit** by providing a dependable, standards-based MQTT transport layer for human-robot interaction systems. By keeping the library decoupled from any particular schema or use case, it supports interoperability, modular integration, and reuse across FORTIS experiments and beyond.

---

## What This Library Provides

- A single, coroutine-friendly Kotlin API for MQTT connect, publish, subscribe, and disconnect
- Support for multiple MQTT engines under a common interface, so applications are not locked to one implementation
- Observable connection state and incoming messages, for clean integration with modern Android app architecture
- Configurable connection options (credentials, QoS, keep-alive, last will, automatic reconnect)
- Input validation to catch common configuration mistakes early

This library is schema-agnostic: it transports MQTT messages but does not define or interpret message content. Applications built on top of it — such as the Bio-Link Android application — define their own topics and payload structures separately; that application-level logic is not part of this repository.

---

## Value for FORTIS Partners and Third-Party Developers

- **FORTIS partners** can reuse this library directly as a tested MQTT transport layer for their own Android-based experiments, independent of how Bio-Link uses it.
- **Third-party Robot and Android developers** get a general-purpose, dependency-light MQTT client library suitable for any project requiring reliable MQTT connectivity from Android.
- **Anyone integrating with the Bio-Link ecosystem** benefits from visibility into the transport layer that underpins the Android side of the Bio-Link communication architecture.

---

## Planned Repository Contents at Release (M10)

- MQTT client library with API documentation
- Sample application demonstrating connect, publish, subscribe, and disconnect
- Build, test, and publishing instructions

---

## Related Component

This library is used as the MQTT transport layer on the Android side of the Bio-Link communication architecture, which connects to the [Bio-Link ROS2 Robot Bridge](https://github.com/iBreve/Bio-Link-ROS2-Robot-Bridge) via MQTT. See that repository for the robot-side integration and message structures.

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

<img src="https://raw.githubusercontent.com/iBreve/Bio-Link-MQTT-Android-Client/main/FORTIS%20EU%20funding%20logo.png" alt="Funded by the European Union" width="300"/>

The Bio-Link project has received funding from EC through the FORTIS (Grant Agreement no. 101135707).

Views and opinions expressed are those of the author(s) only and do not necessarily reflect those of the European Union or the European Research Executive Agency. Neither the European Union nor the granting authority can be held responsible for them.

---

## Contact

For questions about the Bio-Link project or this repository, please open an issue or contact us at info@ibreve.com.
