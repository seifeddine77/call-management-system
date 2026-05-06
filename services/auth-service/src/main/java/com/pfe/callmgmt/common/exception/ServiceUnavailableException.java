package com.pfe.callmgmt.common.exception;

/**
 * Exception thrown when a downstream microservice is unavailable.
 * Replaces generic RuntimeException to satisfy SonarCloud rule java:S112.
 */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
