# Experimental fork changes

This branch combines the playback work below with Next post navigation. It includes upstream v1.3.47, Media3 playback, sound-post synchronization, paused-video restoration, and the Samsung VP8 operating-rate experiment.

Experimental APKs install alongside Personal and upstream builds with separate app data. Updates are installed manually. See [experimental release instructions](docs/experimental-releases.md) for building and publishing.

## Development branch summaries

This catalog covers the fork's feature, fix, spike, and personal integration branches. Each entry states whether its changes are included here; older investigation branches contain alternatives that are not part of this release.

### `feature/654-video-end-behavior`

Adds Loop, AutoAdvance, and Stop choices for video completion in the media viewer, with support for both ExoPlayer and MPV. AutoAdvance moves to the next media item when a video ends. Existing loop preferences migrate to the new setting.

This branch keeps the completion feature separate from playback lifecycle fixes and the Media3 migration.

**Release integration:** Included in this release branch.

### `feature/media-viewer-next-post`

Adds a Next post button alongside the media controls while keeping swiping available. It appears with the bottom controls on phones and the side controls on tablets. It skips remaining attachments from the current post and opens the next post with media in the current filtered order.

The button has a 48 dp tap target and an accessibility label. It disables when no next post is available and does not wrap to the beginning.

On phones, the shared action row stays immediately above the system navigation area across images, GIFs, and videos. Video and sound-post playback controls appear above it, keeping Next post at the same tap position when the media type changes. Tablet side controls retain their existing placement.

**Release integration:** Included in this release branch.

### `fix/media-viewer-playback-lifecycle`

Pauses and stops hidden video and sound-post players even when they are buffering. It preserves the requested playback state and cancels stale preload and listener work to prevent audio from the previous page continuing after navigation.

Adjacent remote videos prefetch into the shared cache without preparing offscreen decoders. This branch retains the existing ExoPlayer API; the Media3 migration is separate.

**Release integration:** Included and adapted for Media3 in this release branch.

### `fix/vp8-decoder-fallback`

Tests synchronous MediaCodec queueing in the existing ExoPlayer backend and adds playback diagnostics for investigating VP8 stalls and dropped frames. The queueing change applies to this backend generally, not only to VP8 videos.

Despite the branch name, the current tip removes the earlier Samsung software-VP8 decoder preference and decoder fallback. This is an investigation branch, not a confirmed general fix.

**Release integration:** Separate investigation; this release branch does not include its synchronous-queueing diagnostic or the removed software-decoder preference.

### `personal/654-video-end-behavior`

Combines Loop, AutoAdvance, and Stop video completion choices with the MPV auto-advance completion fix. It includes Personal packaging so the APK can install alongside the upstream app with separate data.

This is the earlier personal build for the completion feature. Cumulative playback work continues on the integration branches.

**Release integration:** Its completion behavior is included. Release APKs use the Experimental package instead of the Personal package.

### `personal/media3-integration`

Combines the playback lifecycle fix, Loop/AutoAdvance/Stop completion controls, and the migration from standalone ExoPlayer to AndroidX Media3 1.10.1. Personal packaging allows device testing alongside the upstream app.

This is a cumulative test branch. The independent feature and fix branches retain their narrower scopes for review.

**Release integration:** Its combined playback changes are included. Release APKs use the Experimental package.

### `personal/media3-vp8-operating-rate`

Combines Media3 playback, playback lifecycle fixes, and Loop/AutoAdvance/Stop controls with an experimental Samsung VP8 codec operating-rate policy. The policy supplies a minimum operating rate based on 30 fps and playback speed, with diagnostics for investigating playback performance.

This branch also integrates upstream v1.3.47 and the Experimental release channel. Experimental APKs install alongside Personal and upstream builds with separate data and require manual updates. The VP8 policy remains experimental.

**Release integration:** This is the integration base of the release branch; its playback changes and Experimental release channel are included.

### `personal/video-playback-integration`

Combines video completion controls and the playback lifecycle fix in a Personal APK. It also resets completed videos when revisited after auto-advance, so navigating backward can replay them.

The current tip includes the synchronous MediaCodec queueing diagnostic and removes the earlier software-VP8 decoder preference. It uses the older standalone ExoPlayer backend and is a separate investigation build from the Media3 integrations.

**Release integration:** Separate older integration. Its lifecycle and completion controls are included through other branches; its auto-advance revisit reset and synchronous-queueing diagnostic are not included.

### `spike/media3-migration`

Migrates standalone ExoPlayer dependencies and playback APIs to AndroidX Media3 1.10.1, including the copied player UI components and scoped unstable-API opt-ins. It also contains the playback lifecycle fix used as its starting point.

