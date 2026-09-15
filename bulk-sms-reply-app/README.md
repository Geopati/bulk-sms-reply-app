# Bulk Text Reply (MVP)

An Android app that lists your text-message conversations, lets you
multi-select several at once, and then apply a bulk action: send the same
reply (e.g. "STOP"), hide the numbers within this app, and/or forward the
message to the carrier spam short code (7726). Built for Mr. George's own
use as a first step toward the bigger goal of a publishable app.

## What this version does and does not do

- **Reads** your SMS inbox (`READ_SMS` permission) and groups it by sender.
- **Sends** real text messages on your behalf (`SEND_SMS` permission) when
  you choose a bulk reply, or choose "report as spam."
- **Does not** touch your regular Messages app, and does not delete
  anything from it.
- **"Hide"/"block" is app-only.** Because this app is not (yet) set as
  your phone's *default* SMS app, Android does not allow it to add numbers
  to the phone-wide block list - only the default SMS or Phone app can do
  that. So today, blocking a number here just stops that number from
  showing up inside *this* app; the carrier will still deliver the text,
  and it will still appear in your normal Messages app. Turning this into
  a true default-SMS-app replacement is the natural next step if you want
  real, phone-wide blocking - see "Where this goes next" below.
- **Spam reporting** forwards the selected message's text to 7726, which
  is the short code US carriers use for spam-text reports. Carriers act on
  patterns across many reports, not on any single one, so this is a
  long-game tool, not an instant fix.

## Building and installing it (for personal/sideloaded use)

You'll need [Android Studio](https://developer.android.com/studio)
(free) on a Windows/Mac/Linux computer:

1. Install Android Studio and open this folder (`bulk-sms-reply-app`) as
   a project. It will download the Android SDK pieces it needs the first
   time - this can take a while on the first run.
2. Connect your Android phone by USB and turn on **Developer options ->
   USB debugging** (search "how to enable USB debugging on Android" if
   you don't see Developer options - it's normally hidden until you tap
   the Build Number 7 times in About Phone).
3. In Android Studio, press the green "Run" (▶) button with your phone
   selected as the target device. It builds the app and installs it
   directly - no separate APK file needed.
4. The first time you open the app, Android will ask you to grant SMS
   permissions - allow both.

We can also set up a GitHub Actions pipeline that builds an installable
APK file automatically, if you'd rather not install Android Studio -
ask and we'll walk through it together.

## Where this goes next

If the goal becomes a public release (Play Store), Google's policy on the
SMS/Call Log permissions means this app would need to become a full
default SMS app (like Textra or Pulse SMS) with the bulk-select-and-reply
feature built in, rather than a lightweight add-on. That's a bigger
project than this MVP, tracked separately in the project notes.

## Project layout

- `app/src/main/java/com/georgeapp/bulksmsreply/` - Kotlin source
  - `MainActivity.kt` - permission handling and screen wiring
  - `SmsRepository.kt` - reads the SMS inbox into conversations
  - `SendHelper.kt` - sends the bulk reply / spam-report messages
  - `BlocklistStore.kt` - app-local "hide this number" list
  - `PhoneNumbers.kt` - loose phone-number matching helper
  - `ui/` - Compose screens (`ConversationListScreen.kt`, `Theme.kt`)
- `app/src/main/res/` - app icon, strings, theme
