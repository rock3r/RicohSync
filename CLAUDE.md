# RicohSync - Claude AI Assistant Guide

## Project Overview

RicohSync is an Android application that synchronizes GPS data and date/time from your Android phone to your Ricoh camera via Bluetooth Low Energy (BLE). The app automatically connects to supported Ricoh cameras and maintains synchronization in the background.

## Project Structure

This is an Android project built with:
- **Language**: Kotlin
- **Build System**: Gradle with Kotlin DSL
- **Target Platform**: Android (tested on Pixel 9 with Android 15)
- **Hardware Target**: Ricoh cameras (tested with GR IIIx)

## Key Technologies & Architecture

- **Bluetooth Low Energy (BLE)**: Core communication protocol with Ricoh cameras
- **Android Foreground Services**: Maintains connection when app is backgrounded
- **Location Services**: GPS data collection for camera synchronization
- **Android Permissions**: Location, Bluetooth, and background processing permissions

## Development Guidelines

### Code Style
- Follow Kotlin coding conventions, use ktfmt with the Kotlinlang settings
- Use Android Architecture Components where applicable
- Maintain compatibility with Android 12+ (backup rules configured)

### Key Features
1. **Camera Discovery**: BLE device scanning to find nearby Ricoh cameras
2. **Auto-reconnection**: Automatic reconnection when camera becomes available
3. **Background Sync**: Continues syncing when app is backgrounded
4. **GPS & Time Sync**: Real-time location and timestamp synchronization

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