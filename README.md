Pinstr
======

[![Language](https://img.shields.io/badge/language-kotlin-brightgreen.svg)](https://www.github.com/fibelatti/pinboard-kotlin)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://opensource.org/licenses/Apache-2.0)

> **Note:** This is a fork of [Pinboard Kotlin (Pinkt)](https://github.com/fibelatti/pinboard-kotlin) by Filipe Belatti,
> adapted for the [Nostr protocol](https://nostr.com/) using [NIP-B0](https://github.com/nostr-protocol/nips) for decentralized bookmarking.

Pinstr is a [FOSS](https://en.wikipedia.org/wiki/Free_and_open-source_software) Android client for decentralized bookmarking on [Nostr](https://nostr.com/).

* **Public Bookmarks**: Share your bookmarks openly on the Nostr network
* **Private Bookmarks**: End-to-end encrypted bookmarks using quantum-resistant vault encryption (Argon2id + AES-256-GCM)
* **Nostr Authentication**: Secure key management via [Amber](https://github.com/greenart7c3/Amber) (NIP-55)
* **Cross-Platform Sync**: Works seamlessly with the [Pinstr web app](https://pinstr.co)
* **Self-Sovereign**: Your bookmarks, your keys, your relays

Status
------

**This app is currently in active development.**

Releases will be available here once the MVP is complete.

Features
--------

Save links from your favorite websites and apps quickly by sharing them to Pinstr.

**Core Features:**
- Manage all your bookmarks: add, edit, delete, share
- Public and private (encrypted) bookmarks
- Quickly save links from any app using the Android share sheet actions
- Search by term: find bookmarks that contain the term in its URL, Title or Description
- Filter by tags
- Pre-defined filters: All, Public, Private
- Sync bookmarks with Nostr relays
- Cached data for offline usage
- Dark and Light themes
- Dynamic color support
- Portrait and Landscape support

**Nostr-Specific Features:**
- Amber integration (NIP-55) for secure key management
- Private vault with quantum-resistant encryption (Argon2id + AES-256-GCM)
- Cross-platform sync with Pinstr web app
- Configurable relay selection
- NIP-B0 bookmark format (kind 39701 for public, kind 39702 for private)

About the project
--------

Pinstr builds upon the excellent [Pinboard Kotlin](https://github.com/fibelatti/pinboard-kotlin) codebase to bring
decentralized bookmarking to Android via the Nostr protocol.

The project demonstrates:

- Adapting existing Android architecture to work with decentralized protocols
- Integration of Nostr relays (WebSocket) instead of traditional REST APIs
- Quantum-resistant encryption for private data (Argon2id + AES-256-GCM)
- Cross-platform compatibility between web and mobile apps
- Android Jetpack Libraries, including WorkManager and Room (with FTS)
- Clean & beautiful UI built with Jetpack Compose and Material Design 3
- Kotlin, Coroutines and Flows
- DI using Hilt

**Technology Stack:**
- Nostr protocol (NIP-B0 for bookmarks, NIP-55 for signing via Amber)
- Room database for local caching and offline support
- Argon2id + AES-256-GCM for vault encryption
- WebSocket connections to Nostr relays
- Jetpack Compose with Material Design 3

Contributing
--------

Bug reports, feature requests, and improvement ideas are welcome!

Credits
--------

This project is based on [Pinboard Kotlin (Pinkt)](https://github.com/fibelatti/pinboard-kotlin) by Filipe Belatti.

The original architecture, UI components, database layer, sync infrastructure, and share sheet integration
have been preserved and adapted for Nostr protocol integration. We are grateful for the excellent foundation
provided by the original project.

License
--------

    Pinstr - Nostr Bookmarking for Android
    Copyright 2026 Robert

    This product includes software developed by Filipe Belatti (Pinboard Kotlin).

    Original Pinboard Kotlin Copyright 2019 Filipe Belatti
    https://github.com/fibelatti/pinboard-kotlin

    Licensed under the Apache License, Version 2.0 (the "License");
    you may not use this file except in compliance with the License.
    You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

    Unless required by applicable law or agreed to in writing, software
    distributed under the License is distributed on an "AS IS" BASIS,
    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
    See the License for the specific language governing permissions and
    limitations under the License.

    See the NOTICE file for additional information regarding copyright
    and licensing of included software.
