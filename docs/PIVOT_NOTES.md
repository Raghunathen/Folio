# Folio — Pivot Notes (Gramophone music player → audiobook player)

This file is the living log of the Gramophone → Folio pivot: what was decided, what changed,
and how to build/run things. Update it as work progresses so anyone (including future-us) can
pick this up without re-deriving context.

## 1. Origin

- Upstream project was **Gramophone** (`org.akanework.gramophone`), itself forked from a project
  called "Accord". Media3/ExoPlayer-based music player, MediaStore scanning, Room DB for
  playlists/listening stats, FFmpeg decoder AAR for extra codecs.
- GPLv3 licensed. Renaming the package/app is fine under GPLv3 as long as the LICENSE and
  copyright/attribution notices for original authors (Akane Foundation) are preserved.

## 2. Rebrand

- New name: **Folio**. Package/namespace: `com.raghu.folio`. `applicationId`: `com.raghu.folio`.
- `rootProject.name` = `Folio` (was `Accord`).
- Version reset to `versionCode=1`, `versionName=0.1.0` (fresh app, no users yet).
- Git repo initialized locally (there was none before). Remote `origin` =
  `https://github.com/Raghunathen/Folio.git` (empty repo). **Do not push without explicit
  confirmation** — nothing has been pushed as of writing.
- Mechanical rename: moved `app/src/main/java/org/akanework/gramophone` →
  `app/src/main/java/com/raghu/folio`, renamed `Gramophone*`-prefixed files/classes to `Folio*`
  (e.g. `GramophonePlaybackService` → `FolioPlaybackService`), then bulk-replaced
  `org.akanework.gramophone` / `Gramophone` / `gramophone` tokens repo-wide via `sed`.

## 3. Product decisions (locked in with user)

- **Folder structure**: strictly `Audiobooks/<Author>/<BookTitle>/` — audio files (mp3/m4b/etc.)
  live directly inside the book folder, split into parts or as a single file. No "Series"
  subfolder level.
- **Scanning**: SAF folder picker (`ACTION_OPEN_DOCUMENT_TREE`) on the Audiobooks root — **not**
  MediaStore. MediaStore doesn't reliably index arbitrary folder trees or preserve the
  Author/Book hierarchy we need.
- **Multi-file books**: parts play back-to-back as ONE continuous book with a unified
  timeline/progress bar, but remain chapter-jumpable.
- **Metadata sources**: folder names (Author, Title) are primary; embedded ID3/M4B tags fill
  gaps (narrator, series, description, cover); also support sidecar files
  (`metadata.json`/`.opf`/`desc.txt`/`cover.jpg`) Audiobookshelf-style.
- **M4B chapters**: parse embedded chapter markers and expose chapter navigation.
- **Series**: no folder level — auto-detect from filename/title numeric patterns within an
  Author's books (e.g. "01 - ...", "02 - ...") and group/order accordingly.
- **Finished state**: auto-detect near the end of a book (~99% or last 30s), then show a
  dismissible confirm prompt ("Finished?") rather than silently marking it; auto-confirms if the
  user leaves the player without responding either way.
- **Cover art fallback**: generated placeholder (title initials on a gradient/solid color),
  Apple Books style.
- **Custom "Playlist" system** → repurposed as user **Collections** (e.g. "Currently Listening",
  "Favorites").
- Music-only features (**Genres tab, LRC lyrics, "Dates added" grouping**) are being removed
  entirely — not relevant to audiobooks.
- Min SDK stays **31**.
- **UI direction**: Apple Books look — light theme, cover-grid "shelves" as the home screen
  (Continue Listening / Recently Added / All Books), plus an Author → Book drilldown library tab.
- **Widget**: Apple Books/Podcasts style — cover, title/author, progress bar, play/pause + skip,
  resizable.
- App icon: will be replaced with a simple new book-themed vector icon (not done yet).

### v1 feature list (confirmed)
Variable playback speed per book (0.5x–3x, remembered per book) · Sleep timer (fade-out,
shake/tap-to-extend, end-of-chapter option) · Bookmarks (named timestamps) · Skip silence /
volume boost · Custom skip intervals (10/15/30/45s, configurable) · Auto-continue to next book in
series · Chapter list UI · Resume position saved continuously (survives force-close/reboot) ·
Home screen widget · Listening stats (time listened, streaks).

**Explicitly out of scope for v1**: Android Auto, Cast, backup/restore export file.

## 4. Local build environment (this machine had none of this installed)

