package com.yr.perftest.platform.api;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.assertj.core.api.Assertions.assertThat;

/** PlatformExceptionHandler 此前无测试类：最小单测钉住 multipart 超限 → 400 的映射形状。 */
class PlatformExceptionHandlerTest {

    @Test
    void mapsMaxUploadSizeExceededToBadRequest() {
        PlatformExceptionHandler handler = new PlatformExceptionHandler();

        ResponseEntity<ApiError> response =
                handler.handleMaxUploadSize(new MaxUploadSizeExceededException(5L * 1024 * 1024));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().code()).isEqualTo("REQUEST_VALIDATION_FAILED");
        assertThat(response.getBody().message()).isEqualTo("uploaded file exceeds the size limit");
    }
}