This is an investigation branch for the migration. It does not include the configurable video completion feature or Personal packaging.

**Release integration:** The migration and lifecycle changes are included through the Media3 integration.

### `spike/media3-vp8-operating-rate`

Builds on the Media3 migration and playback lifecycle fix to test an explicit codec operating rate for VP8 on Samsung devices. The policy uses a minimum of 30 fps multiplied by playback speed and retains the normal Media3 rate policy for other formats.

Adds codec and frame diagnostics for evaluating the experiment. This branch does not include Personal packaging or the configurable video completion feature; the policy is not a confirmed general fix.

**Release integration:** The operating-rate experiment is included through the Media3 integration and remains experimental.

---

# Kuroba Experimental

### [Latest stable release](https://github.com/K1rakishou/Kuroba-Experimental/releases/latest)

### [Latest beta version](https://github.com/K1rakishou/Kuroba-Experimental-beta/releases/latest)

KurobaEx is a fast Android app for browsing imageboards, such as 4chan and 8chan. It's a fork of Kuroba. This fork provides lots of new features:

- New technological stack (Kotlin, RxJava/Coroutines, Room etc).

- On demand content loading (includes prefetching, youtube videos titles and durations fetching, inlined files size fetching etc).

- Third-party archives support.

- New thread navigation (tabs).

- New in-app navigation (bottom nav bar).

- New bookmarks (they were fully rewritten from scratch, now use way less memory, don't use wakelocks, show separate notifications per thread (and notifications can be swiped away).

- Edge-to-edge theme support.

- New database.

- 4chan global search support.

- Fully dynamic themes with Android Q Day/Night mode support.

- Per-site proxies.

- Ability to attach multiple media files to reply, attach media files that was shared by external apps (even by some keyboards), attach remote media files by URL, etc.

- New image downloader. Allows downloading images while the app is in background, retrying failed to download images, resolving duplicates, etc. 

- New posting. Posting code was moved into a foreground service which now allows stuff like using automatic captcha solvers (2captcha API) seamlessly or queueing multiple replies in different threads (only one reply per thread).

- New Media Viewer. It was rewritten from scratch and now lives in a separate activity. It now also supports stuff like viewing links to media files shared into the app.

- Thread downloader with ability to export threads as HTML pages with all downloaded media.

- Composite catalogs (ability to combine multiple boards of any available sites (except archives) together into a single catalog).

- Mpv video player (downloadable).

- Bookmark groups with ability to setup regex matchers to automatically move newly created bookmarks into them.

- ~~Automatic captcha solver for 4chan captcha (See https://github.com/K1rakishou/4chanCaptchaSolver)~~

- Lots of other tiny improvements.

### Screenshots:

[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/1.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/2.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/3.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/4.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/4.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/5.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/5.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/6.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/6.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/7.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/7.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/8.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/8.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/9.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/9.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/10.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/10.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/11.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/11.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/12.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/12.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/13.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/13.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/14.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/14.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/15.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/15.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/16.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/16.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/17.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/17.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/18.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/18.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/19.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/19.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/20.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/20.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/21.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/21.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/22.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/22.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/23.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/23.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/24.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/24.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/25.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/25.png)
[<img src="fastlane/metadata/android/en-US/images/phoneScreenshots/26.png" width=160>](fastlane/metadata/android/en-US/images/phoneScreenshots/26.png)

##### Currently supported sites
- 4Chan
- Dvach
- 8Kun (thanks to @jirn073-76)
- Lainchan
- Sushichan
- Wired-7 (thanks to @Wired-7)
- 370chan.info (thanks to @alcharkov)
- Endchan
- Kohlchan
- Krautchan
- 8chan.moe
- Lefypol (thanks to @yuiopmbv)
- Diochan (thanks to @Dashchanon)
- ~~420Chan (thanks to @Lolzen)~~
- ~~YesHoney (thanks to @SomeGuy719)~~
- ~~Vhschan (thanks to @MrPurple666)~~
- ~~Soyjak.party (thanks to @absurd-shaman)~~

##### Currently supported 4chan archives
- ArchivedMoe
- ArchiveOfSins
- B4k
- DesuArchive
- Fireden 
- 4Plebs 
- Warosu
- ~~Nyafuu~~
- ~~TokyoChronos~~
- ~~Wakarimasen.moe~~
- ~~RozenArcana~~

## License
[Kuroba is GPLv3](https://github.com/K1rakishou/Kuroba-Experimental/blob/develop/COPYING.txt), [licenses of the used libraries.](https://github.com/K1rakishou/Kuroba-Experimental/blob/develop/Kuroba/app/src/main/assets/html/licenses.html)