No JDK, no Android Studio/SDK, and `./gradlew`'s own distribution download times out in this
sandbox's network (only some hosts are reachable through the local proxy). Everything below was
installed via Homebrew instead, and **you must use the exported env vars / explicit gradle
binary shown below — do NOT use `./gradlew`.**

```bash
# one-time installs
brew install openjdk@17
brew install gradle@8                       # AGP 8.9.0 needs Gradle 8.x, brew's default `gradle` is 9.x
brew install --cask android-commandlinetools
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
yes | sdkmanager --sdk_root="$ANDROID_HOME" --licenses
sdkmanager --sdk_root="$ANDROID_HOME" "platform-tools" "platforms;android-36" "build-tools;36.0.0"
echo "sdk.dir=$ANDROID_HOME" > local.properties   # gitignored
```

Also had to create `package.properties` at repo root (referenced by `app/build.gradle.kts` but
not present/committed upstream): `releaseType=Normal`.

**Command to compile/verify from now on:**
```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@17
export PATH="/opt/homebrew/opt/gradle@8/bin:$JAVA_HOME/bin:$PATH"
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
cd /Users/raghu-25596/Desktop/projects/OrangeBooks
gradle :app:compileDebugKotlin -q
```

No emulator is set up or needed — the user tests on a physical phone.

## 5. Architecture plan

### Data layer (Room DB — replacing the MediaStore-backed `MediaItem`/`Playlist` tables)

New entities (package `com.raghu.folio.logic.data.db.entity`):
- **Author** — id, name, sortName, imageUri (SAF/cached), createdAt.
- **Book** — id, authorId (FK), title, sortTitle, narrator, seriesName, seriesIndex, description,
  coverUri, folderUri (SAF tree/document URI as string), durationMs (aggregate across parts),
  dateAdded, dateModified.
