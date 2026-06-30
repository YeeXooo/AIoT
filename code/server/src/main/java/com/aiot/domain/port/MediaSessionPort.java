package com.aiot.domain.port;

import com.aiot.domain.shared.AccountId;
import com.aiot.domain.shared.DriverId;

/**
 * 音视频会话端口。
 * <p>
 * 负责家属远程对讲/视频监控的会话建立与拆除。
 * 依托 SparkRTC 实现。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md 决策 19 端口 3
 * </p>
 */
public interface MediaSessionPort {

    /**
     * 建立音视频会话。
     *
     * @param participant 参与方账户标识
     * @param driverId    关联驾驶员标识
     * @param sessionType 会话类型
     * @return 会话句柄
     * @throws MediaSessionException 会话建立异常
     */
    SessionHandle establishSession(AccountId participant, DriverId driverId,
                                   SessionType sessionType) throws MediaSessionException;

    /**
     * 终止音视频会话。
     *
     * @param handle 会话句柄
     * @throws MediaSessionException 会话终止异常
     */
    void terminateSession(SessionHandle handle) throws MediaSessionException;

    /**
     * 会话类型枚举。
     */
    enum SessionType {
        AUDIO,  // 仅音频对讲
        VIDEO   // 音频 + 低码率视频
    }

    /**
     * 会话句柄。
     * <p>
     * 领域层不关心其内部实现（如 RTC 房间 ID、Token），
     * 仅作为 establishSession 的返回值供后续 terminateSession 引用。
     * </p>
     *
     * @param sessionId 会话标识
     */
    record SessionHandle(String sessionId) { }

    /**
     * 音视频会话异常。
     */
    sealed class MediaSessionException extends Exception permits
            MediaSessionException.SessionEstablishFailedException,
            MediaSessionException.SessionNotFoundException {

        public MediaSessionException(String message) {
            super(message);
        }

        /**
         * 信令或媒体通道建立失败。
         */
        public static final class SessionEstablishFailedException extends MediaSessionException {
            public SessionEstablishFailedException(String message) {
                super(message);
            }
        }

        /**
         * 终止时未找到对应会话。
         */
        public static final class SessionNotFoundException extends MediaSessionException {
            public SessionNotFoundException(String message) {
                super(message);
            }
        }
    }
}
