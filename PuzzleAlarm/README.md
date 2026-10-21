# Puzzle Alarm

An Android alarm clock that makes you prove you're awake. It works like the stock clock app
(set a time, pick repeat days, add a label), but to turn a ringing alarm off you have to solve a
puzzle that Claude generates fresh each time.

## How it behaves

- **Ringing** keeps going (sound + vibration, full screen over the lock screen) until you solve the
  puzzle. There's no auto-silence, and Back does nothing.
- **Snooze** is allowed **once** per alarm. Per alarm you can turn snooze off or set its length
  (1–30 min). When the snoozed alarm rings again, the only way out is the puzzle.
- **Puzzles** come from an AI using your own API key, pasted into Settings. Supported providers:
  Claude/Anthropic (default model `claude-opus-5-5`), OpenRouter (default `openrouter/auto`, or any
  OpenRouter model id), OpenAI (default `gpt-4o-mini`), or any other OpenAI-compatible server
  (set its base URL and model). The provider is picked automatically from the key prefix
  (`sk-ant-`, `sk-or-`, `sk-`) and can be changed by hand. Styles rotate: mental arithmetic, sequences, riddles, unscrambles, word
  problems, odd-one-out. Difficulty (Easy/Medium/Hard) and the number of puzzles to solve (1–3) are
  in Settings. Wrong twice and you get a hint; you can also ask for a different puzzle.
- **Offline fallback**: with no key, no network, or no reply within 30 s, a puzzle generated on the
  phone is used instead, so the alarm can always be dismissed. The first puzzle starts loading the
  moment the alarm rings.
- Alarms survive reboots and clock/time-zone changes.

## Build and install

CI builds a debug APK on every push that touches this folder: open the **Puzzle Alarm APK**
workflow run in the Actions tab and download the `PuzzleAlarm-debug-apk` artifact, then install it
on the phone (allow "install unknown apps").

Locally, with Android Studio or the Android SDK installed:

```bash
cd PuzzleAlarm
./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest
```

## Publishing

Google Play setup (signing key, store listing, privacy policy, Play Console declarations, CI
upload) is in [play/PUBLISHING.md](play/PUBLISHING.md).

## First run checklist

1. Allow notifications when asked.
2. Settings → paste your Anthropic, OpenRouter or OpenAI key → **Try a puzzle** to check it works.
3. Settings → Permissions: make sure "Full-screen alarm over lock screen" is allowed (Android 14+).
4. On Xiaomi/Oppo/Vivo/Samsung, set the app's battery usage to Unrestricted.
5. **Ring a test alarm now** to try the whole flow.

## Code map

| Path | What |
|---|---|
| `alarm/AlarmScheduler.kt` | Next-ring calculation and `AlarmManager.setAlarmClock` scheduling, including the snooze alarm |
| `alarm/AlarmReceiver.kt`, `BootReceiver.kt` | Fire the alarm; reschedule after reboot/time change |
| `alarm/AlarmService.kt` | Foreground service: sound, vibration, full-screen notification, snooze/dismiss |
| `puzzle/PuzzlePrompt.kt` | The puzzle prompt and reply parsing shared by all providers |
| `puzzle/ClaudePuzzleProvider.kt` | Claude request via the official Anthropic Java SDK |
| `puzzle/OpenAiCompatiblePuzzleProvider.kt` | OpenRouter / OpenAI / compatible chat-completions request |
| `puzzle/OfflinePuzzles.kt` | On-device puzzle generator used as the fallback |
| `ui/` | Compose screens: alarm list, editor, settings, ringing/puzzle |

The API key is stored in the app's private storage and is only sent to the provider you chose.
