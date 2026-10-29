package com.msds.nuser.exceptions;

import com.msds.nuser.dtos.ApiResponseDto;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(value = Exception.class)
    public ResponseEntity<ApiResponseDto<ErrorResponseDto>> handleException(Exception exception) {
        logger.error("Unexpected error while processing the request.", exception);

        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;

        return ResponseEntity.status(status)
                .body(new ApiResponseDto<>(
                        status.value(),
                        new ErrorResponseDto("An unexpected error occurred.")
                ));

    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponseDto<ErrorResponseDto>> handleDataIntegrity(
            DataIntegrityViolationException exception
    ){
        Throwable cause = exception;

        while (cause != null) {
            if (cause instanceof ConstraintViolationException violation
                    && "users_email_key".equals(violation.getConstraintName())) {

                logger.warn("User creation rejected: email already registered.");

                HttpStatus status = HttpStatus.CONFLICT;

                return ResponseEntity.status(status)
                        .body(new ApiResponseDto<>(
                                status.value(),
                                new ErrorResponseDto("Email already registered.")
                        ));
            }

            cause = cause.getCause();
        }

        return handleException(exception);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponseDto<ErrorResponseDto>> handleValidation(
            MethodArgumentNotValidException exception
    ) {
        String message = exception.getBindingResult()
                .getFieldErrors()
                .getFirst()
                .getDefaultMessage();

        return ResponseEntity.badRequest()
                .body(new ApiResponseDto<>(
                        400,
                        new ErrorResponseDto(message)
                ));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponseDto<ErrorResponseDto>> handleNotFound(
            ResourceNotFoundException exception
    ) {
        HttpStatus status = HttpStatus.NOT_FOUND;

        return ResponseEntity.status(status)
                .body(new ApiResponseDto<>(
                        status.value(),
                        new ErrorResponseDto(exception.getMessage())
                ));
    }
}
