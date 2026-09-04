package org.jeecg.modules.ai.video.storage;

import java.io.*;
import java.nio.file.*;

/** Minimal ISO-BMFF boundary check: require an ftyp box and an H.264 sample entry. */
public final class VideoFileVerifier {
    public void verify(Path file) throws IOException {
        long size=Files.size(file);
        if (size<16) throw new IllegalArgumentException("Invalid MP4 file");
        byte[] prefix=new byte[12]; boolean h264=false;
        try (InputStream input=new BufferedInputStream(Files.newInputStream(file,LinkOption.NOFOLLOW_LINKS))) {
            new DataInputStream(input).readFully(prefix);
            int rolling=0,read=0,value;
            while ((value=input.read())!=-1) {
                rolling=(rolling<<8)|(value&255); read++;
                if (read>=4 && (rolling==token("avc1") || rolling==token("avc3"))) { h264=true; break; }
            }
        }
        if (!boxAt(prefix,4,"ftyp") || !h264)
            throw new IllegalArgumentException("Only MP4/H.264 is supported");
    }

    private boolean boxAt(byte[] value,int offset,String name) {
        if (value.length<offset+4) return false;
        for (int i=0;i<4;i++) if (value[offset+i]!=(byte)name.charAt(i)) return false;
        return true;
    }

    private int token(String value) {
        return value.charAt(0)<<24|value.charAt(1)<<16|value.charAt(2)<<8|value.charAt(3);
    }
}
