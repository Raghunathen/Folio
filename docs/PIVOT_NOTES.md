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

### Scanning (not yet implemented)
SAF folder picker over the Audiobooks root → walk `Author/Book/` two levels deep → build
Author/Book/BookPart rows, read tags via Media3's `MetadataRetriever` (or direct ID3/M4B
parsing already present in `FolioExtractorsFactory`/tag utils), look for sidecar metadata files,
generate placeholder covers when none found.

### Playback (not yet implemented)
Adapt `FolioPlaybackService` (was `GramophonePlaybackService`) to build a single continuous
`ConcatenatingMediaSource`-equivalent per book from its parts, track absolute position across
parts, expose chapter seeking, add sleep timer/skip-silence/speed persistence per book.

## 6. Progress log

- [x] Explored original architecture (Gramophone/Media3/Room/MediaStore).
- [x] Renamed package/app to Folio; verified `gradle :app:compileDebugKotlin` succeeds.
- [x] New Room schema (Author/Book/BookPart/Chapter/Bookmark/PlaybackProgress/Collection) added
      additively; build verified.
- [ ] SAF-based Audiobooks folder scanner. ← **next**
- [ ] Playback service rewrite (continuous multi-part books, chapters, speed, sleep timer).
- [ ] Remove music-only features (Genres, Lyrics, Dates-added).
- [ ] New UI (Home shelves, Author/Book library, Book detail/player, Collections).
- [ ] Bookmarks/sleep timer/series-detection/finished-state UI.
- [ ] Home screen widget.
- [ ] New app icon.
- [ ] Full build + on-device smoke test.
