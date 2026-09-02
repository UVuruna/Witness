# app/probe-tools/ — shell-identity call-audio probe (throwaway)

A single Java file run through the ADB shell identity (uid 2000 =
`com.android.shell`), the same privileged identity `scrcpy` and
ShizuCallRecorder use. It exists to MEASURE, on a real device over the cable,
what the previous session only assumed.

## Why it is separate from the app

The Kotlin app (`app/src/...`) is an ordinary third-party app: the platform
refuses it `VOICE_CALL` / `VOICE_UPLINK` / `VOICE_DOWNLINK`. `CallCap.java`
is NOT installed as an app — it is pushed as a dex and executed by
`app_process` so its whole process runs AS shell, which holds
`CAPTURE_AUDIO_OUTPUT` + `CALL_AUDIO_INTERCEPTION`. That is the difference the
M0 measurement turns on.

This is a probe, not product code. The shipping product would obtain the same
shell identity through Shizuku (a UserService), decided at M1 only if the
measurement says the privileged channel is the path.

## Measured on SM-S938B, Android 16, One UI 8.5 (2026-09-02)

- `probe` (no call): all of MIC, VOICE_UPLINK, VOICE_DOWNLINK, VOICE_CALL,
  VOICE_COMMUNICATION, REMOTE_SUBMIX -> **OPENED** as shell.
- `rec 1` (MIC, no call): peak 1606 -> the capture pipe writes real audio.
- `rec 4` (VOICE_CALL, no call): peak 0 -> silent, expected (no call active).
- Open question, needs a live call: does VOICE_CALL carry BOTH voices.

## Build & run

Compile against the SDK's `android.jar` (platform 35), dex with `d8`, push,
and execute as shell:

```
javac -cp ANDROID_JAR -d classes --release 11 CallCap.java
d8 --min-api 29 --output callcap.jar classes/com/uvuruna/callcap/*.class
adb push callcap.jar /data/local/tmp/callcap.jar
adb shell CLASSPATH=/data/local/tmp/callcap.jar app_process /data/local/tmp com.uvuruna.callcap.CallCap probe
adb shell CLASSPATH=/data/local/tmp/callcap.jar app_process /data/local/tmp com.uvuruna.callcap.CallCap rec 4 15000 /data/local/tmp/t.wav
adb pull /data/local/tmp/t.wav
```

Sources: `MIC=1 VOICE_UPLINK=2 VOICE_DOWNLINK=3 VOICE_CALL=4
VOICE_COMMUNICATION=7 REMOTE_SUBMIX=8`.
