package com.cloudelements.nexus.model;

import lombok.Data;
import org.springframework.http.HttpStatus;

import java.util.Map;

/**
 * @author venkat
 */
@Data
public class DispatcherResponse {
    private HttpStatus status;
    private Map<String, String> info;
}
