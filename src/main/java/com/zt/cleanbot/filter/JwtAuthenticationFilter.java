package com.zt.cleanbot.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zt.cleanbot.common.Result;
import com.zt.cleanbot.util.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // 登录、刷新Token、WebSocket握手请求不经过此过滤器
        return path.startsWith("/auth/login") || path.startsWith("/auth/refresh") || path.startsWith("/auth/register")
                || path.startsWith("/ws/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        // 1. 获取 Authorization Header
        String header = request.getHeader("Authorization");

        // 2. 检查 Header 是否存在且格式正确
        if (header == null || !header.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        // 3. 提取 Token
        String token = header.substring(7);

        try {
            // 4. 验证 Token
            if (!jwtUtil.validateToken(token) || jwtUtil.isTokenExpired(token)) {
                sendErrorResponse(response, 401, "Token 无效或已过期");
                return;
            }

            // 5. 从 Token 中提取信息
            String username = jwtUtil.getUsernameFromToken(token);
            List<String> permissions = jwtUtil.getPermissionsFromToken(token);

            // 6. 设置 Spring Security 上下文
            List<SimpleGrantedAuthority> authorities = permissions.stream()
                    .map(SimpleGrantedAuthority::new)
                    .collect(Collectors.toList());

            UsernamePasswordAuthenticationToken authentication = new UsernamePasswordAuthenticationToken(username, null,
                    authorities);

            SecurityContextHolder.getContext().setAuthentication(authentication);

            // 7. 将用户信息存入 Request Attribute，供后续使用
            request.setAttribute("userId", jwtUtil.getUserIdFromToken(token));
            request.setAttribute("username", username);
            request.setAttribute("roleId", jwtUtil.getRoleIdFromToken(token));
            request.setAttribute("roleName", jwtUtil.getRoleNameFromToken(token));
            request.setAttribute("permissions", permissions);

        } catch (Exception e) {
            logger.error("Token 验证失败: " + e.getMessage() + ", Token: " + token, e);
            sendErrorResponse(response, 401, "Token 解析失败: " + e.getMessage());
            return;
        }

        // 8. 继续过滤器链
        filterChain.doFilter(request, response);
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Result<Object> result = Result.error(status, message);
        response.getWriter().write(objectMapper.writeValueAsString(result));
    }
}
