# RicohSync - Claude AI Assistant Guide

## Project Overview

RicohSync is an Android application that synchronizes GPS data and date/time from your Android phone to your camera via Bluetooth Low Energy (BLE). The app supports cameras from multiple vendors (Ricoh, Sony, etc.) and can sync to **multiple cameras simultaneously** in the background.

## Project Structure

This is an Android project built with:
- **Language**: Kotlin
- **Build System**: Gradle with Kotlin DSL
- **Target Platform**: Android (tested on Pixel 9 with Android 15)
- **Hardware Target**: BLE-enabled cameras (tested with Ricoh GR IIIx)

## Key Technologies & Architecture

- **Multi-Device Architecture**: Supports pairing and syncing multiple cameras simultaneously.
- **Multi-Vendor Architecture**: Uses the Strategy Pattern to support different camera brands.
- **Bluetooth Low Energy (BLE)**: Core communication protocol using the Kable library.
- **Android Foreground Services**: Maintains connections when app is backgrounded.
- **Location Services**: Centralized GPS data collection shared across all devices.
- **Proto DataStore**: Persistent storage for paired devices using Protocol Buffers.

## Architecture Overview

### Data Layer
- `PairedDevicesRepository`: Interface for managing paired devices (add, remove, enable/disable) and global sync state
- `DataStorePairedDevicesRepository`: Proto-based persistence implementation
- `CameraRepository`: BLE scanning and connection management
- `LocationRepository`: GPS location updates from Fused Location Provider

### Domain Layer
- `PairedDevice`: Domain model for stored paired cameras
- `DeviceConnectionState`: Sealed interface for device connection states
- `Camera`: Discovered camera model with vendor information
- `CameraVendor`: Strategy interface for vendor-specific protocols

### Service Layer
- `MultiDeviceSyncService`: Foreground service managing all device connections
- `MultiDeviceSyncCoordinator`: Core sync logic for multiple concurrent connections
- `LocationCollectionCoordinator`: Centralized location collection with device registration

### UI Layer
- `DevicesListScreen`: Main screen showing paired devices with enable/disable toggles
- `PairingScreen`: BLE scanning and pairing flow for new devices
- Material 3 design with animated connection status indicators

## Development Guidelines

### Code Style
- Follow Kotlin coding conventions, using `ktfmt` with the `kotlinlang` style
- Run `./gradlew ktfmtFormat` at the end of each task to ensure consistent formatting
- Use Android Architecture Components where applicable
- Maintain compatibility with Android 12+ (backup rules configured)
- All new interfaces should have corresponding fake implementations for testing

### Key Features
1. **Multi-Device Support**: Pair and sync multiple cameras simultaneously.
2. **Camera Discovery**: Vendor-agnostic BLE device scanning.
3. **Auto-reconnection**: Automatic reconnection to enabled devices when in range (requires global sync enabled).
4. **Centralized Location**: Single location collection shared across all connected devices.
5. **Background Sync**: Maintains synchronization via a Foreground Service.
6. **GPS & Time Sync**: Real-time location and timestamp synchronization.
7. **Manual Control**: Notification actions to "Refresh" (restart sync) or "Stop All" (persistent stop).

### Testing
- Unit tests use coroutine test dispatchers with `TestScope`
- All repository interfaces have fake implementations in `test/fakes/`
- Use Khronicle for logging throughout the app (initialized in MainActivity)
- Kable logging uses KhronicleLogEngine adapter

### Testing Notes
- Primary test configuration: Pixel 9 + Android 15 + Ricoh GR IIIx
- Run tests: `./gradlew test`
- Run specific test class: `./gradlew test --tests "fully.qualified.TestClassName"`

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

### Formatting Code
```bash
./gradlew ktfmtFormat
```

## Important Considerations

- Devices must have Bluetooth pairing enabled on the camera side
- Background operation requires proper battery optimization exemptions
- Location permissions are critical for GPS sync functionality
- BLE permissions required for camera communication
- Location collection runs at 60-second intervals when devices are connected

## License

Apache License 2.0 - See LICENSE file for details.

---

*This document is maintained to help Claude AI understand the project context and provide relevant assistance.*
