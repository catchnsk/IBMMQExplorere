package com.ibmexplorer.exception;

import lombok.Getter;

@Getter
public class MqConnectionException extends RuntimeException {

    private final int mqReasonCode;

    public MqConnectionException(int mqReasonCode, String message) {
        super(message);
        this.mqReasonCode = mqReasonCode;
    }

    public MqConnectionException(int mqReasonCode, String message, Throwable cause) {
        super(message, cause);
        this.mqReasonCode = mqReasonCode;
    }
}
