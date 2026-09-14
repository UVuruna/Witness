// The privileged recorder, seen from the app side.
//
// The implementation runs in a process Shizuku spawns AS the ADB shell
// (uid 2000), which holds CAPTURE_AUDIO_OUTPUT and so may open the call's own
// audio. The app cannot do that; this AIDL is the only door to the process
// that can. The app hands over a file descriptor it opened itself, so the
// shell process writes bytes into a file the APP owns — nothing privileged
// ever touches app-private storage directly.
//
// The app never names an audio source. Which sources exist and which may be
// opened is knowledge of the privileged side, so the ladder lives there and
// what actually opened comes back in the result.
//
// AIDL requires either NO method has an id or EVERY method does; Shizuku
// reserves destroy()=16777114, so all are numbered explicitly.
package com.pebblesoft.toolbox.capture.shizuku;

interface IRecorderService {
    // Probe every audio source and report, per source, whether it OPENS.
    String probe() = 1;

    // Begin capturing the call as 16-bit PCM WAV into `sink`, stereo where the
    // device offers it. Returns "" on success or an error string.
    String start(in ParcelFileDescriptor sink) = 2;

    // Stop and finalise the WAV. Returns the encoded CaptureOutcome: which
    // source opened, how many channels, and the per-channel measurement the
    // app needs to decide whether both people are in the file.
    String stop() = 3;

    boolean isRecording() = 4;

    // The AIDL destroy() Shizuku calls when the UserService is torn down.
    void destroy() = 16777114;
}
