# FamilySonar 📍

FamilySonar is a native Android portfolio prototype for sharing a device location with trusted contacts over SMS. It combines local contact management, runtime permission handling, GPS/network location updates, geocoding, foreground services, and emergency messaging without a custom backend or user account.

> **Status:** working portfolio prototype, version `1.0`.

## 🧭 Overview

The application stores trusted phone numbers locally. A user can send an SOS message to every saved contact, request a location update for one contact, or allow an authorized contact to request the location by sending the exact `?loc?` SMS command.

The active application is a single Android module built around one launcher `Activity`, system `BroadcastReceiver` components, a foreground `Service`, and local file storage. SMS delivery, carrier availability, device permissions, and location-provider state remain external dependencies.

## 🧰 Used Technologies

<p align="left">
  <a href="https://www.java.com/"><img src="https://img.shields.io/badge/Java-8-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white" alt="Java 8"></a>
  <a href="https://developer.android.com/"><img src="https://img.shields.io/badge/Android-SDK%2036-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android SDK 36"></a>
  <a href="https://gradle.org/"><img src="https://img.shields.io/badge/Gradle-8.13-02303A?style=for-the-badge&logo=gradle&logoColor=white" alt="Gradle 8.13"></a>
  <a href="https://developer.android.com/jetpack/androidx"><img src="https://img.shields.io/badge/AndroidX-AppCompat%201.7.0-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="AndroidX AppCompat 1.7.0"></a>
  <a href="https://m3.material.io/"><img src="https://img.shields.io/badge/Material%20Components-1.12.0-757575?style=for-the-badge&logo=materialdesign&logoColor=white" alt="Material Components 1.12.0"></a>
  <a href="https://developers.google.com/android/guides/overview"><img src="https://img.shields.io/badge/Play%20services%20Location-15.0.1-4285F4?style=for-the-badge&logo=googleplay&logoColor=white" alt="Google Play services Location 15.0.1"></a>
</p>

## ✨ Key Features

- **Trusted contacts:** add phone numbers, persist them locally, remove them with a swipe gesture, and display them in a `RecyclerView`.
- **Manual location requests:** start a one-time request for a selected contact from the contact list.
- **SMS location requests:** accept the exact `?loc?` command only when the sender's number matches a saved contact.
- **Emergency SOS:** send the last location returned by the fused location provider to all saved contacts.
- **GPS and network location:** use Android `LocationManager` providers, with GPS preferred when a recent GPS fix is available.
- **Location response:** send the measurement time, coordinates, and a geocoded address as separate SMS messages for an authorized request.
- **Foreground location service:** expose ongoing location work through an Android notification and use slower and faster refresh intervals.
- **Permission guidance:** request SMS, foreground/background location, and notification permissions in sequence and show their current status in the UI.
- **Battery settings shortcut:** open Android battery-saver settings from the main screen.

## 🏛️ Architecture

FamilySonar uses a small, event-driven native Android architecture rather than a multi-layer or backend-based design:

| Layer | Responsibility | Main implementation |
| --- | --- | --- |
| Presentation | Main screen, permission status, contact list, SOS action, and location display | `MainActivity`, XML layouts, `ContactsAdapter` |
| Local data | Serialize and restore trusted contacts from app-private storage | `ConfigData`, `Contact`, `ContactList` |
| System events | Receive SMS broadcasts and schedule manual contact requests | `SMSBroadcastReceiver`, `AlarmReceiverClass` |
| Background work | Obtain location, geocode it, broadcast updates, and send SMS responses | `LocationService` |
| Platform integration | Runtime permissions, notification channel, wake locks, `SmsManager`, `LocationManager`, and `Geocoder` | Android SDK and Google Play services Location |

The repository contains one `app` module. The main screen is implemented directly in `MainActivity`; the Navigation Component dependency and navigation resource are present, but the current launcher flow does not use a navigation host or feature fragments.

## 🧩 Core Modules

