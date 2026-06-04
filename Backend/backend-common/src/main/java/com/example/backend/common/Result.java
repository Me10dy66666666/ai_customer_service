package com.example.backend.common;

import lombok.Getter;

@Getter
public class Result<T> {
    private static final int CODE_SUCCESS = 200;
    private static final int CODE_ERROR = 500;

    private final int code;
    private final String message;
    private final T data;

    private Result(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    public static <T> Result<T> success() {
        return new Result<>(CODE_SUCCESS, "Success", null);
    }

    public static <T> Result<T> success(T data) {
        return new Result<>(CODE_SUCCESS, "Success", data);
    }

    public static <T> Result<T> error(int code, String message) {
        return new Result<>(code, message, null);
    }

    public static <T> Result<T> error(String message) {
        return new Result<>(CODE_ERROR, message, null);
    }
}
