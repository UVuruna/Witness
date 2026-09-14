# RecordDetailScreen

One recording, and everything a person needs to decide what to do with it.

## Responsibility

Show who and when, what the recording is WORTH, let her listen, show the
transcript, and be the one place from which a copy may leave the app or the
recording may be destroyed.

## Decisions that outlive the code

- **The verdict comes before the play button.** A pill in a list is enough to
  scan; a person about to send a file to a lawyer needs a sentence. Only
  `BOTH_VOICES` is drawn calm — every other verdict is a warning colour with an
  explanation, because THE HALF-RECORDING LAW is worth nothing if the warning is
  quieter than the action.
- **Playing and sharing use a decrypted COPY, wiped on exit.** The vault file is
  never handed to a media player or a share sheet. The copy lives in the cache
  scratch area while this screen is open and is purged when it goes away, so
  between visits there is nothing on the phone for anyone inspecting it to find.
- **Sharing warns first, every time.** A shared copy leaves the app's protection
  and appears in whatever app carried it. The dialog says so plainly rather than
  relying on the user to remember, and the FileProvider grant is temporary.
- **The seal is checked here, not taken on trust.** The hash stored on the row
  was taken the moment the file was closed; comparing it now is what separates
  "audio" from "evidence that was never edited".
- **Deleting is confirmed and irreversible, and it takes the bytes first.** A row
  without a file is a confusing gap; a file without a row is invisible evidence
  left on the device.

## Connections

- Reads: `ui/AppViewModel.open` (decrypt + seal check + transcript), which in
  turn uses `vault/Vault`.
- Reached from: `ui/recordings/RecordingsScreen` and the home screen's latest
  list, through `ui/AppNav`.
