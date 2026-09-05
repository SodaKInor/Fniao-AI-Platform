package org.jeecg.modules.ai.stream.domain;

/** Provider stop evidence. Only CONFIRMED_STOPPED may become a terminal local stop. */
public final class StreamStopResult {
    private final String providerSessionId;
    private final StreamStopOutcome outcome;

    public StreamStopResult(String providerSessionId, StreamStopOutcome outcome) {
        this.providerSessionId = providerSessionId;
        this.outcome = outcome;
    }

    public String getProviderSessionId() {
        return providerSessionId;
    }

    public StreamStopOutcome getOutcome() {
        return outcome;
    }
}
