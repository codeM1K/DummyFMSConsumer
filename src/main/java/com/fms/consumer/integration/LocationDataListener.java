package com.fms.consumer.integration;

import com.fms.consumer.model.LocationData;

/**
 * Listener interface for receiving location data updates.
 * Implementations are notified when new location data is parsed
 * from WebSocket messages.
 */
public interface LocationDataListener {

    /**
     * Called when new location data is received and successfully parsed.
     *
     * @param data the parsed location data
     */
    void onLocationDataReceived(LocationData data);
}
