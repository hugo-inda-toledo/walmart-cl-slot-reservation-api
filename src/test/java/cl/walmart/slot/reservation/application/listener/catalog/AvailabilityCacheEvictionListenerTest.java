package cl.walmart.slot.reservation.application.listener.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import cl.walmart.slot.reservation.domain.entities.catalog.*;
import cl.walmart.slot.reservation.domain.enums.catalog.*;
import cl.walmart.slot.reservation.domain.exception.catalog.*;
import cl.walmart.slot.reservation.domain.repository.catalog.*;
import cl.walmart.slot.reservation.domain.service.catalog.*;
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

class AvailabilityCacheEvictionListenerTest {
    @Test
    void declaresAfterCommitAndEvictsEveryChangedDay() throws Exception {
        var evicted = new ArrayList<ZoneDate>();
        AvailabilityCacheRepository cache = new AvailabilityCacheRepository() {
            public Map<LocalDate, ZoneDayAvailability> findAll(CatalogZoneId z, Collection<LocalDate> d) { return Map.of(); }
            public void saveAll(Collection<ZoneDayAvailability> d) { }
            public void evict(ZoneDate value) { evicted.add(value); }
        };
        var day = new ZoneDate(new CatalogZoneId(UUID.randomUUID()), LocalDate.of(2031, 2, 3));
        Method method = AvailabilityCacheEvictionListener.class.getDeclaredMethod("on", ZoneDaysChangedEvent.class);
        assertThat(method.getAnnotation(TransactionalEventListener.class).phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
        new AvailabilityCacheEvictionListener(cache).on(new ZoneDaysChangedEvent(Set.of(day)));
        assertThat(evicted).containsExactly(day);
    }

    @Test
    void doesNotEvictWhenThePublishingTransactionRollsBack() {
        var evicted = new ArrayList<ZoneDate>();
        AvailabilityCacheRepository cache = new RecordingCache(evicted);
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("availabilityCacheRepository", cache);
            context.register(AvailabilityCacheEvictionListener.class);
            context.refresh();
            var changed = new ZoneDate(new CatalogZoneId(UUID.randomUUID()), LocalDate.of(2031, 2, 3));
            var template = new TransactionTemplate(new TestTransactionManager());
            template.executeWithoutResult(status -> {
                context.publishEvent(new ZoneDaysChangedEvent(Set.of(changed)));
                status.setRollbackOnly();
            });
            assertThat(evicted).isEmpty();
        }
    }

    private record RecordingCache(List<ZoneDate> evicted) implements AvailabilityCacheRepository {
        public Map<LocalDate, ZoneDayAvailability> findAll(CatalogZoneId z, Collection<LocalDate> d) { return Map.of(); }
        public void saveAll(Collection<ZoneDayAvailability> d) { }
        public void evict(ZoneDate value) { evicted.add(value); }
    }
    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {
        protected Object doGetTransaction() { return new Object(); }
        protected void doBegin(Object transaction, org.springframework.transaction.TransactionDefinition definition) { }
        protected void doCommit(DefaultTransactionStatus status) { }
        protected void doRollback(DefaultTransactionStatus status) { }
    }
}
