package com.fms.consumer.service;

import com.fms.consumer.model.Realm;
import com.fms.consumer.model.Vehicle;

import java.util.List;

/**
 * Listener interface for receiving discovery update notifications.
 * Components that need to be notified when realms or vehicles are
 * discovered or refreshed should implement this interface and register
 * with {@link DiscoveryService}.
 */
public interface DiscoveryListener {

    /**
     * Called when the list of available realms has been updated.
     *
     * @param realms the updated list of realms
     */
    default void onRealmsUpdated(List<Realm> realms) {
    }

    /**
     * Called when the list of vehicles for a specific realm has been updated.
     *
     * @param realmId  the ID of the realm whose vehicles were updated
     * @param vehicles the updated list of vehicles for the realm
     */
    default void onVehiclesUpdated(String realmId, List<Vehicle> vehicles) {
    }

    /**
     * Called when a discovery operation fails.
     *
     * @param error the exception describing the failure
     */
    default void onDiscoveryError(Throwable error) {
    }
}
