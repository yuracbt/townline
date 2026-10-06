# TownLine — your town's news line

An Android app that scans local news sources on a schedule and keeps a
constantly refreshed news line. When new stories appear, you get a
notification.

## What it does

- Scans every **4 hours** (configurable: 1h – 24h) in the background via
  Android's JobScheduler — survives reboots, respects battery rules
- Built-in sources (all public, no login needed):
  - DiscoverAirdrie — Local News & Community (RSS)
  - Google News search for your town (defaults to Airdrie, AB — change it in onboarding)
  - Google News — Calgary
  - Calgary Herald (RSS)
- Detects **new** stories since the last scan, stores them locally (SQLite),
  and posts a notification with the new headlines
- Tap a story to read it in the built-in reader; mark stories read as you go
- Add any public RSS/Atom feed URL of your own in Settings
## Features

- Set your town once — the app builds a local news line for it
- **Suggested feeds**: the app checks a curated directory of local-news RSS
  feeds against your town, verifies each one actually works, and lets you
  add them with one tap (Links → Suggested feeds)
- Background scans every 4 hours (1/2/4/8/12/24h in Settings), re-scheduled
  after reboot; notification when new stories arrive
- **Categories & filters**: every story is auto-categorized (News, Community,
  Events, Business, Calgary, Saved) — tap a chip to filter the line
- **Save to TownLine**: from any app, Share → TownLine and the link lands in
  your line under Saved, with a notification. Saved links are listed under
  Links, where you can open or remove them.
- **Discover screen**: local event pages (Airdrie Chamber business events,
  City of Airdrie community calendar, Airdrie Public Library programs &
  bookings, Eventbrite) — add your own
- In-app reader, unread markers, custom RSS/Atom feeds, enable/disable/delete
  sources

Note: Meta removed the Facebook Groups API entirely in April 2024, so no app
can log in and pull group discussions automatically — that's why TownLine
doesn't have Facebook login.

## First run

The app asks for your town (drives the Google News query) and for
notification permission. **No accounts or logins are needed** — every
built-in source is public.

> Facebook groups can't be scanned automatically: Meta doesn't offer
> group-content access to third-party apps, and scraping facebook.com
> requires a login and gets blocked. If a group or page publishes a public
> RSS/Atom feed, paste its URL into Settings → Add feed.

## Tech notes

- 100% framework APIs, zero dependencies: `HttpURLConnection`,
  `XmlPullParser`, `SQLiteOpenHelper`, `JobScheduler`, `Notification.Builder`
- `minSdk 26`, `targetSdk 34`
- Notifications are local (no server, no Firebase) — they fire when the
  scheduled scan finds new items

## Build

Manual build, no Gradle:

```sh
./build.sh   # needs Android SDK 34 at ~/android-sdk and JDK 17 at ~/jdk17
```

Output: `build-manual/townline.apk` (signed).

## Download

Get the latest APK from the
[releases page](https://github.com/yuracbt/townline/releases).
