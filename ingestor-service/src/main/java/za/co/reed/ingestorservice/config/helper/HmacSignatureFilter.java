package za.co.reed.ingestorservice.config.helper;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StreamUtils;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;

@Slf4j
public class HmacSignatureFilter implements Filter {
    public static final String SIGNATURE_HEADER = "X-Transaction-Aggregator-Signature";
    private static final String SIGNATURE_PREFIX = "sha256=";
    private final String secret;

    public HmacSignatureFilter(String secret) {
        this.secret = secret;
    }

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        String signatureHeader = httpRequest.getHeader(SIGNATURE_HEADER);
        if (signatureHeader == null || !signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Missing or malformed signature header — path={}",
                    httpRequest.getRequestURI());
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing X-Transaction-Aggregator-Signature header");
            return;
        }

        // Cache the body so it can be read again by Jackson
        CachingRequestWrapper cachingRequest = new CachingRequestWrapper(httpRequest);
        byte[] body = StreamUtils.copyToByteArray(cachingRequest.getInputStream());

        String expectedSignature = SIGNATURE_PREFIX + computeHmac(body);
        String providedSignature = signatureHeader;

        if (!timingSafeEquals(expectedSignature, providedSignature)) {
            log.warn("Invalid HMAC signature — path={} provided={}", httpRequest.getRequestURI(), providedSignature);
            httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid signature");
            return;
        }

        chain.doFilter(cachingRequest, response);
    }

    private String computeHmac(byte[] payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC", e);
        }
    }

    /** Timing-safe string comparison to prevent timing attacks. */
    private boolean timingSafeEquals(String a, String b) {
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return java.security.MessageDigest.isEqual(aBytes, bBytes);
    }
}