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
        if (from == JobState.FETCHING_RESULT && !codec.write(old.getProviderResult()).equals(codec.write(u.getProviderResult()))) return false;
        if (to == JobState.FETCHING_RESULT && (u.getProviderResult() == null || u.getResult() != null)) return false;
        if (to == JobState.SUCCEEDED && (u.getResult() == null || u.getError() != null || u.getProviderResult() == null)) return false;
        if (to == JobState.SUCCEEDED && (!codec.write(u.getResult().getData()).equals(codec.write(u.getProviderResult().getData()))
                || u.getResult().isSimulated()!=old.getRequest().isSimulated()
                || u.getResult().getArtifactIds().size()!=u.getProviderResult().getArtifacts().size())) return false;
        if ((to == JobState.FAILED || to == JobState.UNKNOWN) && (u.getError() == null || u.getResult() != null)) return false;
        return to != JobState.WAITING || (u.getResult() == null && u.getError() == null && u.getProviderResult() == null);
    }
}