- **BookPart** — id, bookId (FK), fileUri (SAF content URI string), partIndex, title, durationMs,
  startOffsetMs (cumulative offset within the book's overall timeline) — this is what makes
  multi-file books playable as one continuous timeline.
- **Chapter** — id, bookId (FK), partId (nullable, for per-file embedded chapters), title,
  startMs/endMs in book-overall-timeline coordinates.
- **PlaybackProgress** — bookId (PK/FK), positionMs, currentPartId, playbackSpeed, lastPlayedAt,
  isFinished, finishedAt.
- **Bookmark** — id, bookId (FK), positionMs, label, createdAt.
- **Collection** (replaces `Playlist`) — id, name, coverUri, createdAt.
- **CollectionBookCrossRef** (replaces `PlaylistMediaItemCrossRef`) — many-to-many.
- **ListeningStat** — kept, same (song→book) shape: one row per (book, day), `msPlayed`
  accumulates.

`MediaItem`, `Playlist`, `PlaylistMediaItemCrossRef`, `MediaItemWithPlaylist`,
`PlaylistWithMediaItem` are being removed since they were MediaStore-id-based and don't fit the
SAF-URI-based book model.

> **Status**: implemented additively — the new tables were added to `AppDatabase` (version 2 →
> 3, `fallbackToDestructiveMigration(true)` since there are no real installs yet) *alongside* the
> still-present `MediaItem`/`Playlist`/`PlaylistMediaItemCrossRef` tables, so the project keeps
> compiling right now. Those old tables/DAOs will be deleted together with the old music UI in
> the "remove music-only features" + "new UI" steps, once nothing references them anymore.
> Verified with `gradle :app:compileDebugKotlin` (KSP/Room schema validation passes).

### Scanning

Implemented in `com.raghu.folio.logic.utils.audiobook`:
- **`AudiobookLibraryPrefs`** — persists the user-picked Audiobooks root tree `Uri` (call
  `setRootUri` after an `ACTION_OPEN_DOCUMENT_TREE` result; it takes the persistable permission
  grant too). No picker UI wired up yet — that's part of the UI rewrite step.
- **`AudiobookScanner.scanLibrary(context)`** — the main entry point (suspend, runs on
  `Dispatchers.IO`). Opens the root via `DocumentFile.fromTreeUri`, walks exactly two levels
  (`Author/` → `Book/`), and for each book folder: lists audio files (mp3/m4a/m4b/aac/flac/ogg/
  opus/wav/wma) sorted with the existing `AlphaNumericComparator` (so `part2` sorts before
  `part10`), reads each file's duration via `MediaMetadataRetriever`, and builds `BookPart` rows
  with cumulative `startOffsetMs`. Upserts `Author`/`Book` rows keyed by their (stable) folder
  `Uri` so re-scans update in place, and removes authors/books no longer present on disk.
  **Known v1 limitation**: `BookPart` rows are fully deleted+reinserted on every scan rather than
  diffed, so a part's id isn't stable across rescans of an in-progress book — fine for now since
  scans only happen on explicit user action, revisit once background rescans exist.
- **`SeriesDetector`** — regex-based heuristic over sibling book folder names *within one
  author* (e.g. `"Mistborn 1 - The Final Empire"`, `"Mistborn, Book 2: ..."`, `"01 - Title"`).
  A series is only assigned if ≥2 sibling books share the same detected series text — a lone
  book whose title happens to contain a number is left alone. Extend/replace this heuristic if
  it proves too imprecise once tested against real libraries.
- **`SidecarMetadataReader`** — looks in the book folder for `cover.jpg/png/webp`/`folder.*` art,
  `metadata.json` (Audiobookshelf-style keys: title/author/narrator/description/series/
  seriesIndex), a Calibre-style `*.opf` package XML (title/creator role="aut"|"nrt"/description/
  `calibre:series(_index)` meta), and `desc.txt`/`description.txt` as a last-resort description.
  Sidecar data always wins over folder-name-derived data when present.
- **Not yet implemented**: real M4B/embedded chapter parsing (currently every book gets zero
  chapters from `ChapterDao`, which is still fully playable/seekable via the progress bar - just
  no chapter list yet); placeholder cover generation for books with no art (planned: initials on
  a gradient, generated at UI-display time rather than stored as a file).

### Playback

Core logic implemented in `com.raghu.folio.logic.utils.audiobook`, now wired into a real
service (see below):
- **`AudiobookTimeline`** — pure functions mapping between a book's overall (cross-part)
  position and (partIndex, positionInPart), plus chapter-at-position lookup. `BookPart` list is
  the source of truth for offsets (`startOffsetMs`/`durationMs`), computed once by the scanner.
- **`AudiobookPlayerController`** — wraps a single `ExoPlayer`: loads a book's parts as a
  playlist (`player.setMediaItems(...)`, one `MediaItem` per part file, played back-to-back by
  ExoPlayer natively - no manual concatenation needed), exposes `seekToAbsoluteMs`/
  `skipForward`/`skipBackward`/`seekToChapter`/`setPlaybackSpeed`/`setSkipSilenceEnabled`
  (native `ExoPlayer.skipSilenceEnabled`) and a coroutine-based sleep timer
  (`startSleepTimer`/`cancelSleepTimer`). Persists `PlaybackProgress` (position, current part,
  speed) every 5s while playing and immediately on pause/speed-change/release.

> **Update**: now wired into a real service - **`AudiobookPlaybackService`**
> (`com.raghu.folio.logic`), a `MediaSessionService` built fresh around `AudiobookPlayerController`
> (ExoPlayer with `FolioRenderFactory`/`FolioMediaSourceFactory` reused for FFmpeg/codec support,
> `AudioAttributes` content type `SPEECH` rather than `MUSIC` since that suits narrated audio
> better). Exposes custom session commands: `audiobook_load_book`, `_skip_forward`,
> `_skip_backward`, `_set_speed`, `_seek_chapter`, `_sleep_timer_start`/`_cancel`,
> `_set_skip_silence`. Registered in `AndroidManifest.xml` **alongside** the old
> `FolioPlaybackService` - it's inert until UI binds to it (none does yet). Verified with a full
> `gradle :app:assembleDebug` (not just Kotlin compile, since this touches the manifest).
>
> The old `FolioPlaybackService` (was `GramophonePlaybackService`) is left alone for now since
> it's deeply intertwined with the still-present music UI (`PlayerBottomSheet`, `FullBottomSheet`,
> `MainActivity`, queue/session commands, notification) - deleting it would break that UI's
> compilation before the new UI exists to replace it. Both services + both UIs will be swapped
> over together as part of the "new UI" step.

## 6. Progress log

- [x] Explored original architecture (Gramophone/Media3/Room/MediaStore).
- [x] Renamed package/app to Folio; verified `gradle :app:compileDebugKotlin` succeeds.
- [x] New Room schema (Author/Book/BookPart/Chapter/Bookmark/PlaybackProgress/Collection) added
      additively; build verified.
- [x] SAF-based Audiobooks folder scanner engine (`AudiobookScanner`/`SeriesDetector`/
      `SidecarMetadataReader`/`AudiobookLibraryPrefs`); build verified. UI trigger (folder picker
      screen) still needed - lands with the UI rewrite step.
