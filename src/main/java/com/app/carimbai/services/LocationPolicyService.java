package com.app.carimbai.services;

import com.app.carimbai.dtos.LocationPolicy;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

@Service
public class LocationPolicyService {

    private final ObjectMapper mapper;

    public LocationPolicyService(ObjectMapper mapper) {
        this.mapper = mapper;
    }

    public LocationPolicy fromFlags(String flagsJson) {
        try {
            JsonNode n = flagsJson == null || flagsJson.isBlank() ? mapper.createObjectNode() : mapper.readTree(flagsJson);
            boolean requirePin = n.has("requirePinOnRedeem") ? n.get("requirePinOnRedeem").asBoolean() : true;
            boolean scanA = n.has("enableScanA") ? n.get("enableScanA").asBoolean() : true;
            boolean scanB = n.has("enableScanB") ? n.get("enableScanB").asBoolean() : false;
            return new LocationPolicy(requirePin, scanA, scanB);
        } catch (Exception e) {
            return LocationPolicy.defaults(); // fallback seguro
        }
    }
}
