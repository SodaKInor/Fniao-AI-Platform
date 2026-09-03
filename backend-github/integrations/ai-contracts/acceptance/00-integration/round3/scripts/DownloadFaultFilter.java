package org.jeecg.modules.ai.acceptance;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import javax.servlet.*;
import javax.servlet.http.*;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.*;

/** Test-only network fault, applied after the real owned asset controller opens content. */
@Configuration
public class DownloadFaultFilter {
    @Bean public FilterRegistrationBean<Filter> acceptanceDownloadFault() {
        Filter filter=new Filter() {
            public void init(FilterConfig config) { }
            public void destroy() { }
            public void doFilter(ServletRequest req,ServletResponse res,FilterChain chain) throws IOException,ServletException {
                String mode="normal";
                try { mode=new String(Files.readAllBytes(Paths.get("/acceptance/control/download-mode")),StandardCharsets.UTF_8).trim(); }
                catch (IOException ignored) { }
                if (mode.equals("normal")) { chain.doFilter(req,res); return; }
                final boolean json=mode.equals("json");
                HttpServletResponse real=(HttpServletResponse)res;
                if (!json) real.setHeader("Connection","close");
                chain.doFilter(req,new HttpServletResponseWrapper(real) {
                    private ServletOutputStream stream;
                    @Override public ServletOutputStream getOutputStream() throws IOException {
                        if (stream!=null) return stream;
                        ServletOutputStream target=real.getOutputStream();
                        stream=new ServletOutputStream() {
                            private int bytes;
                            public boolean isReady() { return target.isReady(); }
                            public void setWriteListener(WriteListener listener) { target.setWriteListener(listener); }
                            public void write(int value) throws IOException {
                                if (json) {
                                    if (bytes++==0) {
                                        real.reset(); real.setStatus(404); real.setContentType("application/json");
                                        target.write("{\"success\":false,\"code\":404,\"message\":\"Acceptance download failure\"}".getBytes(StandardCharsets.UTF_8));
                                    }
                                    return;
                                }
                                if (bytes++>=12) { real.flushBuffer(); throw new IOException("Acceptance browser transfer interrupted"); }
                                target.write(value);
                            }
                            public void close() throws IOException { target.close(); }
                        };
                        return stream;
                    }
                });
            }
        };
        FilterRegistrationBean<Filter> registration=new FilterRegistrationBean<>(filter);
        registration.addUrlPatterns("/ai/v1/assets/*"); registration.setOrder(Integer.MAX_VALUE);
        return registration;
    }
}
