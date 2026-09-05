package org.jeecg.modules.ai.legacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.apache.shiro.SecurityUtils;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.web.servlet.AdviceFilter;
import org.jeecg.common.api.vo.Result;
import org.jeecg.modules.ai.job.api.dto.ErrorDto;
import org.jeecg.modules.ai.job.domain.ErrorCode;
import org.springframework.web.util.UrlPathHelper;

/** Runs inside Shiro after JWT. Management reads and owned history do not require ai:infer. */
public final class AiAccessFilter extends AdviceFilter {
    private final ObjectMapper mapper = new ObjectMapper();
    private final UrlPathHelper paths = new UrlPathHelper();
    private final Set<String> legacy = new HashSet<>(Arrays.asList(
            "/tab/tabAiHistory/addIdentify", "/tab/tabAiHistory/addIdentifyClose", "/tab/tabAiHistory/addAudio",
            "/video/tabVideoUtil/startVideoUtil", "/video/tabVideoUtil/stopVideoUtil", "/tab/tabAiSubscription/subInfo",
            "/maxkb/tabMaxkbModel/testConnect"));
    private final Set<String> submissions = new HashSet<>(Arrays.asList("/ai/v1/assets", "/ai/v1/infer", "/ai/v1/jobs"));

    @Override protected boolean preHandle(ServletRequest req, ServletResponse res) throws Exception {
        HttpServletRequest request = (HttpServletRequest) req;
        HttpServletResponse response = (HttpServletResponse) res;
        Subject subject = SecurityUtils.getSubject();
        if (subject.getPrincipal() == null || !subject.isAuthenticated()) {
            return reject(response, 401, ErrorCode.UNAUTHENTICATED, "请先登录");
        }
        String path = paths.getPathWithinApplication(request);
        while (path.endsWith("/") && path.length() > 1) path = path.substring(0, path.length() - 1);
        boolean stopped = legacy.contains(path) || path.equals("/tab/testAI") || path.startsWith("/tab/testAI/");
        boolean submission = "POST".equals(request.getMethod()) && submissions.contains(path);
        if ((stopped || submission) && !subject.isPermitted("ai:infer")) {
            return reject(response, 403, ErrorCode.FORBIDDEN, "没有 AI 执行权限");
        }
        if (stopped) return reject(response, 409, ErrorCode.CAPABILITY_UNAVAILABLE, "旧 AI 执行入口已停用");
        return true;
    }

    private boolean reject(HttpServletResponse response, int status, ErrorCode code, String message) throws Exception {
        ErrorDto error = new ErrorDto();
        error.setErrorCode(code);
        error.setMessage(message);
        error.setSimulated(false);
        Result<ErrorDto> body = Result.error(status, message);
        body.setResult(error);
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(response.getWriter(), body);
        return false;
    }
}
