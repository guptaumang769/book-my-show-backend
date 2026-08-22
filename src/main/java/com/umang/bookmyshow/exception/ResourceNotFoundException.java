package com.umang.bookmyshow.exception;

import org.springframework.http.HttpStatus;

public class ResourceNotFoundException extends BookingException {

    public ResourceNotFoundException(String resource, Object id) {
        super(resource + " not found: " + id, "RESOURCE_NOT_FOUND", HttpStatus.NOT_FOUND);
    }
}
