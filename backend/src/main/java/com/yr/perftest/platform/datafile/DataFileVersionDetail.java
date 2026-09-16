package com.yr.perftest.platform.datafile;

import java.util.List;

public record DataFileVersionDetail(
        DataFileVersion version,
        List<List<String>> previewRows
) {
}
