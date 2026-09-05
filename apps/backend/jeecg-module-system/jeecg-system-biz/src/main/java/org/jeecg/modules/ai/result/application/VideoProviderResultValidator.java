package org.jeecg.modules.ai.result.application;

import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;
import org.jeecg.modules.ai.job.domain.JobRequest;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.video.domain.ProviderVideoEvent;
import org.jeecg.modules.ai.video.domain.VideoProviderResult;

import java.util.HashSet;
import java.util.Set;

public final class VideoProviderResultValidator {
    public void validate(JobRequest request,VideoProviderResult result) throws ProviderException {
        if (result==null || !request.getRequestId().equals(result.getProviderRequestId())
                || result.isSimulated()!=request.isSimulated()
                || result.getEvents().size()>request.getVideoParameters().getMaxEvents()) invalid();
        long previous=-1; Set<String> ids=new HashSet<>();
        for (ProviderVideoEvent event:result.getEvents()) {
            if (event==null || event.getProviderEventId()==null
                    || !event.getProviderEventId().matches("[A-Za-z0-9_-]{1,120}") || !ids.add(event.getProviderEventId())
                    || event.getOffsetMillis()<previous || event.getEventType()==null
                    || event.getEventType().isEmpty() || event.getEventType().length()>120
                    || event.getScore()!=null && (event.getScore().signum()<0
                    || event.getScore().compareTo(java.math.BigDecimal.ONE)>0)
                    || (!request.getVideoParameters().isIncludeSnapshots() && event.getSnapshot()!=null)) invalid();
            previous=event.getOffsetMillis();
        }
        if (!request.getVideoParameters().isAnnotate() && result.getAnnotatedVideo()!=null) invalid();
    }
    private void invalid() throws ProviderException {
        throw new ProviderException(ErrorCode.PROVIDER_PROTOCOL,ExecutionCertainty.UNKNOWN,
                "Provider video result violates the saved request");
    }
}
