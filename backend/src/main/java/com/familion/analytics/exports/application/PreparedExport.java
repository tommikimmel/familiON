package com.familion.analytics.exports.application;

import org.springframework.http.MediaType;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

public record PreparedExport(
        String filename, MediaType mediaType, StreamingResponseBody body) {}
