// The privileged recorder, seen from the app side.
//
// The implementation runs in a process Shizuku spawns AS the ADB shell
// (uid 2000), which holds CAPTURE_AUDIO_OUTPUT and so may open the call's own
// audio. The app cannot do that; this AIDL is the only door to the process
// that can. The app hands over a file descriptor it opened itself, so the
// shell process writes bytes into a file the APP owns — nothing privileged
// ever touches app-private storage directly.
//
// AIDL requires either NO method has an id or EVERY method does; Shizuku
// reserves destroy()=16777114, so all are numbered explicitly.
package com.pebblesoft.toolbox.capture.shizuku;

interface IRecorderService {
    // Probe every audio source and report, per source, whether it OPENS.
    String probe() = 1;

    // Begin capturing `audioSource` (a MediaRecorder.AudioSource constant) as
    // 16-bit PCM WAV into `sink`. Returns "" on success or an error string.
    String start(int audioSource, in ParcelFileDescriptor sink) = 2;

    // Stop and finalise the WAV. Returns the peak amplitude seen, so the app
    // can tell "sound arrived" from "silence" without decoding the file.
    int stop() = 3;

    boolean isRecording() = 4;

    // The AIDL destroy() Shizuku calls when the UserService is torn down.
    void destroy() = 16777114;
}
