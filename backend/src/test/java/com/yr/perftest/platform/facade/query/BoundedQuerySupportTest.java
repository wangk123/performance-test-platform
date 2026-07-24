package com.yr.perftest.platform.facade.query;

import com.yr.perftest.platform.agent.contract.ApiResponse;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BoundedQuerySupportTest {
    @Test
    void pageBudgetProvidesExpectedDefaults() {
        PageBudget budget = PageBudget.defaults();

        assertThat(budget.maxItems()).isEqualTo(1000);
        assertThat(budget.maxBytes()).isEqualTo(1_048_576);
        assertThat(budget.maxMillis()).isEqualTo(3000);
    }

    @Test
    void pageBudgetRejectsNegativeMaxItems() {
        PageBudget budget = new PageBudget(-1, 1_048_576, 3000);

        assertThatThrownBy(budget::validate)
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void cursorCodecRoundTripsOpaquePayload() {
        CursorCodec codec = new CursorCodec();
        String payload = "lastId=42";

        String cursor = codec.encode(payload);

        assertThat(cursor).isNotEqualTo(payload);
        assertThat(codec.decode(cursor)).isEqualTo(payload);
    }

    @Test
    void availabilityRepresentsMissingData() {
        Availability availability = new Availability(
                false,
                Instant.parse("2026-07-24T08:00:00Z"),
                Instant.parse("2026-07-24T08:05:00Z"),
                null,
                false,
                "failure-samples.db",
                Availability.MissingReason.NO_DATA
        );

        assertThat(availability.present()).isFalse();
        assertThat(availability.missingReason()).isEqualTo(Availability.MissingReason.NO_DATA);
    }

    @Test
    void boundedPageCarriesItemsAndAvailability() {
        Availability availability = new Availability(true, null, null, null, true, "source", null);
        BoundedPage<String> page = new BoundedPage<>(
                List.of("first"),
                true,
                "next",
                List.of("budget:items"),
                availability
        );

        assertThat(page.items()).containsExactly("first");
        assertThat(page.availability()).isEqualTo(availability);
    }

    @Test
    void pagedResponseFillsPaginationFields() {
        ApiResponse<List<String>> response = ApiResponse.paged(
                "req-1",
                "1",
                List.of("first"),
                List.of("budget:items"),
                true,
                "next"
        );

        assertThat(response.data()).containsExactly("first");
        assertThat(response.error()).isNull();
        assertThat(response.warnings()).containsExactly("budget:items");
        assertThat(response.truncated()).isTrue();
        assertThat(response.nextCursor()).isEqualTo("next");
    }
}
