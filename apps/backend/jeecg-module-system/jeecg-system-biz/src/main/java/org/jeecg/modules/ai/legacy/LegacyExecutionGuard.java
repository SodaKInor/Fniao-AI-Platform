package org.jeecg.modules.ai.legacy;

/** Permanent migration gate, independent of old feature flags and inference mode. */
public final class LegacyExecutionGuard {
    private LegacyExecutionGuard() { }

    public static boolean isLocalExecutionAllowed() { return false; }

    public static void reject() {
        throw new IllegalStateException("旧 AI 执行入口已停用；请使用已授权的新 AI 接口");
    }
}
