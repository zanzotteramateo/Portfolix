package com.portfolix.api;

import com.portfolix.api.asset.Asset;
import com.portfolix.api.asset.AssetRepository;
import com.portfolix.api.asset.AssetType;
import com.portfolix.api.auth.oauth.IdentityProvider;
import com.portfolix.api.auth.oauth.UserIdentity;
import com.portfolix.api.auth.oauth.UserIdentityRepository;
import com.portfolix.api.auth.token.EmailToken;
import com.portfolix.api.auth.token.EmailTokenPurpose;
import com.portfolix.api.auth.token.EmailTokenRepository;
import com.portfolix.api.common.Currency;
import com.portfolix.api.portfolio.Portfolio;
import com.portfolix.api.portfolio.PortfolioRepository;
import com.portfolix.api.transaction.Transaction;
import com.portfolix.api.transaction.TransactionRepository;
import com.portfolix.api.transaction.TransactionType;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserRepository;
import com.portfolix.api.user.preference.UserPreferences;
import com.portfolix.api.user.preference.UserPreferencesRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Verifica las reglas que definen las migraciones (cascadas, unicidad, restricciones)
 * contra un Postgres real levantado con Testcontainers.
 */
@DataJpaTest
@Import(TestcontainersConfiguration.class)
class DatabaseSchemaTest {

    @Autowired
    private TestEntityManager em;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private PortfolioRepository portfolioRepository;
    @Autowired
    private AssetRepository assetRepository;
    @Autowired
    private TransactionRepository transactionRepository;
    @Autowired
    private EmailTokenRepository emailTokenRepository;
    @Autowired
    private UserIdentityRepository userIdentityRepository;
    @Autowired
    private UserPreferencesRepository userPreferencesRepository;

    @Test
    void deletingUser_cascadesToPortfoliosAndTransactions() {
        User user = persistUser("juan@email.com");
        Portfolio portfolio = em.persist(new Portfolio(user, "Jubilación"));
        Asset btc = em.persist(new Asset("TESTCOIN", "Test Coin", AssetType.CRYPTO, Currency.USD));
        em.persist(buy(portfolio, btc));
        em.flush();
        em.clear();

        userRepository.deleteById(user.getId());
        userRepository.flush();
        em.clear();

        assertThat(portfolioRepository.count()).isZero();
        assertThat(transactionRepository.count()).isZero();
        assertThat(assetRepository.existsById(btc.getId())).as("el catálogo de activos no se toca").isTrue();
    }

    @Test
    void deletingUser_cascadesToEmailTokens() {
        User user = persistUser("juan@email.com");
        Instant now = Instant.now();
        em.persist(new EmailToken(user, EmailTokenPurpose.EMAIL_VERIFICATION, "hash", now, now.plusSeconds(60)));
        em.flush();
        em.clear();

        userRepository.deleteById(user.getId());
        userRepository.flush();
        em.clear();

        assertThat(emailTokenRepository.count()).isZero();
    }

    @Test
    void deletingUser_cascadesToPreferences() {
        User user = persistUser("juan@email.com");
        em.persist(UserPreferences.defaultsFor(user.getId()));
        em.flush();
        em.clear();

        userRepository.deleteById(user.getId());
        userRepository.flush();
        em.clear();

        assertThat(userPreferencesRepository.count()).isZero();
    }

