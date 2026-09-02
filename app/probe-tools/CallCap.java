package com.uvuruna.callcap;

import android.media.AudioFormat;
import android.media.AudioRecord;
import java.io.RandomAccessFile;

/**
 * Runs as the ADB shell identity (uid 2000 = com.android.shell), which is the
 * SAME privileged identity scrcpy and ShizuCallRecorder use — it holds
 * CAPTURE_AUDIO_OUTPUT and CALL_AUDIO_INTERCEPTION, the permissions the audio
 * policy service checks for VOICE_CALL / VOICE_UPLINK / VOICE_DOWNLINK.
 *
 *   probe            -> just try to OPEN every source, print STATE (no call needed)
 *   rec SRC MS PATH  -> open SRC, record MS milliseconds to PATH as WAV, print peak
 *
 * Source ints: MIC=1 VOICE_UPLINK=2 VOICE_DOWNLINK=3 VOICE_CALL=4
 *              VOICE_COMMUNICATION=7 REMOTE_SUBMIX=8
 */
public final class CallCap {
    static final int RATE = 16000;
    static final int CH = AudioFormat.CHANNEL_IN_MONO;
    static final int ENC = AudioFormat.ENCODING_PCM_16BIT;

    static final int[] SRC = {1, 2, 3, 4, 7, 8};
    static final String[] NAME = {"?","MIC","VOICE_UPLINK","VOICE_DOWNLINK","VOICE_CALL","?","?","VOICE_COMMUNICATION","REMOTE_SUBMIX"};

    public static void main(String[] a) throws Exception {
        if (a.length >= 1 && a[0].equals("probe")) { probeAll(); return; }
        if (a.length >= 4 && a[0].equals("rec")) {
            rec(Integer.parseInt(a[1]), Integer.parseInt(a[2]), a[3]); return;
        }
        System.out.println("usage: probe | rec SRC MS PATH");
    }

    static void probeAll() {
        for (int s : SRC) {
            int min = AudioRecord.getMinBufferSize(RATE, CH, ENC);
            String tag = NAME[s] + "(" + s + ")";
            try {
                AudioRecord r = new AudioRecord(s, RATE, CH, ENC, Math.max(min, 4096) * 2);
                int st = r.getState();
                System.out.println(tag + " -> " + (st == AudioRecord.STATE_INITIALIZED ? "OPENED" : "NOT_INITIALIZED(" + st + ")"));
                r.release();
            } catch (Throwable t) {
                System.out.println(tag + " -> REFUSED: " + t.getClass().getSimpleName() + " " + t.getMessage());
            }
        }
    }

    static void rec(int src, int ms, String path) throws Exception {
        int min = AudioRecord.getMinBufferSize(RATE, CH, ENC);
        AudioRecord r = new AudioRecord(src, RATE, CH, ENC, Math.max(min, 4096) * 2);
        if (r.getState() != AudioRecord.STATE_INITIALIZED) {
            System.out.println("REFUSED: source " + src + " state " + r.getState());
            r.release(); return;
        }
        short[] buf = new short[min];
        RandomAccessFile raf = new RandomAccessFile(path, "rw");
        raf.setLength(0);
        raf.write(new byte[44]);
        long total = 0, silent = 0, bufs = 0; int peak = 0;
        r.startRecording();
        long end = System.currentTimeMillis() + ms;
        byte[] bytes = new byte[min * 2];
        while (System.currentTimeMillis() < end) {
            int n = r.read(buf, 0, buf.length);
            if (n <= 0) continue;
            int bp = 0;
            for (int i = 0; i < n; i++) {
                short v = buf[i];
                bytes[i*2] = (byte)(v & 0xff);
                bytes[i*2+1] = (byte)((v >> 8) & 0xff);
                int av = Math.abs(v);
                if (av > bp) bp = av;
            }
            raf.write(bytes, 0, n * 2);
            total += n; bufs++;
            if (bp < 60) silent++;
            if (bp > peak) peak = bp;
        }
        r.stop(); r.release();
        writeHeader(raf, total * 2);
        raf.close();
        double sr = bufs > 0 ? (double) silent / bufs : 1.0;
        System.out.println("DONE src=" + src + " peak=" + peak + " silentRatio=" + String.format("%.3f", sr) + " samples=" + total + " file=" + path);
    }

    static void writeHeader(RandomAccessFile raf, long dataLen) throws Exception {
        raf.seek(0);
        byte[] h = new byte[44];
        put(h,0,"RIFF"); i32(h,4,(int)(dataLen+36)); put(h,8,"WAVE"); put(h,12,"fmt ");
        i32(h,16,16); i16(h,20,1); i16(h,22,1); i32(h,24,RATE); i32(h,28,RATE*2); i16(h,32,2); i16(h,34,16);
        put(h,36,"data"); i32(h,40,(int)dataLen);
        raf.write(h);
    }
    static void put(byte[]b,int o,String s){for(int i=0;i<s.length();i++)b[o+i]=(byte)s.charAt(i);}
    static void i32(byte[]b,int o,int v){b[o]=(byte)v;b[o+1]=(byte)(v>>8);b[o+2]=(byte)(v>>16);b[o+3]=(byte)(v>>24);}
    static void i16(byte[]b,int o,int v){b[o]=(byte)v;b[o+1]=(byte)(v>>8);}
}
