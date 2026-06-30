package com.aiot.domain.port;

import com.aiot.domain.model.RescueReport;

/**
 * 救援报告投递端口。
 * <p>
 * 负责向 120 救援中心投递 SOS 救援报告。
 * 仅负责"投递"行为，不负责报告内容的组装。
 * </p>
 * <p>
 * 设计依据：docs/ood_domain.md 决策 19 端口 2
 * </p>
 */
public interface RescueReportPort {

    /**
     * 投递救援报告。
     *
     * @param report 救援报告
     * @throws RescueReportException 投递异常
     */
    void deliverRescueReport(RescueReport report) throws RescueReportException;

    /**
     * 救援报告投递异常。
     */
    sealed class RescueReportException extends Exception permits
            RescueReportException.DeliveryFailedException,
            RescueReportException.AckTimeoutException {

        public RescueReportException(String message) {
            super(message);
        }

        /**
         * 链路故障。
         */
        public static final class DeliveryFailedException extends RescueReportException {
            public DeliveryFailedException(String message) {
                super(message);
            }
        }

        /**
         * 救援中心未在约定时间内确认接收。
         */
        public static final class AckTimeoutException extends RescueReportException {
            public AckTimeoutException(String message) {
                super(message);
            }
        }
    }
}