- [x] Playback core (continuous multi-part timeline, chapters, speed, sleep timer, skip
      silence) as a standalone controller; build verified.
- [x] `AudiobookPlaybackService` (new `MediaSessionService`) wired around the playback core,
      registered alongside the old `FolioPlaybackService`; full `assembleDebug` verified.
- [x] Removed all music-only UI (fragments/adapters/components), old `FolioPlaybackService`,
      old DB entities/DAOs (`MediaItem`/`Playlist`/`ListeningStat`), and their manifest entry.
      Rewrote `LibraryViewModel`/`MainActivity` for the audiobook data model, and added a
      minimal placeholder `HomeFragment` (SAF folder picker + flat scanned-book list, tapping a
      book sends `COMMAND_LOAD_BOOK` to `AudiobookPlaybackService`). `gradle :app:compileDebugKotlin`
      and `gradle :app:assembleDebug` both pass again.
- [x] Home screen upgraded to a sectioned list: "Continue Listening" shelf + one section per
      author, each row showing a progress bar (`item_home_header.xml` added).
- [x] Shared `PlayerViewModel` (activity-scoped, single `MediaController` connection), mini-player
      bar (`activity_main.xml`), and `PlayerSheetFragment` bottom sheet combining book detail +
      full transport controls (play/pause, skip -15/+30, seekbar scrubbing via new
      `COMMAND_SEEK_ABSOLUTE`, speed cycling, sleep timer dialog, add-bookmark dialog, chapters
      list with tap-to-seek). `HomeFragment` now delegates playback to
      `MainActivity.playBook(bookId)` instead of building its own throwaway `MediaController`.
- [x] Finished-state detection: `AudiobookPlayerController` marks `PlaybackProgress.isFinished`
      on `Player.STATE_ENDED`; `MainActivity` shows a "Finished!" confirmation dialog and
      refreshes the library so the book drops out of Continue Listening.
- [x] M4B embedded chapter parsing: `M4bChapterParser.kt` parses Nero-style `chpl` atoms
      (moov/udta/chpl, as written by `m4b-tool`) for single-file `.m4a`/`.m4b` books. QuickTime
      "chap" text-track chapters are NOT supported. Not yet verified against a real device file.
- [x] Cover art: `CoverPlaceholderGenerator` (Apple-Books-style initials tile) used whenever a
      book has no `coverUri`; Coil3 (`ImageView.load`) used to load real covers in the Home list
      and the player sheet. Player sheet also shows the book description when present.
- [x] Settings: new "Audiobook Playback" category in `settings_audio.xml` with
      `skip_forward_seconds`/`skip_backward_seconds` `SeekBarPreference`s (read by
      `PlayerViewModel.skipForward()/skipBackward()` as their defaults); the pre-existing
      `skip_silence` toggle is now actually applied — `AudiobookPlaybackService.loadBook()` reads
      it and calls `controller.setSkipSilenceEnabled()` right after loading. Known limitation:
      toggling `skip_silence` while a book is already playing only takes effect on the next
      load, not live. **Volume boost (LoudnessEnhancer) was deliberately deferred** — wiring it
      correctly requires tracking ExoPlayer's audio session id, which isn't stably available
      until the audio renderer is enabled, and this can't be verified without a physical device.
- [x] New book-themed app icon: replaced the old vinyl-record raster foreground with a vector
      book glyph (`ic_launcher_foreground.xml`, white cover + indigo spine/bookmark notch) on an
      indigo (`#3F51B5`) adaptive-icon background.
- [x] Home screen widget (`com.raghu.folio.widget.AudiobookWidgetProvider`): cover, title/author,
      progress bar, play/pause + skip ±seconds buttons. State is pushed from
      `AudiobookPlaybackService` into a small `WidgetStateStore` (SharedPreferences) on
      `onIsPlayingChanged`/`onMediaMetadataChanged`/book load, plus a 20s periodic tick while
      playing (so the progress bar drifts along without per-second updates/battery cost); button
      taps open a short-lived `MediaController` connection to send the command and release.
- [ ] New UI polish (Author/Book library grid, Collections screen) — Home screen is still a
      sectioned list of text rows, not a cover grid. ← **next candidate, not started**
- [ ] Listening stats UI.
- [ ] Full build + on-device smoke test (including verifying M4B chapter parsing and the new
      widget against real audiobook files/devices) — **cannot be done by the agent; needs the
      user's physical device**.

