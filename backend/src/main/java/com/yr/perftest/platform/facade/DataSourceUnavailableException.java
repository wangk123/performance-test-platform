package com.yr.perftest.platform.facade;

import com.yr.perftest.platform.facade.query.Availability;

public class DataSourceUnavailableException extends RuntimeException {
    private final Availability availability;

    public DataSourceUnavailableException(String message, Throwable cause) {
        this(message, cause, null);
    }

    public DataSourceUnavailableException(String message, Throwable cause, Availability availability) {
        super(message, cause);
        this.availability = availability;
    }

    public Availability availability() {
        return availability;
    }
}
