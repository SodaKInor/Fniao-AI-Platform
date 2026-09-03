package org.jeecg.modules.ai.legacy;

import com.fasterxml.jackson.databind.ObjectMapper;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;
import org.jeecg.common.api.vo.Result;
import org.jeecg.config.shiro.filters.JwtFilter;
import org.jeecg.modules.ai.api.dto.ErrorDto;
import org.jeecg.modules.ai.domain.ErrorCode;

/** Same JWT/realm authentication and CORS; only AI's rejection representation differs. */
public final class AiJwtFilter extends JwtFilter {
    private final ObjectMapper mapper=new ObjectMapper();
    public AiJwtFilter(boolean allowOrigin) { super(allowOrigin); }

    @Override protected boolean isAccessAllowed(ServletRequest request,ServletResponse response,Object mappedValue) {
        try { return executeLogin(request,response); }
        catch (Exception failure) {
            HttpServletResponse http=(HttpServletResponse)response;
            ErrorDto error=new ErrorDto();
            error.setErrorCode(ErrorCode.UNAUTHENTICATED); error.setMessage("请先登录");
            error.setSimulated(false);
            Result<ErrorDto> body=Result.error(401,"请先登录"); body.setResult(error);
            http.setStatus(401); http.setContentType("application/json;charset=UTF-8");
            try { mapper.writeValue(http.getWriter(),body); }
            catch (java.io.IOException ignored) { /* Authentication stays rejected if the client disconnects. */ }
            return false;
        }
    }
}
