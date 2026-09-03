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
        String decimal=threshold.signum()==0 ? "0" : threshold.stripTrailingZeros().toPlainString();
        String canonical="wgai-inference-v1\n"+capability+"\n"+asset+"\n"+decimal+"\n"
                +parameters.getMaxDetections()+"\n"+parameters.isAnnotate()+"\n"+(retry==null ? "" : retry)+"\n";
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
