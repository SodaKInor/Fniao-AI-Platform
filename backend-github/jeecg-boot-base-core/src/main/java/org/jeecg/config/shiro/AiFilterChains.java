package org.jeecg.config.shiro;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reserve the AI boundary before broad anonymous patterns, including configured exclusions. */
public final class AiFilterChains {
    private AiFilterChains() { }

    public static Map<String, String> protect(Map<String, String> existing, boolean accessFilterPresent) {
        return protect(existing,accessFilterPresent,false);
    }

    public static Map<String, String> protect(Map<String, String> existing, boolean accessFilterPresent, boolean aiJwtPresent) {
        Map<String, String> protectedChains = new LinkedHashMap<>();
        String chain = (aiJwtPresent ? "aiJwt" : "jwt") + (accessFilterPresent ? ",aiAccess" : ",perms[ai:infer]");
        for (String path : new String[]{"/ai/v1/**", "/tab/testAI/**", "/tab/tabAiHistory/**",
                "/tab/tabAiSubscription/**", "/tab/tabAiBase/**", "/video/tabVideoUtil/**"}) {
            protectedChains.put(path, chain);
        }
        for (Map.Entry<String, String> entry : existing.entrySet()) protectedChains.putIfAbsent(entry.getKey(), entry.getValue());
        return protectedChains;
    }
}
