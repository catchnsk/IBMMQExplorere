package com.ibmexplorer.exception;

public class MqQueueNotFoundException extends RuntimeException {
    public MqQueueNotFoundException(String queueName) {
        super("Queue not found: " + queueName);
    }
}