    @Test
    void anEmailChangeToken_needsTheNewEmail() {
        User user = persistUser("juan@email.com");
        Instant now = Instant.now();
        EmailToken withoutNewEmail =
                new EmailToken(user, EmailTokenPurpose.EMAIL_CHANGE, "hash", now, now.plusSeconds(60));

        assertThatThrownBy(() -> emailTokenRepository.saveAndFlush(withoutNewEmail))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void googleOnlyUser_hasNoPassword_andItsIdentityIsDeletedWithIt() {
        User user = em.persist(new User("google@email.com", null, "Juan Pérez", Instant.now()));
        em.persist(new UserIdentity(user, IdentityProvider.GOOGLE, "google-sub-1"));
        em.flush();
        em.clear();

        userRepository.deleteById(user.getId());
        userRepository.flush();
        em.clear();

        assertThat(userIdentityRepository.count()).isZero();
    }

    @Test
    void aGoogleAccount_canOnlyBeLinkedToOneUser() {
        em.persist(new UserIdentity(persistUser("juan@email.com"), IdentityProvider.GOOGLE, "google-sub-1"));
        em.flush();

        UserIdentity sameGoogleAccount =
                new UserIdentity(persistUser("ana@email.com"), IdentityProvider.GOOGLE, "google-sub-1");
        assertThatThrownBy(() -> userIdentityRepository.saveAndFlush(sameGoogleAccount))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void email_isStoredLowercaseAndUnique() {
        User user = persistUser("  Juan@Email.COM ");
        assertThat(user.getEmail()).isEqualTo("juan@email.com");

        User duplicate = new User("juan@email.com", "hash", "Otro Juan", Instant.now());
        assertThatThrownBy(() -> userRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void portfolioName_isUniquePerUserIgnoringCase() {
        User juan = persistUser("juan@email.com");
        User ana = persistUser("ana@email.com");
        em.persist(new Portfolio(juan, "Jubilación"));

        // Otro usuario puede usar el mismo nombre.
        portfolioRepository.saveAndFlush(new Portfolio(ana, "Jubilación"));

        assertThatThrownBy(() -> portfolioRepository.saveAndFlush(new Portfolio(juan, "JUBILACIÓN")))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void assetWithTransactions_cannotBeDeleted() {
        User user = persistUser("juan@email.com");
        Portfolio portfolio = em.persist(new Portfolio(user, "Jubilación"));
        Asset btc = em.persist(new Asset("TESTCOIN", "Test Coin", AssetType.CRYPTO, Currency.USD));
        em.persist(buy(portfolio, btc));
        em.flush();
        em.clear();

        assertThatThrownBy(() -> {
            assetRepository.deleteById(btc.getId());
            assetRepository.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void transaction_copiesCurrencyFromAssetAndKeepsDecimals() {
        User user = persistUser("juan@email.com");
        Portfolio portfolio = em.persist(new Portfolio(user, "Ahorro Crypto"));
        Asset btc = em.persist(new Asset("TESTCOIN", "Test Coin", AssetType.CRYPTO, Currency.USD));
        Transaction tx = em.persist(new Transaction(portfolio, btc, TransactionType.BUY,
                new BigDecimal("0.00000001"), new BigDecimal("72400.123456789012345678"),
                LocalDate.of(2026, 9, 25), null));
        em.flush();
        em.clear();

        Transaction saved = transactionRepository.findById(tx.getId()).orElseThrow();
        assertThat(saved.getCurrency()).isEqualTo(Currency.USD);
        assertThat(saved.getQuantity()).isEqualByComparingTo("0.00000001");
        assertThat(saved.getPrice()).isEqualByComparingTo("72400.123456789012345678");
    }

    @Test
    void transaction_withNonPositiveQuantity_isRejected() {
        User user = persistUser("juan@email.com");
        Portfolio portfolio = em.persist(new Portfolio(user, "Jubilación"));
        Asset aapl = em.persist(new Asset("TESTCEDEAR", "Test Cedear", AssetType.CEDEAR, Currency.ARS));
        Transaction invalid = new Transaction(portfolio, aapl, TransactionType.BUY,
                BigDecimal.ZERO, new BigDecimal("11200"), LocalDate.of(2026, 9, 22), null);

        assertThatThrownBy(() -> transactionRepository.saveAndFlush(invalid))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private User persistUser(String email) {
        return em.persist(new User(email, "hash", "Juan Pérez", Instant.now()));
    }

    private Transaction buy(Portfolio portfolio, Asset asset) {
        return new Transaction(portfolio, asset, TransactionType.BUY,
                new BigDecimal("0.1"), new BigDecimal("72400"), LocalDate.of(2026, 9, 25), null);
    }
}