| Module | Role |
| --- | --- |
| [`MainActivity`](app/src/main/java/com/familysonar/MainActivity.java) | Initializes the UI, loads contacts, manages runtime permissions, handles contact changes, opens battery settings, refreshes displayed location data, and sends SOS messages. |
| [`LocationService`](app/src/main/java/com/familysonar/LocationService.java) | Runs as a location foreground service, reads GPS/network updates, maintains the current fix, geocodes the location, notifies the Activity, and sends authorized request responses. |
| [`SMSBroadcastReceiver`](app/src/main/java/com/familysonar/SMSBroadcastReceiver.java) | Reads incoming SMS PDUs, matches the exact `?loc?` command and sender number, then starts a short fast-refresh request. |
| [`AlarmReceiverClass`](app/src/main/java/com/familysonar/AlarmReceiverClass.java) | Handles the manual contact-list request, sends a `START` message, and returns the last or next available location coordinate. |
| [`ContactsAdapter`](app/src/main/java/com/familysonar/ContactsAdapter.java) | Binds saved phone numbers to the `RecyclerView` and exposes the per-contact location action. |
| [`ConfigData`](app/src/main/java/com/familysonar/ConfigData.java) | Stores `ContactList` in the app's private files directory as `configdata.dat`. |
| [`JobService`](app/src/main/java/com/familysonar/JobService.java) | Contains an alternate cell-information SMS response path; the active SMS receiver starts `LocationService` instead of scheduling this job. |

`BootCompleteReceiverClass` is present as a Java class, but it is not registered in the current manifest and therefore is not documented as an active boot-start feature.

## 🔄 Data Flow

### Authorized SMS request

```mermaid
sequenceDiagram
    participant Contact as Trusted contact
    participant Carrier as SMS carrier
    participant Receiver as SMSBroadcastReceiver
    participant Storage as ConfigData
    participant Service as LocationService
    participant Device as GPS or network provider
    participant Geocoder as Android Geocoder

    Contact->>Carrier: Send ?loc?
    Carrier->>Receiver: SMS_RECEIVED
    Receiver->>Storage: Load saved phone numbers
    alt Exact command and trusted sender
        Receiver->>Service: Start fast request
        Service->>Device: Request one location update
        Device-->>Service: Coordinates and timestamp
        Service->>Geocoder: Resolve address
        Service->>Contact: Send time, coordinates, and address
    else Unknown sender or different message
        Receiver-->>Contact: Ignore request
    end
```

The foreground service uses a 15-minute standard interval and a 10-second fast interval. A fast SMS request is configured for one location update and has a 30-second timeout response when no update is obtained.

### Local manual request and SOS

- A contact-row action schedules `AlarmReceiverClass`, which sends `START` and then sends one coordinate response using the fused last location or a provider update.
- The SOS action reads the fused last location and sends one emergency SMS to each saved contact.
- There is no public HTTP API, application server, external database, or account system in this repository.

## 🛠️ Technology Stack

| Area | Technologies and configuration |
| --- | --- |
| Language | Java with source and target compatibility set to Java 8 |
| Android build | Android Gradle Plugin `8.13.0`, Gradle Wrapper `8.13`, `compileSdk 36`, `targetSdk 34`, `minSdk 28` |
| UI | Android XML layouts, AndroidX AppCompat `1.7.0`, Material Components `1.12.0`, ConstraintLayout `2.1.4`, RecyclerView, and `ItemTouchHelper` |
| Navigation dependencies | AndroidX Navigation Fragment/UI `2.7.7`; a navigation resource is included but is not connected to the current single-Activity screen |
| Location | Google Play services Location `15.0.1`, Android `LocationManager`, GPS, network provider, and `Geocoder` |
| Communication | Android `SmsManager`, `BroadcastReceiver`, `AlarmManager`, and foreground `Service` |
| Testing | JUnit `4.13.2` and AndroidX Test JUnit `1.1.5`; Espresso Core `3.5.1` is defined in the version catalog but is not wired into `app/build.gradle` |

Dependency versions are centralized in [`gradle/libs.versions.toml`](gradle/libs.versions.toml), and the Android module configuration is in [`app/build.gradle`](app/build.gradle).

## 🚀 Getting Started

### Requirements

- Android Studio with Android SDK platform API 36 installed.
- JDK 17 or newer. The workspace build task also supports the detected Android Studio JDK.
- A physical Android device running Android 9/API 28 or newer for meaningful SMS and location testing.
- A SIM-enabled device and a second phone for testing SMS request and SOS flows.

### Clone and open

```powershell
git clone https://github.com/marek-czelen/FamilySonar.git
cd FamilySonar
```

Open the project in Android Studio and synchronize it with Gradle. Set `JAVA_HOME` to a JDK 17+ installation if Android Studio or the terminal cannot detect one. Android Studio will normally create the machine-specific `local.properties` file containing the Android SDK path; it should not be committed.

### Build from PowerShell

