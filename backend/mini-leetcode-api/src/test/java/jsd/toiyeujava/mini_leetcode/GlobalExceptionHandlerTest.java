package jsd.toiyeujava.mini_leetcode;

import jsd.toiyeujava.mini_leetcode.exception.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;

import static org.assertj.core.api.Assertions.assertThat;

class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("handleAuthenticationException: returns 401 ProblemDetail")
    void handleAuthenticationException() {
        ProblemDetail problem = handler.handleAuthenticationException(new BadCredentialsException("Bad creds"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        assertThat(problem.getTitle()).isEqualTo("Authentication Failed");
        assertThat(problem.getDetail()).isEqualTo("Bad creds");
        assertThat(problem.getProperties()).containsKey("timestamp");
    }

    @Test
    @DisplayName("handleAccessDeniedException: returns 403 ProblemDetail")
    void handleAccessDeniedException() {
        ProblemDetail problem = handler.handleAccessDeniedException(new AccessDeniedException("No permission"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
        assertThat(problem.getTitle()).isEqualTo("Forbidden");
        assertThat(problem.getDetail()).contains("Access denied");
    }

    @Test
    @DisplayName("handleNotFoundException: returns 404 ProblemDetail")
    void handleNotFoundException() {
        ProblemDetail problem = handler.handleNotFoundException(new ResourceNotFoundException("Not found"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.NOT_FOUND.value());
        assertThat(problem.getTitle()).isEqualTo("Resource Not Found");
        assertThat(problem.getDetail()).isEqualTo("Not found");
    }

    @Test
    @DisplayName("handleDuplicateException: returns 409 ProblemDetail")
    void handleDuplicateException() {
        ProblemDetail problem = handler.handleDuplicateException(new DuplicateResourceException("Already exists"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.CONFLICT.value());
        assertThat(problem.getTitle()).isEqualTo("Resource Conflict");
        assertThat(problem.getDetail()).isEqualTo("Already exists");
    }

    @Test
    @DisplayName("handleIllegalStateOrArgument: returns 400 ProblemDetail")
    void handleIllegalStateOrArgument() {
        ProblemDetail problem = handler.handleIllegalStateOrArgument(new IllegalArgumentException("Invalid state"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
        assertThat(problem.getDetail()).isEqualTo("Invalid state");
    }

    @Test
    @DisplayName("handleMethodNotSupported: returns 405 ProblemDetail")
    void handleMethodNotSupported() {
        ProblemDetail problem = handler.handleMethodNotSupported(new org.springframework.web.HttpRequestMethodNotSupportedException("POST"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.METHOD_NOT_ALLOWED.value());
        assertThat(problem.getTitle()).isEqualTo("Method Not Allowed");
    }

    @Test
    @DisplayName("handleIllegalStateOrArgument: handles HttpMessageNotReadableException with custom message")
    void handleHttpMessageNotReadableException() {
        ProblemDetail problem = handler.handleIllegalStateOrArgument(new org.springframework.http.converter.HttpMessageNotReadableException("Invalid JSON"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getDetail()).isEqualTo("Malformed JSON request body");
    }

    @Test
    @DisplayName("handleMediaTypeNotSupported: returns 415 ProblemDetail")
    void handleMediaTypeNotSupported() {
        ProblemDetail problem = handler.handleMediaTypeNotSupported(new org.springframework.web.HttpMediaTypeNotSupportedException("text/plain"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
        assertThat(problem.getTitle()).isEqualTo("Unsupported Media Type");
    }

    @Test
    @DisplayName("handleMaxUploadSizeExceeded: returns 413 ProblemDetail")
    void handleMaxUploadSizeExceeded() {
        ProblemDetail problem = handler.handleMaxUploadSizeExceeded(new org.springframework.web.multipart.MaxUploadSizeExceededException(1024L));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
        assertThat(problem.getTitle()).isEqualTo("Payload Too Large");
    }

    @Test
    @DisplayName("handleIllegalStateOrArgument: handles MissingServletRequestPartException as 400 Bad Request")
    void handleMissingServletRequestPartException() {
        ProblemDetail problem = handler.handleIllegalStateOrArgument(new org.springframework.web.multipart.support.MissingServletRequestPartException("inputFile"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST.value());
        assertThat(problem.getTitle()).isEqualTo("Bad Request");
    }

    @Test
    @DisplayName("handleGenericException: returns 500 ProblemDetail")
    void handleGenericException() {
        ProblemDetail problem = handler.handleGenericException(new RuntimeException("Server error"));
        assertThat(problem.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR.value());
        assertThat(problem.getTitle()).isEqualTo("Internal Server Error");
        assertThat(problem.getDetail()).isEqualTo("An unexpected error occurred");
    }
}
