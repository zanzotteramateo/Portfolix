package com.portfolix.api.auth.token;

import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static com.portfolix.api.auth.token.EmailTokenPurpose.EMAIL_VERIFICATION;
import static com.portfolix.api.auth.token.EmailTokenPurpose.PASSWORD_RESET;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailTokenServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-02T12:00:00Z");

    private final EmailTokenRepository repository = mock(EmailTokenRepository.class);
    private final EmailTokenProperties properties =
            new EmailTokenProperties(Duration.ofHours(24), Duration.ofHours(1), Duration.ofHours(24), Duration.ofSeconds(60));
    private final EmailTokenService service =
            new EmailTokenService(repository, properties, Clock.fixed(NOW, ZoneOffset.UTC));

    private User user;

    @BeforeEach
    void setUp() {
        user = new User("juan@email.com", "hash", "Juan Pérez", NOW);
        ReflectionTestUtils.setField(user, "id", 1L);
    }

    @Test
    void issue_replacesThePendingTokenAndStoresOnlyTheHash() {
        String value = service.issue(user, EMAIL_VERIFICATION);

        ArgumentCaptor<EmailToken> saved = ArgumentCaptor.forClass(EmailToken.class);
        var order = inOrder(repository);
        order.verify(repository).deletePending(1L, EMAIL_VERIFICATION);
        order.verify(repository).save(saved.capture());

        EmailToken token = saved.getValue();
        assertThat(token.getTokenHash()).isEqualTo(SecureTokens.hash(value)).isNotEqualTo(value);
        assertThat(token.getPurpose()).isEqualTo(EMAIL_VERIFICATION);
        assertThat(token.getCreatedAt()).isEqualTo(NOW);
        assertThat(token.getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void issue_usesTheTtlOfEachPurpose() {
        service.issue(user, PASSWORD_RESET);

        ArgumentCaptor<EmailToken> saved = ArgumentCaptor.forClass(EmailToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void issueEmailChange_storesTheNewEmailInTheToken() {
        service.issueEmailChange(user, "nuevo@email.com");

        ArgumentCaptor<EmailToken> saved = ArgumentCaptor.forClass(EmailToken.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getPurpose()).isEqualTo(EmailTokenPurpose.EMAIL_CHANGE);
        assertThat(saved.getValue().getNewEmail()).isEqualTo("nuevo@email.com");
        assertThat(saved.getValue().getExpiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
    }

    @Test
    void issuedRecently_looksForTokensCreatedWithinTheCooldown() {
        when(repository.existsByUserIdAndPurposeAndCreatedAtAfter(1L, EMAIL_VERIFICATION, NOW.minusSeconds(60)))
                .thenReturn(true);

        assertThat(service.issuedRecently(1L, EMAIL_VERIFICATION)).isTrue();
    }

    @Test
    void find_withUnknownToken_failsOnTokenField() {
        when(repository.findByTokenHashAndPurpose(any(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.find("inventado", EMAIL_VERIFICATION))
                .isInstanceOf(BusinessException.class)
                .hasMessage(EmailTokenService.INVALID_LINK_MESSAGE)
                .extracting("field").isEqualTo("token");
    }

    @Test
    void redeem_marksTheTokenAsUsed() {
        EmailToken token = token(NOW.plusSeconds(1));

        service.redeem(token);

        assertThat(token.getUsedAt()).isEqualTo(NOW);
    }

    @Test
    void redeem_withExpiredToken_fails() {
        EmailToken token = token(NOW);

        assertThatThrownBy(() -> service.redeem(token))
                .isInstanceOf(BusinessException.class)
                .hasMessage(EmailTokenService.EXPIRED_LINK_MESSAGE);
        assertThat(token.isUsed()).isFalse();
    }

    @Test
    void redeem_withUsedToken_fails() {
        EmailToken token = token(NOW.plus(Duration.ofHours(1)));
        token.markUsed(NOW.minusSeconds(30));

        assertThatThrownBy(() -> service.redeem(token))
                .isInstanceOf(BusinessException.class)
                .hasMessage(EmailTokenService.USED_LINK_MESSAGE);
    }

    private EmailToken token(Instant expiresAt) {
        return new EmailToken(user, EMAIL_VERIFICATION, "hash", NOW.minus(Duration.ofHours(1)), expiresAt);
    }
}
