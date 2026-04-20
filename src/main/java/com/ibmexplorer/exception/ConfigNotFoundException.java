package com.ibmexplorer.exception;

public class ConfigNotFoundException extends RuntimeException {
    public ConfigNotFoundException(Long id) {
        super("Configuration not found with id: " + id);
    }

    public ConfigNotFoundException(String name) {
        super("Configuration not found: " + name);
    }
}
