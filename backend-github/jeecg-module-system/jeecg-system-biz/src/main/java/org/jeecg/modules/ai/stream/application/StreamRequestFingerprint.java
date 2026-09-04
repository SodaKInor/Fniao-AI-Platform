package org.jeecg.modules.ai.stream.application;

import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.stream.domain.StreamParameters;

import java.nio.charset.StandardCharsets;
import java.security.*;
import org.jeecg.modules.ai.job.application.AiRequestException;

final class StreamRequestFingerprint {
    void key(String value) { if (value==null || !value.matches("[A-Za-z0-9_-]{8,128}")) invalid(); }
    void id(String value) { if (value==null || !value.matches("[A-Za-z0-9_-]{1,80}")) invalid(); }
    String digest(String capability,String source,StreamParameters parameters) {
        if (!"video-stream-analysis.v1".equals(capability) || parameters==null) invalid();
        id(source);
        if (parameters.getMaxEventsPerPoll()<1 || parameters.getMaxEventsPerPoll()>200
                || parameters.getPollIntervalMillis()<250 || parameters.getPollIntervalMillis()>30000) invalid();
        return hash("wgai-stream-v1\n"+capability+"\n"+source+"\n"+parameters.getMaxEventsPerPoll()+"\n"
                +parameters.getPollIntervalMillis()+"\n"+parameters.isIncludeSnapshots()+"\n");
    }
    private String hash(String value) {
        try {
            byte[] bytes=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(64); for (byte b:bytes) out.append(String.format("%02x",b&255));
            return out.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    private void invalid() { throw new AiRequestException(ErrorCode.INVALID_REQUEST,"Invalid stream request"); }
}
