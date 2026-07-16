package com.fms.consumer.integration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fms.consumer.model.LocationData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Parses incoming location data messages and notifies registered listeners.
 * Uses Jackson ObjectMapper for JSON deserialization and implements a
 * thread-safe listener pattern using CopyOnWriteArrayList.
 */
@Component
public class LocationDataHandler {

    private static final Logger logger = LoggerFactory.getLogger(LocationDataHandler.class);

    private final ObjectMapper jsonMapper;
    private final List<LocationDataListener> listeners;

    public LocationDataHandler() {
        this.jsonMapper = new ObjectMapper();
        this.listeners = new CopyOnWriteArrayList<>();
    }

    public LocationDataHandler(ObjectMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
        this.listeners = new CopyOnWriteArrayList<>();
    }

    /**
     * Parses a JSON string into a LocationData object.
     * Handles null input, empty strings, malformed JSON, and missing required fields.
     * On error, logs at WARN level and returns null.
     *
     * @param json the JSON string to parse
     * @return parsed LocationData or null if parsing fails
     */
    public LocationData parse(String json) {
        if (json == null || json.isBlank()) {
            logger.warn("Received null or empty location data message");
            return null;
        }

        try {
            JsonNode root = jsonMapper.readTree(json);

            // Validate required fields
            if (!root.has("vehicleId") || root.get("vehicleId").isNull()) {
                logger.warn("Location data missing required field 'vehicleId': {}", json);
                return null;
            }
            if (!root.has("latitude") || !root.get("latitude").isNumber()) {
                logger.warn("Location data missing or invalid required field 'latitude': {}", json);
                return null;
            }
            if (!root.has("longitude") || !root.get("longitude").isNumber()) {
                logger.warn("Location data missing or invalid required field 'longitude': {}", json);
                return null;
            }

            String vehicleId = root.get("vehicleId").asText();
            double latitude = root.get("latitude").asDouble();
            double longitude = root.get("longitude").asDouble();

            // Validate coordinate ranges
            if (latitude < -90 || latitude > 90) {
                logger.warn("Location data has invalid latitude {}: {}", latitude, json);
                return null;
            }
            if (longitude < -180 || longitude > 180) {
                logger.warn("Location data has invalid longitude {}: {}", longitude, json);
                return null;
            }

            // Parse timestamp (optional, defaults to current time)
            Instant timestamp;
            if (root.has("timestamp") && root.get("timestamp").isNumber()) {
                timestamp = Instant.ofEpochMilli(root.get("timestamp").asLong());
            } else {
                timestamp = Instant.now();
            }

            // Parse metadata (optional)
            Map<String, Object> metadata = new HashMap<>();
            if (root.has("metadata") && root.get("metadata").isObject()) {
                JsonNode metadataNode = root.get("metadata");
                metadataNode.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    if (value.isNumber()) {
                        if (value.isIntegralNumber()) {
                            metadata.put(entry.getKey(), value.asLong());
                        } else {
                            metadata.put(entry.getKey(), value.asDouble());
                        }
                    } else if (value.isBoolean()) {
                        metadata.put(entry.getKey(), value.asBoolean());
                    } else if (value.isTextual()) {
                        metadata.put(entry.getKey(), value.asText());
                    } else {
                        metadata.put(entry.getKey(), value.toString());
                    }
                });
            }

            return new LocationData(vehicleId, latitude, longitude, timestamp, metadata);

        } catch (JsonProcessingException e) {
            logger.warn("Failed to parse location data JSON: {}", e.getMessage());
            return null;
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid location data values: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Notifies all registered listeners of new location data.
     * Catches and logs exceptions from individual listeners to prevent
     * one failing listener from affecting others.
     *
     * @param data the location data to broadcast
     */
    public void notifyListeners(LocationData data) {
        if (data == null) {
            return;
        }
        for (LocationDataListener listener : listeners) {
            try {
                listener.onLocationDataReceived(data);
            } catch (Exception e) {
                logger.warn("Listener threw exception while processing location data: {}", e.getMessage());
            }
        }
    }

    /**
     * Registers a listener to receive location data updates.
     *
     * @param listener the listener to add
     */
    public void addListener(LocationDataListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Removes a previously registered listener.
     *
     * @param listener the listener to remove
     */
    public void removeListener(LocationDataListener listener) {
        if (listener != null) {
            listeners.remove(listener);
        }
    }

    /**
     * Returns the number of registered listeners.
     *
     * @return listener count
     */
    public int getListenerCount() {
        return listeners.size();
    }
}
