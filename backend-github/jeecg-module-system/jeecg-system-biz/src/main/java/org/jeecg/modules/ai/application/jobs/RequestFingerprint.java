package org.jeecg.modules.ai.application.jobs;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.*;
import org.jeecg.modules.ai.domain.*;

public final class RequestFingerprint {
    public String digest(String capability,String asset,DetectionParameters parameters,String retry) {
        if (!"image-detection.v1".equals(capability)) invalid();
        identifier(asset); if (retry!=null) identifier(retry);
        if (parameters==null || parameters.getThreshold()==null) invalid();
        BigDecimal threshold=parameters.getThreshold();
        if (threshold.compareTo(BigDecimal.ZERO)<0 || threshold.compareTo(BigDecimal.ONE)>0
                || threshold.precision()>1000 || Math.abs((long)threshold.scale())>1000
                || parameters.getMaxDetections()<1 || parameters.getMaxDetections()>100) invalid();
        String decimal=decimal(threshold);
        String canonical="wgai-inference-v1\n"+capability+"\n"+asset+"\n"+decimal+"\n"
                +parameters.getMaxDetections()+"\n"+parameters.isAnnotate()+"\n"+(retry==null ? "" : retry)+"\n";
        return hash(canonical);
    }
    public String digest(String capability,String asset,VideoParameters parameters,String retry) {
        if (!"video-file-analysis.v1".equals(capability)) invalid();
        identifier(asset); if (retry!=null) identifier(retry);
        if (parameters==null || parameters.getThreshold()==null
                || parameters.getThreshold().compareTo(BigDecimal.ZERO)<0
                || parameters.getThreshold().compareTo(BigDecimal.ONE)>0
                || parameters.getSampleIntervalMillis()<100 || parameters.getSampleIntervalMillis()>60000
                || parameters.getMaxEvents()<1 || parameters.getMaxEvents()>1000) invalid();
        String canonical="wgai-video-v1\n"+capability+"\n"+asset+"\n"+decimal(parameters.getThreshold())+"\n"
                +parameters.getSampleIntervalMillis()+"\n"+parameters.getMaxEvents()+"\n"
                +parameters.isIncludeSnapshots()+"\n"+parameters.isAnnotate()+"\n"+(retry==null ? "" : retry)+"\n";
        return hash(canonical);
    }
    private String decimal(BigDecimal value) {
        if (value.precision()>1000 || Math.abs((long)value.scale())>1000) invalid();
        return value.signum()==0 ? "0" : value.stripTrailingZeros().toPlainString();
    }
    private String hash(String canonical) {
        try {
            byte[] hash=MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            StringBuilder out=new StringBuilder(64);
            for (byte b:hash) out.append(String.format("%02x",b & 255));
            return out.toString();
        } catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    public void key(String key) { if (key==null || !key.matches("[A-Za-z0-9_-]{8,128}")) invalid(); }
    public void identifier(String id) { if (id==null || !id.matches("[A-Za-z0-9_-]{1,80}")) invalid(); }
    private void invalid() { throw new AiRequestException(ErrorCode.INVALID_REQUEST,"Invalid inference request"); }
}
