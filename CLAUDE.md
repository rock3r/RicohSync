# RicohSync - Claude AI Assistant Guide

## Project Overview

RicohSync is an Android application that synchronizes GPS data and date/time from your Android phone to your camera via Bluetooth Low Energy (BLE). The app supports cameras from multiple vendors (starting with Ricoh) and automatically maintains synchronization in the background.

## Project Structure

This is an Android project built with:
- **Language**: Kotlin
- **Build System**: Gradle with Kotlin DSL
- **Target Platform**: Android (tested on Pixel 9 with Android 15)
- **Hardware Target**: BLE-enabled cameras (tested with Ricoh GR IIIx)

## Key Technologies & Architecture

- **Multi-Vendor Architecture**: Uses the Strategy Pattern to support different camera brands.
- **Bluetooth Low Energy (BLE)**: Core communication protocol using the Kable library.
- **Android Foreground Services**: Maintains connection when app is backgrounded.
- **Location Services**: GPS data collection for camera synchronization.
- **Android Permissions**: Location, Bluetooth, and background processing permissions.

## Development Guidelines

### Code Style
- Follow Kotlin coding conventions, use ktfmt with the Kotlinlang settings
- Use Android Architecture Components where applicable
- Maintain compatibility with Android 12+ (backup rules configured)

### Key Features
1. **Camera Discovery**: Vendor-agnostic BLE device scanning.
2. **Auto-reconnection**: Automatic reconnection to the last paired camera.
3. **Background Sync**: Maintains synchronization via a Foreground Service.
4. **GPS & Time Sync**: Real-time location and timestamp synchronization using vendor-specific protocols.

### Testing Notes
- Primary test configuration: Pixel 9 + Android 15 + Ricoh GR IIIx
- Other device combinations may work but are untested

## Common Tasks

### Building the Project
```bash
./gradlew build
```

### Running Tests
```bash
./gradlew test
```

### Installing Debug Build
```bash
./gradlew installDebug
```

## Important Considerations

- App must be in foreground during initial camera connection
- Background operation requires proper battery optimization exemptions
- Location permissions are critical for GPS sync functionality
- BLE permissions required for camera communication

## License

Apache License 2.0 - See LICENSE file for details.

---

*This document is maintained to help Claude AI understand the project context and provide relevant assistance.*