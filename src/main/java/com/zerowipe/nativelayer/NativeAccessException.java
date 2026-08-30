package com.zerowipe.nativelayer;

/**
 * A native Windows storage API call failed. Carries the raw Win32 error
 * code (from {@code GetLastError}) where one is available; 0 otherwise.
 */
public class NativeAccessException extends RuntimeException {

    private final int win32ErrorCode;

    public NativeAccessException(String message, int win32ErrorCode) {
        super(message + " (Win32 error " + win32ErrorCode + ")");
        this.win32ErrorCode = win32ErrorCode;
    }

    public int win32ErrorCode() {
        return win32ErrorCode;
    }
}
