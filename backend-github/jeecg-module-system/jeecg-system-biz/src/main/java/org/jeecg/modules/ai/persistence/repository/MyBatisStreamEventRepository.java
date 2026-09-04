package org.jeecg.modules.ai.persistence.repository;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import java.util.function.IntConsumer;
import java.util.stream.Collectors;
import org.springframework.transaction.*;
import org.springframework.transaction.support.TransactionTemplate;
import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.StreamEventRepository;
import org.jeecg.modules.ai.persistence.converter.StreamRecordConverter;
import org.jeecg.modules.ai.persistence.entity.*;
import org.jeecg.modules.ai.persistence.mapper.*;

public final class MyBatisStreamEventRepository implements StreamEventRepository {
    private final StreamSessionMapper sessions;
    private final StreamEventMapper events;
    private final AssetMapper assets;
    private final StreamRecordConverter converter;
    private final TransactionTemplate transaction;
    private final IntConsumer inserted;
    private final IntConsumer duplicate;
    public MyBatisStreamEventRepository(StreamSessionMapper sessions,StreamEventMapper events,AssetMapper assets,
            StreamRecordConverter converter,PlatformTransactionManager manager) {
        this(sessions,events,assets,converter,manager,count -> {},count -> {});
    }
    public MyBatisStreamEventRepository(StreamSessionMapper sessions,StreamEventMapper events,AssetMapper assets,
            StreamRecordConverter converter,PlatformTransactionManager manager,IntConsumer inserted,IntConsumer duplicate) {
        this.sessions=sessions; this.events=events; this.assets=assets; this.converter=converter;
        this.inserted=Objects.requireNonNull(inserted); this.duplicate=Objects.requireNonNull(duplicate);
        transaction=new TransactionTemplate(manager); transaction.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public StreamEventPage listOwned(String sessionId,String owner,String cursor,int limit) {
        if (limit<1 || limit>200) throw new IllegalArgumentException("Invalid event page limit");
        Cursor after=cursor(cursor); List<StreamEventRow> rows=events.page(sessionId,owner,after.offset,after.id,limit+1);
        String next=null;
        if (rows.size()>limit) {
            rows=new ArrayList<>(rows.subList(0,limit)); StreamEventRow last=rows.get(rows.size()-1);
            next=Base64.getUrlEncoder().withoutPadding().encodeToString(
                    (last.offsetMillis+":"+last.eventId).getBytes(StandardCharsets.UTF_8));
        }
        return new StreamEventPage(sessionId,rows.stream().map(converter::event).collect(Collectors.toList()),next);
    }

    public boolean appendAndAdvance(String sessionId,long version,String expectedCursor,List<StreamEvent> values,
            String nextCursor,Instant now) {
        if (values==null || values.size()>200 || nextCursor!=null && nextCursor.length()>512) return false;
        int[] counts=new int[2];
        boolean applied=Boolean.TRUE.equals(transaction.execute(status -> {
            StreamSessionRow row=sessions.lock(sessionId);
            if (row==null || row.version!=version || !Objects.equals(row.providerCursor,expectedCursor)
                    || !"RUNNING".equals(row.state)) return false;
            for (StreamEvent event:values) {
                if (event==null) return false;
                String snapshot=event.getSnapshotAssetId();
                if (snapshot!=null && (!snapshot.equals(snapshotId(sessionId,event.getProviderEventId()))
                        || assets.findOwned(snapshot,row.ownerId)==null)) return false;
            }
            for (StreamEvent event:values) {
                if (events.insertIgnore(converter.event(sessionId,event))==1) counts[0]++; else counts[1]++;
            }
            row.providerCursor=nextCursor; row.updatedAt=now.toEpochMilli(); row.version++;
            if (sessions.update(row)==1) return true;
            status.setRollbackOnly(); return false;
        }));
        if (applied) { inserted.accept(counts[0]); duplicate.accept(counts[1]); }
        return applied;
    }

    /** Snapshot identities are bound to one local session and provider event, never merely to an owner. */
    private String snapshotId(String sessionId,String providerEventId) {
        if (providerEventId==null) return "";
        return "out_"+UUID.nameUUIDFromBytes(
                (sessionId+"\n"+providerEventId+"-snapshot").getBytes(StandardCharsets.UTF_8));
    }

    private Cursor cursor(String value) {
        if (value==null) return new Cursor(null,null);
        if (value.length()>512) throw new IllegalArgumentException("Invalid event cursor");
        try {
            String[] parts=new String(Base64.getUrlDecoder().decode(value),StandardCharsets.UTF_8).split(":",-1);
            if (parts.length!=2 || !parts[1].matches("[A-Za-z0-9_-]{1,80}")) throw new IllegalArgumentException();
            return new Cursor(Long.valueOf(parts[0]),parts[1]);
        } catch (RuntimeException e) { throw new IllegalArgumentException("Invalid event cursor"); }
    }
    private static final class Cursor {
        final Long offset; final String id; Cursor(Long offset,String id) { this.offset=offset; this.id=id; }
    }
}
