package com.aiot.domain.port;

/**
 * 缓冲区异常基类。
 * <p>
 * VehicleStateBuffer 和 PhysiologicalDataBuffer 共享的异常类型。
 * </p>
 */
public sealed class BufferException extends Exception permits
        BufferException.WindowNotCoveredException,
        BufferException.BufferUnavailableException {

    public BufferException(String message) {
        super(message);
    }

    /**
     * 请求窗口超出缓冲保留范围。
     */
    public static final class WindowNotCoveredException extends BufferException {
        public WindowNotCoveredException(String message) {
            super(message);
        }
    }

    /**
     * 缓冲未初始化或采集组件异常。
     */
    public static final class BufferUnavailableException extends BufferException {
        public BufferUnavailableException(String message) {
            super(message);
        }
    }
}
