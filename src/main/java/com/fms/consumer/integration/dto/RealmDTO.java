package com.fms.consumer.integration.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * DTO representing a realm retrieved from the Open Remote API.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class RealmDTO {

    private String id;
    private String name;

    public RealmDTO() {
    }

    public RealmDTO(String id, String name) {
        this.id = id;
        this.name = name;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return "RealmDTO{" +
                "id='" + id + '\'' +
                ", name='" + name + '\'' +
                '}';
    }
}
