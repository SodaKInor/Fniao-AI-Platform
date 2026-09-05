package org.jeecg.modules.ai.stream.application;

import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.ExecutionCertainty;
import org.jeecg.modules.ai.job.domain.ProviderException;
import org.jeecg.modules.ai.result.domain.ProviderArtifact;
import org.jeecg.modules.ai.result.port.ProviderArtifactReader;
import org.jeecg.modules.ai.stream.domain.ProviderStreamEvent;
import org.jeecg.modules.ai.stream.domain.ProviderStreamEventPage;
import org.jeecg.modules.ai.stream.domain.StreamEvent;
import org.jeecg.modules.ai.stream.domain.StreamSession;
import org.jeecg.modules.ai.stream.port.StreamEventRepository;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.*;
import org.jeecg.modules.ai.asset.application.AssetService;
import org.jeecg.modules.ai.job.application.AiRequestException;

public final class StreamEventCollector {
    private final StreamEventRepository events;
    private final ProviderArtifactReader reader;
    private final AssetService assets;
    private final Clock clock;
    private final long outputLimit;
    public StreamEventCollector(StreamEventRepository events,ProviderArtifactReader reader,AssetService assets,
            Clock clock,long outputLimit) {
        this.events=events; this.reader=reader; this.assets=assets; this.clock=clock; this.outputLimit=outputLimit;
    }

    public boolean collect(StreamSession session,ProviderStreamEventPage page) throws ProviderException {
        if (page==null || page.getItems().size()>session.getRequest().getParameters().getMaxEventsPerPoll())
            throw protocol();
        List<StreamEvent> stored=new ArrayList<>(); Set<String> ids=new HashSet<>();
        long previous=-1; java.time.Instant previousTime=null;
        for (ProviderStreamEvent event:page.getItems()) {
            if (event==null || event.getProviderEventId()==null
                    || !event.getProviderEventId().matches("[A-Za-z0-9_-]{1,120}") || !ids.add(event.getProviderEventId())
                    || event.getOffsetMillis()<previous || event.getOccurredAt()==null || event.getEventType()==null
                    || previousTime!=null && event.getOccurredAt().isBefore(previousTime)
                    || event.getEventType().isEmpty() || event.getEventType().length()>120
                    || event.getScore()!=null && (event.getScore().signum()<0
                    || event.getScore().compareTo(java.math.BigDecimal.ONE)>0)) throw protocol();
            String eventId=id("evt",session.getRequest().getSessionId(),event.getProviderEventId()); String snapshotId=null;
            if (event.getSnapshot()!=null) {
                if (!session.getRequest().getParameters().isIncludeSnapshots()) throw protocol();
                snapshotId=id("out",session.getRequest().getSessionId(),event.getProviderEventId()+"-snapshot");
                collectArtifact(snapshotId,session,event.getSnapshot());
            }
            stored.add(new StreamEvent(eventId,event.getProviderEventId(),event.getOffsetMillis(),event.getOccurredAt(),
                    event.getEventType(),event.getScore(),snapshotId)); previous=event.getOffsetMillis();
            previousTime=event.getOccurredAt();
        }
        return events.appendAndAdvance(session.getRequest().getSessionId(),session.getVersion(),session.getCursor(),
                stored,page.getNextCursor(),clock.instant());
    }

    private void collectArtifact(String id,StreamSession session,ProviderArtifact artifact) throws ProviderException {
        if (assets.collected(id,session.getRequest().getOwnerId()).isPresent()) return;
        if (artifact.getExpiresAt()!=null && !clock.instant().isBefore(artifact.getExpiresAt()))
            throw new AiRequestException(ErrorCode.ARTIFACT_EXPIRED,"Stream snapshot expired");
        try (InputStream input=reader.open(session.getRequest().getCapability(),artifact,outputLimit)) {
            assets.collect(id,session.getRequest().getOwnerId(),artifact.getMetadata(),input,outputLimit);
        } catch (IOException e) {
            throw new ProviderException(ErrorCode.ARTIFACT_TRANSFER,ExecutionCertainty.UNKNOWN,
                    "Stream snapshot transfer failed");
        }
    }
    private String id(String prefix,String session,String providerEvent) {
        return prefix+"_"+UUID.nameUUIDFromBytes((session+"\n"+providerEvent).getBytes(StandardCharsets.UTF_8));
    }
    private ProviderException protocol() {
        return new ProviderException(ErrorCode.PROVIDER_PROTOCOL,ExecutionCertainty.UNKNOWN,
                "Provider stream event page violates the saved request");
    }
}
