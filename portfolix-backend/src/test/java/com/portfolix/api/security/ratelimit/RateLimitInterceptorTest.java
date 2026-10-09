package com.portfolix.api.security.ratelimit;

import com.portfolix.api.common.exception.TooManyRequestsException;
import io.github.bucket4j.TimeMeter;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.HandlerMethod;

import java.time.Duration;
import java.util.Map;

import static com.portfolix.api.security.ratelimit.RateLimitPolicy.EMAIL;
import static com.portfolix.api.security.ratelimit.RateLimitPolicy.LOGIN;
import static com.portfolix.api.security.ratelimit.RateLimitPolicy.TOKEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

class RateLimitInterceptorTest {

    private static final String IP = "10.0.0.1";

    private final FakeTimeMeter time = new FakeTimeMeter();
    private final RateLimitInterceptor interceptor = new RateLimitInterceptor(properties(true), time);

    @Test
    void consume_beyondTheCapacity_failsWithTheWaitForTheNextRequest() {
        consumeTimes(LOGIN, IP, 3);

        TooManyRequestsException error = catchThrowableOfType(TooManyRequestsException.class,
                () -> interceptor.consume(LOGIN, IP));

        // 3 por minuto: se recupera un pedido cada 20 segundos.
        assertThat(error.getRetryAfter()).isEqualTo(Duration.ofSeconds(20));
        assertThat(error).hasMessage("Hiciste demasiados pedidos seguidos. Probá de nuevo en 20 segundos");
    }

    @Test
    void requestsComeBackGradually() {
        consumeTimes(LOGIN, IP, 3);

        time.advance(Duration.ofSeconds(20));

        assertThatCode(() -> interceptor.consume(LOGIN, IP)).doesNotThrowAnyException();
        assertThatThrownBy(() -> interceptor.consume(LOGIN, IP)).isInstanceOf(TooManyRequestsException.class);
    }

    @Test
    void eachIpAndPolicyHasItsOwnLimit() {
        consumeTimes(LOGIN, IP, 3);

        assertThatCode(() -> interceptor.consume(LOGIN, "10.0.0.2")).doesNotThrowAnyException();
        assertThatCode(() -> interceptor.consume(EMAIL, IP)).doesNotThrowAnyException();
    }

    @Test
    void disabled_neverLimits() {
        RateLimitInterceptor disabled = new RateLimitInterceptor(properties(false), time);

        assertThatCode(() -> {
            for (int i = 0; i < 10; i++) {
                disabled.consume(LOGIN, IP);
            }
        }).doesNotThrowAnyException();
    }

    @Test
    void preHandle_onlyLimitsEndpointsWithTheAnnotation() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRemoteAddr(IP);
        HandlerMethod limited = new HandlerMethod(new TestEndpoints(), "limited");
        HandlerMethod free = new HandlerMethod(new TestEndpoints(), "free");

        for (int i = 0; i < 5; i++) {
            assertThat(interceptor.preHandle(request, new MockHttpServletResponse(), free)).isTrue();
        }
        consumeTimes(EMAIL, IP, 2);
        assertThatThrownBy(() -> interceptor.preHandle(request, new MockHttpServletResponse(), limited))
                .isInstanceOf(TooManyRequestsException.class);
    }

    private void consumeTimes(RateLimitPolicy policy, String ip, int times) {
        for (int i = 0; i < times; i++) {
            interceptor.consume(policy, ip);
        }
    }

    private static RateLimitProperties properties(boolean enabled) {
        return new RateLimitProperties(enabled, Map.of(
                LOGIN, new RateLimitProperties.Limit(3, Duration.ofMinutes(1)),
                EMAIL, new RateLimitProperties.Limit(2, Duration.ofHours(1)),
                TOKEN, new RateLimitProperties.Limit(5, Duration.ofMinutes(1))));
    }

    /** Reloj de Bucket4j que se adelanta a mano. */
    private static final class FakeTimeMeter implements TimeMeter {

        private long nanos;

        void advance(Duration duration) {
            nanos += duration.toNanos();
        }

        @Override
        public long currentTimeNanos() {
            return nanos;
        }

        @Override
        public boolean isWallClockBased() {
            return false;
        }
    }

    static class TestEndpoints {

        @RateLimited(EMAIL)
        public void limited() {
        }

        public void free() {
        }
    }
}
