# Vault

Where the evidence lives, and the only door to it.

## Responsibility

Hold every recording and transcript encrypted in app-private storage, seal each
one at the moment it is closed, and hand out temporary decrypted COPIES when —
and only when — a screen needs to play or share something.

## Decisions that outlive the code

- **The seal is taken over the STORED bytes**, right after the stream closes and
  before anything else may read the file. That is the only moment at which the
  file is provably untouched, so the hash taken then is the one worth keeping.
  `Seal.over()` streams the file in 64 KB blocks, so a two-hour recording never
  has to fit in memory.
- **Nothing ever leaves the vault directory.** Playback and sharing go through
  `decryptToScratch`, which writes a copy into `cacheDir/open`. That directory is
  the ONLY path exposed to `FileProvider` (`res/xml/shared_paths.xml`), and
  `purgeScratch()` wipes it as soon as the screen that needed it goes away.
- **Encryption is Jetpack Security `EncryptedFile` with a keystore-backed master
  key**, not a password the user could forget or an abuser could force out of
  her. Losing the device keystore means losing the vault — that is the intended
  trade, and it is exactly why off-phone backup is its own feature.
- **The directory is called `store` and file names carry no meaning.** THE
  INSPECTION TEST does not stop at the app's front door.

## Connections

- Used by: the capture pipeline (writes), the recordings screens (reads),
  `AppViewModel.deleteRecord` (deletes audio and transcript together).
- Indexed by: `data/CallRecord`, which stores the file name, size, hash and the
  moment the seal was taken.
