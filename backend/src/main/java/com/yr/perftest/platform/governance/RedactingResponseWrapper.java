package com.yr.perftest.platform.governance;

import jakarta.servlet.ServletOutputStream;
import jakarta.servlet.WriteListener;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;

/**
 * 缓冲 agent 面响应体，供输出边界脱敏（T10）。不直接向真实响应写入，链结束后由过滤器统一写出。
 */
public class RedactingResponseWrapper extends HttpServletResponseWrapper {
    private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();
    private PrintWriter writer;

    public RedactingResponseWrapper(HttpServletResponse response) {
        super(response);
    }

    @Override
    public ServletOutputStream getOutputStream() {
        return new ServletOutputStream() {
            @Override
            public boolean isReady() {
                return true;
            }

            @Override
            public void setWriteListener(WriteListener writeListener) {
                // 同步缓冲，无需异步写监听
            }

            @Override
            public void write(int b) {
                buffer.write(b);
            }

            @Override
            public void write(byte[] bytes, int off, int len) {
                buffer.write(bytes, off, len);
            }
        };
    }

    @Override
    public PrintWriter getWriter() {
        if (writer == null) {
            writer = new PrintWriter(new OutputStreamWriter(buffer, StandardCharsets.UTF_8), true);
        }
        return writer;
    }

    @Override
    public void flushBuffer() {
        // 延迟到过滤器统一写出，避免提前提交真实响应
    }

    @Override
    public void resetBuffer() {
        buffer.reset();
        if (writer != null) {
            writer.flush();
            buffer.reset();
        }
    }

    @Override
    public void sendError(int sc) {
        setStatus(sc);
    }

    @Override
    public void sendError(int sc, String msg) {
        setStatus(sc);
    }

    public byte[] getContentAsByteArray() {
        if (writer != null) {
            writer.flush();
        }
        return buffer.toByteArray();
    }
}
