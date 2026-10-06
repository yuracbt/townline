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
- **Discover screen**: your Facebook groups (one-tap links — Meta doesn't
  allow automatic group scanning, so add your groups here yourself) plus
  local event pages: Airdrie Chamber business events, City of Airdrie
  community calendar, Airdrie Public Library programs & bookings, Eventbrite
  Airdrie
- **Categories & filters**: every story is auto-categorized (News, Community,
  Events, Business, Calgary, Facebook) — tap a chip to filter the line
- **Share to TownLine**: from the Facebook app, Share → TownLine on any group
  post and it lands in your line under Facebook, with a notification.
  Saved posts are listed under Discover → My Facebook groups → Saved posts,
  where you can open or remove them.
  (Meta removed the Groups API entirely in April 2024, so no app — this one
  included — can log in and pull group discussions automatically.)

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
