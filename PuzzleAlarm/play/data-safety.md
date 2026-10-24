# Play Console answers

## Data safety form

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No**. The app has no backend. The only network traffic is a puzzle request from the user's phone straight to the AI provider the user configured with their own key, carrying no personal data. |
| Is all user data encrypted in transit? | Yes (HTTPS to the AI provider) |
| Do you provide a way for users to request that their data is deleted? | Data exists only on the device; clearing storage or uninstalling deletes it |

If Play review questions the API key, the defensible answer is still "not collected": the key is
user-supplied and sent only to the user's chosen provider, never to the developer. A conservative
alternative is to declare **App activity → Other user-generated content: collected, not shared,
optional, processed ephemerally, for app functionality**.

## App content declarations

| Item | Answer |
|---|---|
| Privacy policy URL | Public URL where `privacy-policy.md` is hosted (required) |
| Ads | No ads |
| App access | All features available without login. Note for reviewers: "No account needed. To see AI puzzles, enter any Anthropic, OpenRouter or OpenAI key in Settings; without a key the app uses on-device puzzles. Settings → 'Ring a test alarm now' shows the ringing screen." |
| Target audience | 13+ (or 18+ to skip family-policy review) |
| News app | No |
| Government app | No |
| Financial features | None |
| Health | No |

## Permission declarations

**Exact alarms (`USE_EXACT_ALARM`)**: Play allows this permission only for apps whose core
function is an alarm clock or calendar. Declare: "Puzzle Alarm is an alarm clock app. Its core
function is ringing user-set alarms at the exact chosen time, which requires exact alarms."

**Full-screen intent (`USE_FULL_SCREEN_INTENT`)**: on Android 14+ Play grants this by default only
to alarm and calling apps. Declare the use case "Alarm clock: shows the ringing alarm over the lock
screen."

**Foreground service type `mediaPlayback`**: Play Console asks for a description and a video link.
Description: "While an alarm is ringing, a foreground service plays the alarm sound until the user
solves a puzzle or snoozes. The user starts it by setting the alarm." Video: a short screen
recording of setting an alarm and it ringing (Settings → "Ring a test alarm now" makes this quick).
