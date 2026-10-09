package com.portfolix.api.portfolio;

import com.portfolix.api.common.exception.BusinessException;
import com.portfolix.api.common.exception.ResourceNotFoundException;
import com.portfolix.api.user.User;
import com.portfolix.api.user.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PortfolioServiceTest {

    private static final Long USER_ID = 1L;

    private final PortfolioRepository repository = mock(PortfolioRepository.class);
    private final UserService userService = mock(UserService.class);
    private final PortfolioService service = new PortfolioService(repository, userService);

    private final User user = new User("juan@email.com", "hash", "Juan Pérez", Instant.now());

    @BeforeEach
    void setUp() {
        when(userService.getReference(USER_ID)).thenReturn(user);
        when(repository.saveAndFlush(any(Portfolio.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void create_trimsName() {
        var created = service.create(USER_ID, "  Ahorro Crypto  ");

        assertThat(created.name()).isEqualTo("Ahorro Crypto");
    }

    @Test
    void create_withDuplicateName_isRejected() {
        when(repository.existsByUserIdAndName(USER_ID, "Jubilación")).thenReturn(true);

        assertThatThrownBy(() -> service.create(USER_ID, "Jubilación"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PortfolioService.DUPLICATE_NAME_MESSAGE);
        verify(repository, never()).saveAndFlush(any());
    }

    @Test
    void create_whenLimitReached_isRejected() {
        when(repository.countByUserId(USER_ID)).thenReturn((long) PortfolioService.MAX_PORTFOLIOS_PER_USER);

        assertThatThrownBy(() -> service.create(USER_ID, "Uno más"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Alcanzaste el máximo de 20 portafolios");
    }

    @Test
    void create_whenDatabaseRejectsDuplicate_returnsBusinessError() {
        // Otro request creó el mismo nombre entre la validación y el insert.
        when(repository.saveAndFlush(any(Portfolio.class))).thenThrow(new DataIntegrityViolationException("uq"));

        assertThatThrownBy(() -> service.create(USER_ID, "Jubilación"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PortfolioService.DUPLICATE_NAME_MESSAGE);
    }

    @Test
    void rename_onlyChangingCase_isAllowed() {
        Portfolio portfolio = new Portfolio(user, "jubilación");
        when(repository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(portfolio));
        when(repository.existsByUserIdAndNameExcluding(USER_ID, "Jubilación", 10L)).thenReturn(false);

        var renamed = service.rename(USER_ID, 10L, "Jubilación");

        assertThat(renamed.name()).isEqualTo("Jubilación");
    }

    @Test
    void rename_toAnotherPortfoliosName_isRejected() {
        when(repository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.of(new Portfolio(user, "Trading")));
        when(repository.existsByUserIdAndNameExcluding(USER_ID, "Jubilación", 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.rename(USER_ID, 10L, "Jubilación"))
                .isInstanceOf(BusinessException.class)
                .hasMessage(PortfolioService.DUPLICATE_NAME_MESSAGE);
    }

    @Test
    void getOwned_withPortfolioOfAnotherUser_throwsNotFound() {
        when(repository.findByIdAndUserId(10L, USER_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOwned(USER_ID, 10L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage(PortfolioService.NOT_FOUND_MESSAGE);
    }
}
