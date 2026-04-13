# Alarmy — Android Alarm App with Memory Pattern Dismiss Challenge

A reliable Android alarm app built with Kotlin, MVVM architecture, and Room Database. Alarms cannot be dismissed by simply tapping a button — users must complete a memory pattern game on a 3x3 grid.

## Features

- **Multiple alarms** with create/edit/delete support
- **Repeat days** (Mon–Sun toggle), labels, AM/PM and 24-hour format
- **Custom ringtone** selection and vibration toggle
- **Snooze** with configurable delay (5/10/15 min or disabled)
- **Persistent notification** showing next upcoming alarm
- **Memory Pattern Dismiss Challenge** — 3x3 grid game with Easy/Medium/Hard difficulty
- **Dark theme** by default
- **Survives Doze, battery saver, app kill, and device restart**

## Setup

1. Open the project in **Android Studio Hedgehog (2023.1.1)** or later
2. Sync Gradle — dependencies download automatically
3. Connect a device or emulator (min SDK 26 / Android 8.0)
4. Run the app

## Permissions Explained

| Permission | Why |
|---|---|
| `SCHEDULE_EXACT_ALARM` / `USE_EXACT_ALARM` | Schedule alarms at exact times |
| `USE_FULL_SCREEN_INTENT` | Show alarm over lock screen |
| `WAKE_LOCK` | Keep CPU awake during alarm |
| `RECEIVE_BOOT_COMPLETED` | Re-register alarms after reboot |
| `DISABLE_KEYGUARD` | Dismiss lock screen for alarm display |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevent system from killing alarm scheduling |
| `FOREGROUND_SERVICE` + `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Keep alarm sound playing reliably |
| `POST_NOTIFICATIONS` | Show alarm and next-alarm notifications (Android 13+) |
| `VIBRATE` | Vibrate on alarm |
| **Device Admin** | Prevents force-stop of the app |

On first launch, the app prompts for all critical permissions. A warning banner appears on the home screen if any are missing.

## Architecture

```
com.alarmapp.alarmy/
├── AlarmyApp.kt                  # Application class (notification channels)
├── data/
│   ├── Alarm.kt                  # Room Entity
│   ├── AlarmDao.kt               # Data Access Object
│   ├── AlarmDatabase.kt          # Room Database singleton
│   ├── AlarmRepository.kt        # Repository pattern
│   └── AlarmViewModel.kt         # MVVM ViewModel
├── game/
│   └── MemoryGameView.kt         # Custom View — 3x3 memory pattern game
├── receiver/
│   ├── AlarmReceiver.kt          # BroadcastReceiver — triggers alarm
│   ├── BootReceiver.kt           # Reschedules alarms after reboot
│   └── AlarmDeviceAdminReceiver.kt
├── service/
│   └── AlarmService.kt           # Foreground service — plays sound/vibration
├── ui/
│   ├── MainActivity.kt           # Alarm list + permission management
│   ├── AddEditAlarmActivity.kt   # Create/edit alarm screen
│   ├── AlarmActivity.kt          # Full-screen alarm + memory game
│   └── AlarmAdapter.kt           # RecyclerView adapter
└── util/
    ├── AlarmScheduler.kt         # AlarmManager scheduling logic
    └── PermissionHelper.kt       # Permission checking utilities
```

## How Alarm Reliability Works

1. **`setAlarmClock()`** — highest priority AlarmManager API; survives Doze mode
2. **Foreground Service** — plays sound via `STREAM_ALARM` at max volume, keeps playback alive
3. **Boot Receiver** — re-registers all enabled alarms on device restart
4. **Battery optimization exemption** — prevents system from limiting the app
5. **Device Admin** — prevents user from force-stopping the app
6. **Window flags** — `FLAG_SHOW_WHEN_LOCKED`, `FLAG_TURN_SCREEN_ON`, etc. ensure the alarm shows over lock screen

## Testing on Emulator vs Real Device

### Emulator
- Exact alarms work but Doze testing requires `adb` commands:
  ```
  adb shell dumpsys deviceidle force-idle
  adb shell dumpsys deviceidle unforce
  ```
- Ringtone picker may show limited options
- Device Admin works but has no real effect on emulator

### Real Device
- Grant all permissions on first launch for best results
- Test with screen off, app killed, and after reboot
- Some OEMs (Xiaomi, Huawei, Samsung) have extra battery restrictions — the battery optimization exemption dialog handles most cases
- To test alarm sounds at full volume, ensure the alarm stream isn't muted

## Memory Pattern Game

The dismiss challenge shows a 3x3 grid. Blocks highlight one by one in a random sequence. The user must tap them in the exact same order.

- **Easy**: 3 blocks in sequence
- **Medium**: 5 blocks in sequence  
- **Hard**: 7 blocks in sequence

Wrong taps trigger a shake animation, reset the sequence with a new random pattern, and increment the attempt counter. The alarm keeps ringing until the pattern is completed correctly.

## Build

```bash
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`
