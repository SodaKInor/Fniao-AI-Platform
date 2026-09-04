package org.jeecg.modules.ai.persistence.converter;

import org.jeecg.modules.ai.domain.*;

/** Single enforcement point for the frozen repository transition table. */
public final class ClaimedUpdateRules {
    public boolean allows(JobRecord old, JobUpdate u, SnapshotCodec codec) {
        if (u == null || u.getState() == null || u.getUpdatedAt() == null) return false;
        JobState from = old.getState(), to = u.getState();
        boolean legal = (from == JobState.DISPATCHING && (to == JobState.WAITING || to == JobState.FETCHING_RESULT
                || to == JobState.FAILED || to == JobState.UNKNOWN))
                || (from == JobState.WAITING && (to == JobState.FETCHING_RESULT || to == JobState.FAILED || to == JobState.UNKNOWN))
                || (from == JobState.FETCHING_RESULT && (to == JobState.FETCHING_RESULT || to == JobState.SUCCEEDED || to == JobState.FAILED));
        if (!legal || u.getUpdatedAt().isBefore(old.getUpdatedAt())) return false;
        boolean video=old.getRequest().getJobType()==JobType.VIDEO_FILE_ANALYSIS;
        if (from==JobState.FETCHING_RESULT && !sameCheckpoint(old,u,codec,video)) return false;
        if (to==JobState.FETCHING_RESULT && (!hasCheckpoint(u,video) || hasResult(u))) return false;
        if (to==JobState.SUCCEEDED && (u.getError()!=null || !validSuccess(old,u,codec,video))) return false;
        if ((to==JobState.FAILED || to==JobState.UNKNOWN) && (u.getError()==null || hasResult(u))) return false;
        if (to==JobState.UNKNOWN && u.getUnknownReason()==null
                && (video || u.getError()==null || u.getError().getCode()!=ErrorCode.RESULT_UNKNOWN)) return false;
        return to!=JobState.WAITING || (!hasCheckpoint(u,video) && !hasResult(u) && u.getError()==null);
    }

    private boolean sameCheckpoint(JobRecord old,JobUpdate update,SnapshotCodec codec,boolean video) {
        if (video) {
            VideoSnapshotCodec snapshots=new VideoSnapshotCodec();
            return snapshots.write(old.getVideoProviderResult()).equals(snapshots.write(update.getVideoProviderResult()));
        }
        return codec.write(old.getProviderResult()).equals(codec.write(update.getProviderResult()));
    }
    private boolean hasCheckpoint(JobUpdate update,boolean video) {
        return video ? update.getVideoProviderResult()!=null : update.getProviderResult()!=null;
    }
    private boolean hasResult(JobUpdate update) { return update.getResult()!=null || update.getVideoResult()!=null; }
    private boolean validSuccess(JobRecord old,JobUpdate update,SnapshotCodec codec,boolean video) {
        if (!hasCheckpoint(update,video)) return false;
        if (video) {
            VideoProviderResult checkpoint=update.getVideoProviderResult(); VideoResult result=update.getVideoResult();
            if (result==null || result.isSimulated()!=old.getRequest().isSimulated()
                    || result.getEvents().size()!=checkpoint.getEvents().size()) return false;
            int artifacts=checkpoint.getAnnotatedVideo()==null ? 0 : 1;
            for (ProviderVideoEvent event:checkpoint.getEvents()) if (event.getSnapshot()!=null) artifacts++;
            return artifacts==result.getSnapshotAssetIds().size()+(result.getAnnotatedVideoAssetId()==null ? 0 : 1);
        }
        InferenceResult result=update.getResult(); ProviderResult checkpoint=update.getProviderResult();
        return result!=null && codec.write(result.getData()).equals(codec.write(checkpoint.getData()))
                && result.isSimulated()==old.getRequest().isSimulated()
                && result.getArtifactIds().size()==checkpoint.getArtifacts().size();
    }
}
