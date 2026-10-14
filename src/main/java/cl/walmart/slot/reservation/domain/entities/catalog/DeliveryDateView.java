package cl.walmart.slot.reservation.domain.entities.catalog;

import cl.walmart.slot.reservation.domain.enums.catalog.DeliveryDateStatus;
import java.time.LocalDate;

public record DeliveryDateView(LocalDate date, String label, DeliveryDateStatus status) { }
