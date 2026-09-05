package org.jeecg.modules.ai.assetsjobs;

import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.jeecg.modules.ai.job.domain.JobError;
import org.jeecg.modules.ai.job.domain.JobPage;
import org.jeecg.modules.ai.job.domain.JobRecord;
import org.jeecg.modules.ai.job.domain.JobState;
import org.jeecg.modules.ai.job.domain.JobSubmission;
import org.jeecg.modules.ai.job.domain.JobUpdate;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.*;
import static org.junit.Assert.*;

public class PersistenceTest {
    private DbFixture f;
    @After public void close() throws Exception { if (f!=null) f.close(); }

    @Test public void concurrentCreateAndClaimAreAtomic() throws Exception {
        f=new DbFixture(); ExecutorService pool=Executors.newFixedThreadPool(12);
        try {
            List<Callable<JobSubmission>> tasks=new ArrayList<>();
            for (int i=0;i<24;i++) tasks.add(() -> f.jobs.createOrGet(f.request("a","same-key")));
            List<Future<JobSubmission>> results=pool.invokeAll(tasks);
            Set<String> ids=new HashSet<>(); int created=0;
            for (Future<JobSubmission> future:results) {
                JobSubmission submission=future.get(); ids.add(submission.getJob().getRequest().getRequestId());
                if (submission.isCreated()) created++;
            }
            assertEquals(1,created); assertEquals(1,ids.size()); String id=ids.iterator().next();
            List<Callable<Boolean>> claims=new ArrayList<>();
            for (int i=0;i<24;i++) claims.add(() -> f.jobs.claimPending(id,0,UUID.randomUUID().toString(),Instant.now()).isPresent());
            int winners=0; for (Future<Boolean> won:pool.invokeAll(claims)) if (won.get()) winners++;
            assertEquals(1,winners);
            assertEquals(Integer.valueOf(2),f.sql.queryForObject("SELECT COUNT(*) FROM ai_job_event",Integer.class));
            JobRecord current=f.jobs.findOwned(id,"a").get();
            assertFalse(f.jobs.updateClaimed(id,0,current.getDispatchToken(),new JobUpdate(JobState.UNKNOWN,null,null,new JobError(ErrorCode.RESULT_UNKNOWN,"unknown",true),Instant.now())));
            assertFalse(f.jobs.updateClaimed(id,1,"wrong",new JobUpdate(JobState.UNKNOWN,null,null,new JobError(ErrorCode.RESULT_UNKNOWN,"unknown",true),Instant.now())));
            assertFalse(f.jobs.cancelPending(id,"a",1,Instant.now()).isPresent());
        } finally { pool.shutdownNow(); }
    }

    @Test public void capacityIsDurableAndKeysAreCaseSensitive() throws Exception {
        f=new DbFixture(2,1);
        JobRecord first=f.jobs.createOrGet(f.request("a","Same-key")).getJob();
        f.jobs.createOrGet(f.request("a","same-key"));
        try { f.jobs.createOrGet(f.request("a","third-key")); fail(); } catch (RejectedExecutionException expected) { }
        assertFalse(f.jobs.createOrGet(f.request("a","Same-key")).isCreated());
        assertTrue(f.jobs.cancelPending(first.getRequest().getRequestId(),"a",0,Instant.now()).isPresent());
        assertTrue(f.jobs.createOrGet(f.request("b","Same-key")).isCreated());
        assertEquals(1,f.jobs.listOwned("b",null,null,20).getItems().size());
        assertFalse(f.jobs.findOwned(first.getRequest().getRequestId(),"b").isPresent());
    }

    @Test public void eventFailureRollsBackClaim() throws Exception {
        f=new DbFixture(); JobRecord job=f.jobs.createOrGet(f.request("a","event-key")).getJob(); String id=job.getRequest().getRequestId();
        f.sql.update("INSERT INTO ai_job_event VALUES(?,1,'DISPATCHING',0)",id);
        try { f.jobs.claimPending(id,0,"token",Instant.now()); fail(); } catch (RuntimeException expected) { }
        assertEquals(JobState.PENDING,f.get(job).getState()); assertEquals(0,f.get(job).getVersion());
    }

    @Test public void cancelAndDispatchHaveOnlyOneWinner() throws Exception {
        f=new DbFixture(); JobRecord job=f.jobs.createOrGet(f.request("a","cancel-key")).getJob(); String id=job.getRequest().getRequestId();
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            Future<Boolean> cancel=pool.submit(() -> { start.await(); return f.jobs.cancelPending(id,"a",0,Instant.now()).isPresent(); });
            Future<Boolean> claim=pool.submit(() -> { start.await(); return f.jobs.claimPending(id,0,"token",Instant.now()).isPresent(); });
            start.countDown(); assertTrue(cancel.get() ^ claim.get());
            assertEquals(1,f.get(job).getVersion());
        } finally { pool.shutdownNow(); }
    }

    @Test public void historyUsesStableOwnerScopedCursor() throws Exception {
        f=new DbFixture();
        for (int i=0;i<5;i++) f.jobs.createOrGet(f.request("a","history-"+i));
        f.jobs.createOrGet(f.request("b","history-0"));
        JobPage page=f.jobs.listOwned("a",null,null,2); Set<String> ids=new HashSet<>();
        while (true) {
            for (JobRecord job:page.getItems()) { assertEquals("a",job.getRequest().getOwnerId()); assertTrue(ids.add(job.getRequest().getRequestId())); }
            if (page.getNextCursor()==null) break;
            page=f.jobs.listOwned("a",null,page.getNextCursor(),2);
        }
        assertEquals(5,ids.size());
        try { f.jobs.listOwned("a",null,"invalid",20); fail(); } catch (IllegalArgumentException expected) { }
    }

    @Test public void unknownAndFinalStateCannotBeOverwritten() throws Exception {
        f=new DbFixture(); JobRecord pending=f.jobs.createOrGet(f.request("a","unknown-key")).getJob(); String id=pending.getRequest().getRequestId();
        JobRecord claimed=f.jobs.claimPending(id,0,"token",Instant.now()).get();
        assertFalse(f.jobs.updateClaimed(id,claimed.getVersion(),"token",new JobUpdate(JobState.SUCCEEDED,null,null,null,Instant.now())));
        assertTrue(f.jobs.updateClaimed(id,claimed.getVersion(),"token",new JobUpdate(JobState.UNKNOWN,null,null,new JobError(ErrorCode.RESULT_UNKNOWN,"unknown",true),Instant.now())));
        assertFalse(f.jobs.updateClaimed(id,2,"token",new JobUpdate(JobState.WAITING,null,null,null,Instant.now())));
        assertFalse(f.jobs.claimPending(id,2,"another",Instant.now()).isPresent());
    }
}
