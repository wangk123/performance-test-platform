package com.yr.perftest.platform.evidence;

import com.yr.perftest.platform.execution.ExecutionValidationException;
import com.yr.perftest.platform.facade.query.Availability;

public final class EvidenceSourceSupport {
    private EvidenceSourceSupport() {
    }

    public static boolean supports(CorrelationKey key) {
        return key != null
                && key.executionId() > 0
                && key.from() != null
                && key.to() != null
                && !key.from().isAfter(key.to());
    }

    public static boolean isDeletedExecution(RuntimeException exception) {
        return exception instanceof ExecutionValidationException
                && exception.getMessage() != null
                && exception.getMessage().contains("execution does not exist");
    }

    public static EvidenceSummary deleted(CorrelationKey key, String sourceType, String sourceClock) {
        String sourceRef = "execution:" + key.executionId();
        Availability availability = new Availability(
                false,
                null,
                null,
                null,
                false,
                sourceRef,
                Availability.MissingReason.DELETED
        );
        return new EvidenceSummary(
                key,
                sourceType,
                availability,
                java.util.Map.of(),
                sourceRef,
                sourceClock
        );
    }

    public static Availability filteredAvailability(Availability source, boolean present) {
        if (present || !source.present()) {
            return source;
        }
        return new Availability(
                false,
                null,
                null,
                source.granularity(),
                source.truncated(),
                source.sourceRef(),
                Availability.MissingReason.NO_DATA
        );
    }
}