```powershell
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

The generated APK files are written to:

```text
app/build/outputs/apk/debug/app-debug.apk
app/build/outputs/apk/release/app-release.apk
```

The release build currently uses the debug signing configuration and has code shrinking disabled. It is suitable for development and portfolio review, not for a production publishing pipeline.

### Install on a connected device

```powershell
.\gradlew.bat assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell monkey -p com.familysonar 1
```

On first launch, grant the requested SMS, foreground/background location, and notification permissions. Use the in-app battery settings shortcut when the device restricts background work.

## 🔐 Security Considerations

### Implemented protections

- Runtime permission checks cover SMS, fine/coarse location, background location, and notifications; foreground location service permissions are declared in the manifest.
- Incoming location requests are accepted only for the exact `?loc?` command and an exact phone-number match in the saved contact list.
- Contacts are stored in the application's private files directory rather than in a shared external location.
- Location work is visible through a foreground-service notification.

### Limitations to address before production use

- `configdata.dat` is Java-serialized local data and is not encrypted. The manifest enables Android backup, while the repository's backup rules do not exclude this file; privacy-sensitive contact data should be reviewed before distribution.
- SMS and location data are sensitive. The request protocol has no cryptographic authentication or message encryption, and authorization depends on the sender address supplied by the telephony stack.
- Phone numbers are compared as raw strings. International formatting, normalization, duplicates, and spoofing or carrier-specific sender formats are not handled.
- SMS send failures, unavailable providers, missing last locations, and some permission/error paths are not surfaced through a complete user-facing error model.
- The release variant uses the debug key. A production release would need a protected signing key, explicit backup policy, privacy documentation, and a tested release process.

## 🧪 Testing

The repository contains two example tests:

- [`ExampleUnitTest`](app/src/test/java/com/familysonar/ExampleUnitTest.java) verifies a basic local JUnit assertion.
- [`ExampleInstrumentedTest`](app/src/androidTest/java/com/familysonar/ExampleInstrumentedTest.java) verifies the application package name on an Android device.

Run the local unit-test task with:

```powershell
.\gradlew.bat testDebugUnitTest
```

Feature-specific tests for permissions, SMS reception, location providers, geocoding, contact persistence, and failure handling are not implemented yet. Instrumented tests require a connected device or emulator and are not a substitute for physical-device SMS testing.

## 📁 Project Structure

```text
FamilySonar/
├── app/
│   ├── src/main/java/com/familysonar/
│   │   ├── MainActivity.java
│   │   ├── LocationService.java
│   │   ├── SMSBroadcastReceiver.java
│   │   ├── AlarmReceiverClass.java
│   │   ├── ConfigData.java
│   │   ├── Contact.java / ContactList.java
│   │   └── ContactsAdapter.java
│   ├── src/main/res/          # XML layouts, themes, icons, and supporting resources
│   ├── src/test/              # local unit test
│   ├── src/androidTest/       # instrumented Android test
│   └── build.gradle
├── gradle/libs.versions.toml  # centralized dependency versions
├── gradlew / gradlew.bat      # Gradle Wrapper scripts
├── settings.gradle
└── README.md
```

## 📌 Project Status & Future Improvements

**Current status:** working portfolio prototype, version `1.0`. The debug build and local unit-test task complete successfully in the current development environment. No production deployment or release distribution is configured in the repository.

Potential next steps based on the current implementation:

- Add feature-level unit and instrumentation coverage for permission, SMS, service, persistence, and error flows.
- Normalize and validate phone numbers before saving and comparing them.
- Replace plain Java serialization with a safer, encrypted storage strategy and define explicit backup exclusions.
- Improve handling and user feedback for null locations, provider failures, SMS failures, and denied permissions.
- Separate UI, location, SMS, and persistence responsibilities into clearer layers.
- Configure production signing, CI checks, and a documented release process.
- Evaluate a signed request protocol or a backend/push-based channel for use cases where SMS is insufficient.

## 🤝 Contributing

Contributions that improve reliability, privacy, accessibility, or device compatibility are welcome:

1. Fork the repository and create a focused feature or fix branch.
2. Keep changes scoped and document any Android-version or device-specific behavior.
3. Run `assembleDebug` and `testDebugUnitTest` before opening a pull request.
4. Describe the tested device/API level and any SMS or location prerequisites.

## 👤 Author

**Marek** · [marek-czelen](https://github.com/marek-czelen)

### 💼 Portfolio Project

FamilySonar is a practical portfolio project demonstrating native Android development, background execution, runtime permissions, location services, SMS communication, and honest documentation of security and hardware-dependent limitations. It should be treated as a learning and review artifact rather than a production safety service.