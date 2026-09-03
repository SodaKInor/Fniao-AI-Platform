package org.jeecg.modules.ai.application.jobs;

import org.jeecg.modules.ai.domain.*;
import org.jeecg.modules.ai.port.JobRepository;

public final class JobQueryService {
    private final JobRepository jobs;
    private final org.jeecg.modules.ai.port.AssetRepository assets;
    public JobQueryService(JobRepository jobs,org.jeecg.modules.ai.port.AssetRepository assets) { this.jobs=jobs; this.assets=assets; }
    public java.util.List<Asset> resultAssets(JobRecord job) {
        java.util.List<Asset> result=new java.util.ArrayList<>();
        if (job.getResult()!=null) for (String id:job.getResult().getArtifactIds())
            result.add(assets.findOwned(id,job.getRequest().getOwnerId())
                    .orElseThrow(() -> new AiRequestException(ErrorCode.NOT_FOUND,"Result asset not found")));
        return result;
    }
    public JobRecord owned(String id,String owner) {
        new RequestFingerprint().identifier(id);
        return jobs.findOwned(id,owner).orElseThrow(() -> new AiRequestException(ErrorCode.NOT_FOUND,"Request not found"));
    }
    public JobPage history(String owner,JobState state,String cursor,int limit) { return jobs.listOwned(owner,state,cursor,limit); }
    public JobRecord await(String id,String owner,long waitMillis) {
        if (waitMillis<0 || waitMillis>1500) throw new AiRequestException(ErrorCode.INVALID_REQUEST,"Invalid wait budget");
        long end=System.nanoTime()+waitMillis*1000000L;
        JobRecord job=owned(id,owner);
        while (!finished(job) && System.nanoTime()<end) {
            long remaining=end-System.nanoTime();
            if (remaining<=0) break;
            try { Thread.sleep(Math.min(25,Math.max(1,remaining/1000000L))); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); break; }
            job=owned(id,owner);
        }
        return job;
    }
    public static boolean finished(JobRecord job) {
        JobState s=job.getState();
        return s==JobState.SUCCEEDED || s==JobState.FAILED || s==JobState.UNKNOWN || s==JobState.CANCELLED;
    }
}
