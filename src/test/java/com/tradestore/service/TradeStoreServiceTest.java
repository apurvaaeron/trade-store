package com.tradestore.service;

import static com.tradestore.TestTrades.TODAY;
import static com.tradestore.TestTrades.trade;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.tradestore.domain.LowerVersionException;
import com.tradestore.domain.MaturityDateInPastException;
import com.tradestore.domain.Trade;
import com.tradestore.domain.TradeNotFoundException;
import com.tradestore.domain.TradeRepository;
import com.tradestore.domain.TradeValidator;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TradeStoreServiceTest {

    private final Clock clock = Clock.fixed(TODAY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
    private final TradeRepository repository = mock(TradeRepository.class);
    private final TradeStoreService service = new TradeStoreService(repository, new TradeValidator(clock), clock);

    @Test
    void storesNewTradeWithTodayAsCreatedDateAndNotExpired() {
        when(repository.findById("T1")).thenReturn(Optional.empty());
        when(repository.upsertUnlessLowerVersion(any())).thenReturn(1);

        Trade stored = service.store(trade("T1", 1, TODAY.plusDays(10)));

        ArgumentCaptor<Trade> captor = ArgumentCaptor.forClass(Trade.class);
        verify(repository).upsertUnlessLowerVersion(captor.capture());
        assertThat(captor.getValue()).isEqualTo(stored);
        assertThat(stored.getCreatedDate()).isEqualTo(TODAY);
        assertThat(stored.isExpired()).isFalse();
        assertThat(stored.getMaturityDate()).isEqualTo(TODAY.plusDays(10));
    }

    @Test
    void keepsSuppliedCreatedDate() {
        LocalDate created = LocalDate.of(2015, 3, 14);
        when(repository.findById("T2")).thenReturn(Optional.empty());
        when(repository.upsertUnlessLowerVersion(any())).thenReturn(1);

        Trade stored = service.store(trade("T2", 1, "CP-1", "B1", TODAY, created));

        assertThat(stored.getCreatedDate()).isEqualTo(created);
    }

    @Test
    void replacesTradeWithSameVersion() {
        when(repository.findById("T2")).thenReturn(Optional.of(stored("T2", 2)));
        when(repository.upsertUnlessLowerVersion(any())).thenReturn(1);

        Trade stored = service.store(trade("T2", 2, "CP-9", "B7", TODAY, null));

        assertThat(stored.getCounterPartyId()).isEqualTo("CP-9");
        assertThat(stored.getBookId()).isEqualTo("B7");
    }

    @Test
    void rejectsLowerVersionWithoutWriting() {
        when(repository.findById("T2")).thenReturn(Optional.of(stored("T2", 2)));

        assertThatThrownBy(() -> service.store(trade("T2", 1, TODAY)))
                .isInstanceOf(LowerVersionException.class);
        verify(repository, never()).upsertUnlessLowerVersion(any());
    }

    @Test
    void rejectsPastMaturityWithoutWriting() {
        when(repository.findById("T1")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.store(trade("T1", 1, TODAY.minusDays(1))))
                .isInstanceOf(MaturityDateInPastException.class);
        verify(repository, never()).upsertUnlessLowerVersion(any());
    }

    @Test
    void rejectsWhenAConcurrentHigherVersionWinsTheRace() {
        when(repository.findById("T3"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(stored("T3", 5)));
        when(repository.upsertUnlessLowerVersion(any())).thenReturn(0);

        assertThatThrownBy(() -> service.store(trade("T3", 4, TODAY)))
                .isInstanceOf(LowerVersionException.class)
                .hasMessage("Trade T3 version 4 rejected: current version is 5");
    }

    @Test
    void getThrowsWhenTradeUnknown() {
        when(repository.findById("NOPE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get("NOPE")).isInstanceOf(TradeNotFoundException.class)
                .hasMessage("Trade NOPE not found");
    }

    @Test
    void getAndGetAllDelegateToRepository() {
        Trade t = stored("T1", 1);
        when(repository.findById("T1")).thenReturn(Optional.of(t));
        when(repository.findAllByOrderByTradeIdAsc()).thenReturn(List.of(t));

        assertThat(service.get("T1")).isSameAs(t);
        assertThat(service.getAll()).containsExactly(t);
    }

    private static Trade stored(String id, int version) {
        return new Trade(id, version, "CP-1", "B1", TODAY.plusDays(30), TODAY, false);
    }
}
