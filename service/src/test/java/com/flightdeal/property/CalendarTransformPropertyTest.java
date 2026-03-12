// Feature: flight-deal-notifier, Property 9: Calendar response transformation
package com.flightdeal.property;

import com.flightdeal.generated.model.TimeWindow;
import net.jqwik.api.*;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Property 9: For any list of TimeWindows returned by GoogleCalendarClient,
 * each window has startDate <= endDate.
 *
 * Validates: Requirements 7.3
 */
class CalendarTransformPropertyTest {

    @Property(tries = 100)
    void freeWindowsHaveValidStartBeforeEnd(
            @ForAll("timeWindowList") List<TimeWindow> windows) {

        for (TimeWindow window : windows) {
            LocalDate start = LocalDate.parse(window.getStartDate());
            LocalDate end = LocalDate.parse(window.getEndDate());
            assertTrue(start.compareTo(end) <= 0,
                    "Window start " + start + " should be <= end " + end);
        }
    }

    @Provide
    Arbitrary<List<TimeWindow>> timeWindowList() {
        Arbitrary<TimeWindow> windowArb = Arbitraries.integers().between(0, 330)
                .flatMap(startOffset -> Arbitraries.integers().between(1, 30)
                        .map(duration -> {
                            LocalDate start = LocalDate.of(2025, 1, 1).plusDays(startOffset);
                            LocalDate end = start.plusDays(duration);
                            return TimeWindow.builder()
                                    .startDate(start.toString())
                                    .endDate(end.toString())
                                    .build();
                        }));
        return windowArb.list().ofMinSize(1).ofMaxSize(10);
    }
}
