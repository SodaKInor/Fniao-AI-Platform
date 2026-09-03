package org.jeecg.modules.ai.api.controller;

import org.apache.shiro.SecurityUtils;
import org.jeecg.common.system.vo.LoginUser;
import org.jeecg.modules.ai.application.jobs.AiRequestException;
import org.jeecg.modules.ai.domain.ErrorCode;

public final class JobApiIdentity {
    private JobApiIdentity() { }
    public static String owner() {
        Object principal;
        try {
            org.apache.shiro.subject.Subject subject=SecurityUtils.getSubject();
            principal=subject.isAuthenticated() ? subject.getPrincipal() : null;
        }
        catch (RuntimeException e) { principal=null; }
        if (!(principal instanceof LoginUser) || ((LoginUser)principal).getId()==null)
            throw new AiRequestException(ErrorCode.UNAUTHENTICATED,"Login required");
        return ((LoginUser)principal).getId();
    }
}
