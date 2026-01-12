package org.openlogisticsfoundation.ecmr.config.metrics;

import java.util.concurrent.atomic.AtomicLong;

import org.openlogisticsfoundation.ecmr.api.model.EcmrStatus;
import org.openlogisticsfoundation.ecmr.domain.models.EcmrType;
import org.openlogisticsfoundation.ecmr.persistence.repositories.EcmrRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class EcmrCountMetric {

    private final EcmrRepository ecmrRepository;
    private final AtomicLong newCount = new AtomicLong();
    private final AtomicLong loadingCount = new AtomicLong();
    private final AtomicLong inTransportCount = new AtomicLong();
    private final AtomicLong deliveredCount = new AtomicLong();
    private final AtomicLong archivedCount = new AtomicLong();

    public EcmrCountMetric(MeterRegistry meterRegistry,
            EcmrRepository ecmrRepository) {
        this.ecmrRepository = ecmrRepository;

        Gauge.builder("ecmr_count", newCount, AtomicLong::get).tag("status", "new").tag("type", "ecmr").register(meterRegistry);
        Gauge.builder("ecmr_count", loadingCount, AtomicLong::get).tag("status", "loading").tag("type", "ecmr").register(meterRegistry);
        Gauge.builder("ecmr_count", inTransportCount, AtomicLong::get).tag("status", "in_transport").tag("type", "ecmr").register(meterRegistry);
        Gauge.builder("ecmr_count", deliveredCount, AtomicLong::get).tag("status", "delivered").tag("type", "ecmr").register(meterRegistry);
        Gauge.builder("ecmr_count", archivedCount, AtomicLong::get).tag("type", "archived").register(meterRegistry);
    }

    @Scheduled(fixedDelayString = "PT60S")
    public void updateMetrics() {
        newCount.set(ecmrRepository.countByTypeAndEcmrStatus(EcmrType.ECMR, EcmrStatus.NEW));
        loadingCount.set(ecmrRepository.countByTypeAndEcmrStatus(EcmrType.ECMR, EcmrStatus.LOADING));
        inTransportCount.set(ecmrRepository.countByTypeAndEcmrStatus(EcmrType.ECMR, EcmrStatus.IN_TRANSPORT));
        deliveredCount.set(ecmrRepository.countByTypeAndEcmrStatus(EcmrType.ECMR, EcmrStatus.DELIVERED));
        archivedCount.set(ecmrRepository.countByType(EcmrType.ARCHIVED));
    }
}