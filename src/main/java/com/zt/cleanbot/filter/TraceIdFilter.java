package com.zt.cleanbot.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;

/**
 * 请求追踪 ID 过滤器
 * 为每个请求生成唯一的 traceId，并在日志中输出
 * 支持分布式链路追踪
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TraceIdFilter.class);

    private static final String TRACE_ID_KEY = "traceId";
    private static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                   HttpServletResponse response,
                                   FilterChain filterChain) throws ServletException, IOException {

        String traceId = null;

        try {
            // 1. 尝试从请求头中获取 traceId（用于分布式追踪）
            traceId = request.getHeader(TRACE_ID_HEADER);

            // 2. 如果请求头中没有，则生成新的 traceId
            if (traceId == null || traceId.isEmpty()) {
                traceId = generateTraceId();
            }

            // 3. 将 traceId 放入 MDC（Mapped Diagnostic Context）
            MDC.put(TRACE_ID_KEY, traceId);

            // 4. 将 traceId 添加到响应头中
            response.setHeader(TRACE_ID_HEADER, traceId);

            // 5. 记录请求信息
            if (logger.isDebugEnabled()) {
                logger.debug("开始处理请求: {} {} from {}",
                    request.getMethod(),
                    request.getRequestURI(),
                    request.getRemoteAddr());
            }

            // 6. 继续执行后续过滤器
            filterChain.doFilter(request, response);

        } finally {
            // 7. 请求处理完成后，清除 MDC 中的 traceId
            if (traceId != null) {
                MDC.remove(TRACE_ID_KEY);
            }
        }
    }

    /**
     * 生成唯一的 traceId
     * 格式：{时间戳后8位}-{随机UUID前8位}
     * 示例：12345678-a1b2c3d4
     */
    private String generateTraceId() {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().replace("-", "");
        return timestamp.substring(timestamp.length() - 8) + "-" + uuid.substring(0, 8);
    }
}
