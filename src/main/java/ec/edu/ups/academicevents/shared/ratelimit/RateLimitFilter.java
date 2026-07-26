package ec.edu.ups.academicevents.shared.ratelimit;

import ec.edu.ups.academicevents.shared.exception.ApiErrorResponse;
import ec.edu.ups.academicevents.shared.exception.ErrorCode;
import ec.edu.ups.academicevents.shared.exception.InvalidTokenException;
import ec.edu.ups.academicevents.shared.security.JwtService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH = "/api/auth/login";
    private static final String REGISTER_PATH = "/api/auth/register";
    private static final String REPORTS_PATH_PREFIX = "/api/reports/";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String FORWARDED_FOR_HEADER = "X-Forwarded-For";
    private static final String EMAIL_FIELD = "email";
    private static final String RATE_LIMIT_MESSAGE = "Ha excedido el límite de solicitudes. Intente nuevamente más tarde.";

    private final RedisRateLimiter rateLimiter;
    private final JwtService jwtService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip = clientIp(request);

        if (HttpMethod.POST.matches(request.getMethod()) && LOGIN_PATH.equals(path)) {
            handleLogin(request, response, filterChain, ip);
            return;
        }

        if (HttpMethod.POST.matches(request.getMethod()) && REGISTER_PATH.equals(path)) {
            RateLimitResult result = rateLimiter.tryConsume(RateLimitPolicy.REGISTER_IP, ip);
            proceedOrReject(request, response, filterChain, result);
            return;
        }

        if (path.startsWith(REPORTS_PATH_PREFIX)) {
            Long userId = resolveUserId(request);
            RateLimitResult result = userId != null
                    ? rateLimiter.tryConsume(RateLimitPolicy.REPORTS_USER, String.valueOf(userId))
                    : rateLimiter.tryConsume(RateLimitPolicy.PUBLIC_IP, ip);
            proceedOrReject(request, response, filterChain, result);
            return;
        }

        Long userId = resolveUserId(request);
        RateLimitResult result = userId != null
                ? rateLimiter.tryConsume(RateLimitPolicy.AUTHENTICATED_USER, String.valueOf(userId))
                : rateLimiter.tryConsume(RateLimitPolicy.PUBLIC_IP, ip);
        proceedOrReject(request, response, filterChain, result);
    }

    private void handleLogin(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain, String ip)
            throws ServletException, IOException {
        CachedBodyHttpServletRequest wrappedRequest = new CachedBodyHttpServletRequest(request);

        RateLimitResult ipResult = rateLimiter.tryConsume(RateLimitPolicy.LOGIN_IP, ip);
        if (!ipResult.allowed()) {
            rejectWithTooManyRequests(request, response, ipResult.ttlSeconds());
            return;
        }

        String email = extractEmail(wrappedRequest);
        if (email != null) {
            RateLimitResult emailResult = rateLimiter.tryConsume(RateLimitPolicy.LOGIN_EMAIL, email.toLowerCase());
            if (!emailResult.allowed()) {
                rejectWithTooManyRequests(request, response, emailResult.ttlSeconds());
                return;
            }
        }

        filterChain.doFilter(wrappedRequest, response);
    }

    private void proceedOrReject(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain,
            RateLimitResult result) throws ServletException, IOException {
        if (!result.allowed()) {
            rejectWithTooManyRequests(request, response, result.ttlSeconds());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Long resolveUserId(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return null;
        }
        try {
            Claims claims = jwtService.parseAndValidate(header.substring(BEARER_PREFIX.length()));
            return jwtService.extractUserId(claims);
        } catch (InvalidTokenException ex) {
            return null;
        }
    }

    private String extractEmail(CachedBodyHttpServletRequest request) {
        try {
            JsonNode root = objectMapper.readTree(request.getCachedBody());
            JsonNode emailNode = root.get(EMAIL_FIELD);
            return emailNode == null ? null : emailNode.asString();
        } catch (Exception ex) {
            return null;
        }
    }

    private void rejectWithTooManyRequests(HttpServletRequest request, HttpServletResponse response, long ttlSeconds)
            throws IOException {
        ApiErrorResponse body = ApiErrorResponse.of(
                HttpStatus.TOO_MANY_REQUESTS.value(),
                HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                ErrorCode.RATE_LIMIT_EXCEEDED,
                RATE_LIMIT_MESSAGE,
                request.getRequestURI());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(ttlSeconds));
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), body);
    }

    private String clientIp(HttpServletRequest request) {
        String forwardedFor = request.getHeader(FORWARDED_FOR_HEADER);
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private static final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

        private final byte[] cachedBody;

        private CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
            super(request);
            this.cachedBody = StreamUtils.copyToByteArray(request.getInputStream());
        }

        private byte[] getCachedBody() {
            return cachedBody;
        }

        @Override
        public ServletInputStream getInputStream() {
            return new CachedBodyServletInputStream(cachedBody);
        }

        @Override
        public BufferedReader getReader() {
            return new BufferedReader(
                    new InputStreamReader(new ByteArrayInputStream(cachedBody), StandardCharsets.UTF_8));
        }
    }

    private static final class CachedBodyServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream buffer;

        private CachedBodyServletInputStream(byte[] cachedBody) {
            this.buffer = new ByteArrayInputStream(cachedBody);
        }

        @Override
        public boolean isFinished() {
            return buffer.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int read() {
            return buffer.read();
        }
    }
}
