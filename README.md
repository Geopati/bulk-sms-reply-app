# Bulk SMS Reply Log (MVP)

An Android app that lists your text-message conversations, lets you
multi-select several at once, and then apply a bulk action: send the same
reply (e.g. "STOP"), hide the numbers within this app, and/or forward the
message to the carrier spam short code (7726). It also keeps a running,
searchable log of every message and reports of who (or on whose behalf)
they were sent. Built for Mr. George's own use as a first step toward the
bigger goal of a publishable app.

> Folder/deliverable name: `bulk-sms-reply-log-app` (renamed from the
> original `bulk-sms-reply-app` to reflect the new log/reports features -
> see the note near the bottom of this file for what did and didn't change).

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
- **"Mark as read" is app-only, the same way "block" is.** After you apply
  a bulk action (reply/block/report) to a conversation, this app marks it
  "read" in its own log so it stops showing an UNREAD tag here. It does
  **not** mark it read in your native Messages app. Android only lets the
  phone's *default* texting app write to the shared SMS inbox that the
  native app reads from, and (same as with blocking) this app deliberately
  isn't set as that default - see "About mark-as-read" below for why, and
  what it would take to change that.

## The four tabs

- **Messages** - the conversation list and bulk-action screen.
  - Every row shows a **`RE: [label]`** headline (see "RE: labels" below)
    with the raw phone number underneath.
  - An **"Unread only"** chip filters the list down to conversations this
    app hasn't marked read yet.
  - **"Select unread"** (next to "Select all") selects every still-unread
    conversation in one tap, so you don't have to check boxes by hand.
  - Once you apply a bulk action to a conversation, it moves out of this
    tab entirely and into **S.R.B.** (see below) - it can no longer be
    accidentally selected and re-processed a second time.
- **S.R.B.** (Stop / Report / Block) - new in Round 5. Every conversation
  you've already applied a bulk action to lives here instead of in
  Messages, each showing which action(s) were taken (Replied / Reported /
  Blocked) as checkmarked badges, plus the message that triggered it. This
  directly fixes the mix-up where a processed conversation could still
  show up in Messages and get STOPped or reported a second time. If a
  number you've already processed texts you again, that new message makes
  the conversation "unprocessed" again and it reappears back in Messages,
  since it's genuinely a new, unhandled message.
- **Log** - a running, searchable record of every incoming text the app
  has seen: when it arrived, who it was from, and its `RE:` label. Search
  by sender, label, or message text, and filter by 7/30/90 days, 1 year,
  or all time. Whatever bulk action you take on a conversation (reply,
  block, report) is recorded against its log entry too. An entry shows an
  **Unread** tag only while it's actually unread - a processed/read entry
  shows nothing there now, instead of the old "Read (in this app)" text.
  An **Export CSV** button shares the currently-filtered log as a CSV file
  (via Android's normal share sheet - email it to yourself, save to
  Drive, whatever you prefer).
- **Reports** - the same log, broken down into day / week / month /
  quarter (3 months) / half-year (6 months) / year totals, each showing
  how many texts came in and from which senders (grouped by `RE:` label).
  **Tap a sender row** to expand it and see every one of their full
  messages, with any web links in the text shown as tappable links and
  the action taken on that message, if any. An **Export CSV** button
  exports the current breakdown, including the action taken per message
  rather than just the totals.

The log only tracks what's *already* on your phone plus anything new the
app sees each time you open it - it can't retroactively know about texts
you deleted before installing the app.

## RE: labels (Round 5)

Every conversation, log entry, and report row now shows a `RE: [label]`
headline instead of a bare "on behalf of" phrase or a phone number. It's
worked out in this order from the message text:

1. **A last name (or two)** found in the message's own disclosure line
   (e.g. "Paid for by Jane Smith" -> `RE: Smith`; two names ->
   `RE: Smith/Jones`).
2. **A business name**, if no personal name was found but the disclosure
   names a company (e.g. "Authorized by Acme Corp" -> `RE: Acme Corp`).
3. **A best guess of "Political" or "Commercial"**, if neither a name nor
   a business turned up, based on which of those two categories the
   message's own wording leans toward.

This is a plain keyword/pattern heuristic, not machine learning, so it
will sometimes mislabel or miss - it's meant as a quick at-a-glance sort,
not a certified attribution.

## Versioning

Starting with Round 5, the app's version number follows its build round:
Round *N* is version `1.N` (so Round 5 is v1.5, Round 6 will be v1.6, and
so on). The current version and a short note on what changed are shown in
the app itself via the (i) info icon on the Messages tab's top bar.

## Building and installing it (for personal/sideloaded use)

### Option A - GitHub builds the APK for you (no software to install)

This project already includes `.github/workflows/build.yml`, which tells
GitHub to build an installable APK automatically. Steps:

1. Create a free account at github.com (skip if you already have one).
2. Create a new repository (the "+" in the top right -> "New repository").
   Any name is fine, e.g. `bulk-sms-reply-log-app`. Public or private, your call.
3. On the new repo's page, choose "uploading an existing file," then drag
   this whole `bulk-sms-reply-log-app` folder (everything inside it) into the
   browser window, and commit/upload it. GitHub keeps the folder structure.
4. Click the "Actions" tab at the top of the repo. A build should start on
   its own within a minute or two (or click "Run workflow" if it doesn't).
5. Once it finishes (green check mark), click into that run and download
   the "bulk-text-reply-debug-apk" file under "Artifacts." It's a zip -
   unzip it to get `app-debug.apk`.
6. Get that APK onto your phone (email it to yourself, or Google Drive,
   or a USB cable), then tap it on the phone to install. Android will ask
   to allow installing from that source once - allow it just for that file.
7. Open the app and allow the two permission prompts it shows.

### Option B - build it yourself in Android Studio

You'll need [Android Studio](https://developer.android.com/studio)
(free) on a Windows/Mac/Linux computer:

1. Install Android Studio and open this folder (`bulk-sms-reply-log-app`) as
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

## About the rename to "bulk-sms-reply-log-app"

The folder/deliverable name and the name shown under the app's icon on
your phone ("Bulk SMS Reply Log") were both updated to reflect the new
log/reports features. On purpose, two things were **not** renamed, to
avoid introducing new build risk right before you push this version
through GitHub Actions:

- The Android package ID (`com.georgeapp.bulksmsreply`) - this is an
  internal identifier baked into every source file's folder path; renaming
  it is a bigger, riskier change than a name deserves on its own, and it
  has no effect on anything you see or type.
- Your existing GitHub repository name - if you'd like that renamed too,
  it's simple and safe: on the repo's page, go to **Settings -> repository
  name**, type the new name, and save. GitHub automatically redirects the
  old URL, so nothing breaks. Entirely your call, and not required.

## About mark-as-read

Mr. George asked for processed texts to be marked read - including in the
native Messages app. That last part isn't possible without a bigger
change: Android only allows the phone's *default* SMS/texting app to write
to the shared SMS inbox (which includes the "read" flag), the same rule
that already limits blocking to being app-only in this build. He was given
three options and chose the lightweight one for now:

1. **Make this app the phone's default texting app** - the only way to get
   real, everywhere read-syncing. A real pivot: Android would then deliver
   all incoming texts to this app instead of the native Messages app, so
   it would need to reliably receive and store them going forward, and the
   native app would stop getting new texts unless he switched back.
2. **Keep this app as an add-on; track "read" locally only** (chosen for
   now) - no change to how he texts day to day. Bulk-processing a
   conversation marks it read in this app's own Log/Reports and clears its
   UNREAD tag on the Messages tab; the native Messages app is unaffected.
3. **Switch default only for bulk-processing sessions** - a middle ground
   he can revisit later: briefly make this app the default before running
   a batch, then switch back to his regular Messages app afterward.

If he later wants real native-app syncing, options 1 or 3 are there to
revisit - they're a bigger build (implementing what Android requires of a
default SMS app), tracked as a possible future step rather than done now.

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
  - `MessageLogDatabase.kt` - the running SQLite log, including which
    conversations count as "processed" (S.R.B.)
  - `AttributionExtractor.kt` - the on-behalf-of / `RE:` label heuristics
  - `ReportGenerator.kt` - buckets the log into day/week/month/etc. reports
  - `CsvExporter.kt` - shares the Log/Reports tabs as CSV files
  - `AppInfo.kt` - the app version shown in the About dialog
  - `ui/` - Compose screens (`ConversationListScreen.kt`, `SrbScreen.kt`,
    `LogScreen.kt`, `ReportsScreen.kt`, `Theme.kt`)
- `app/src/main/res/` - app icon, strings, theme, and the FileProvider's
  `xml/file_paths.xml` (needed for CSV export/sharing)
