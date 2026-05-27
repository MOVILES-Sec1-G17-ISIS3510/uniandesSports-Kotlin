# Telemetry & Analytics Integration (Locker / Firebase Analytics)

## 1. Architecture Overview

To answer business-oriented telemetry questions (such as event volume and user interactions), the application utilizes a centralized logging engine based on the `LogViewModelInterface`. This ensures that tracking is cleanly decoupled from direct SDK dependencies, allowing easy testing and swappable backends (e.g., Firebase, local logging, or a mock engine).

```mermaid
graph TD
    UI[UI Views: e.g., ProfesoresScreen] -->|Calls log()| LVM[LogViewModelInterface]
    LVM -->|Implemented by| FLVM[FirebaseLogViewModel]
    LVM -->|Implemented by| DLVM[DummyLogViewModel]
    FLVM -->|Sends Events| FA[Firebase Analytics SDK]
    FLVM -->|Sends Exceptions| FC[Firebase Crashlytics SDK]
    FLVM -->|Prints Console Log| LOG[Logcat: TELEMETRY_LOG]
```

---

## 2. Key Components

### Telemetry Interface
* **File Path**: [LogViewModelInterface.kt](file:///c:/Users/juli2/StudioProjects/uniandesSports-Kotlin/app/src/main/java/com/uniandes/sport/viewmodels/log/LogViewModelInterface.kt)
* **Description**: Defines standard functions for general event logging, recording non-fatal exceptions, and setting user metadata properties.

### Firebase Analytics Logger
* **File Path**: [FirebaseLogViewModel.kt](file:///c:/Users/juli2/StudioProjects/uniandesSports-Kotlin/app/src/main/java/com/uniandes/sport/viewmodels/log/FirebaseLogViewModel.kt)
* **Behavior**:
  1. Calls `Log.d("TELEMETRY_LOG", ...)` so developers can see telemetry events instantly in Android Studio Logcat.
  2. Wraps and invokes the `FirebaseAnalytics` SDK's `logEvent` API.
  3. Propagates all custom parameter maps dynamically.
  4. Records custom crash keys and exceptions to `FirebaseCrashlytics`.

---

## 3. Coach Comparison Analytics Events

### Event: `COACHES_COMPARED`
Triggered immediately when the user confirms their selection of 2 or 3 coaches to compare side-by-side.

* **Trigger Location**: [ProfesoresScreen.kt](file:///c:/Users/juli2/StudioProjects/uniandesSports-Kotlin/app/src/main/java/com/uniandes/sport/ui/screens/tabs/ProfesoresScreen.kt)
* **Payload Structure**:

| Parameter Key | Type | Description | Example Value |
| :--- | :--- | :--- | :--- |
| `screen_name` | String | Origin screen where event took place | `"ProfesoresScreen"` |
| `coach_ids` | String | Comma-separated list of compared coach IDs | `"1VdTcDvTF30MHrx3ZCMN,bRUffB5xBlcNbBamg8hb"` |
| `coach_count` | String | Number of coaches compared | `"2"` |
| `compared_names` | String | Readably formatted string of the compared coach names | `"Michael Torres vs Juan Felipe Hernández"` |

* **Code Implementation**:
```kotlin
logViewModel.log(
    screen = "ProfesoresScreen",
    action = "COACHES_COMPARED",
    params = mapOf(
        "coach_ids" to coachIdsJoined,
        "coach_count" to selectedCoachesForComparison.size.toString(),
        "compared_names" to selectedCoachesForComparison.map { id ->
            filteredProfesores.find { it.id == id }?.nombre ?: id
        }.joinToString(" vs ")
    )
)
```

---

## 4. Verification & Testing

### Console Output (Logcat)
To verify that the event telemetry payload matches the specification without needing full access to the Firebase console, filter your Logcat tags by `TELEMETRY_LOG`:

```text
2026-05-26 21:51:16.001 D/TELEMETRY_LOG: Logging Event: COACHES_COMPARED | Screen: ProfesoresScreen | Params: {coach_ids=1VdTcDvTF30MHrx3ZCMN,bRUffB5xBlcNbBamg8hb8Q0aGSD3, coach_count=2, compared_names=Michael Torres vs Juan Felipe Hernández}
```

### Firebase debugview
To inspect real-time transmission of the event:
1. Connect the test device or emulator.
2. Enable Debug mode on the device:
   ```bash
   adb shell setprop debug.firebase.analytics.app com.uniandes.sport
   ```
3. Open **Firebase Console** $\to$ **Analytics** $\to$ **DebugView** to observe events coming in live.
