package com.sxxm.stockknock.common.security;

/**
 * JWT 인증 필터
 * 
 * 역할:
 * - HTTP 요청마다 JWT 토큰 검증
 * - 유효한 토큰이면 SecurityContext에 인증 정보 설정
 * - 공개 API 경로는 필터 건너뛰기 (회원가입, 로그인, Swagger)
 * - 인증 실패 시 401 Unauthorized 반환
 */
import com.sxxm.stockknock.common.util.JwtUtil;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    @Autowired
    private JwtUtil jwtUtil;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String path = request.getRequestURI();
        
        // Swagger 및 공개 API 경로는 필터를 건너뜀
        if (path.startsWith("/swagger-ui") || 
            path.startsWith("/v3/api-docs") || 
            path.startsWith("/swagger-resources") ||
            path.startsWith("/webjars") ||
            path.startsWith("/configuration") ||
            path.startsWith("/api/news/recent") || // 뉴스 조회는 공개 API
            path.startsWith("/api/news/") && path.matches("/api/news/\\d+") || // 뉴스 상세 조회는 공개
            path.startsWith("/api/youtube") || // 유튜버 브리핑 공개 API
            path.startsWith("/api/auth") || // 인증 API는 공개
            path.startsWith("/api/stocks") || // 주식 정보도 공개
            path.equals("/favicon.ico")) {
            System.out.println("JWT 필터 건너뜀: " + path);
            chain.doFilter(request, response);
            return;
        }
        
        // /api/news/market-briefing과 /api/news/today-summary는 인증 필요 (필터 통과)
        
        System.out.println("JWT 필터 처리 중: " + path);
        
        final String requestTokenHeader = request.getHeader("Authorization");

        String email = null;
        String jwtToken = null;

        if (requestTokenHeader != null && requestTokenHeader.startsWith("Bearer ")) {
            jwtToken = requestTokenHeader.substring(7);
            try {
                email = jwtUtil.getEmailFromToken(jwtToken);
            } catch (Exception e) {
                logger.error("Unable to get JWT Token", e);
            }
        }

        if (email != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            if (jwtUtil.validateToken(jwtToken)) {
                UsernamePasswordAuthenticationToken usernamePasswordAuthenticationToken =
                        new UsernamePasswordAuthenticationToken(email, null, null);
                usernamePasswordAuthenticationToken
                        .setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(usernamePasswordAuthenticationToken);
            }
        }
        chain.doFilter(request, response);
    }
}

