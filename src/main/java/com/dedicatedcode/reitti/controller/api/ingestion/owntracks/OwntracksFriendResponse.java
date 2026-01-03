package com.dedicatedcode.reitti.controller.api.ingestion.owntracks;


import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Base64;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class OwntracksFriendResponse {
    @JsonProperty("_type")
    private final String type;
    @JsonProperty("tid")
    private final String tid;
    @JsonProperty("name")
    private final String name;
    @JsonProperty("lat")
    private final Double lat;
    @JsonProperty("lon")
    private final Double lon;
    @JsonProperty("tst")
    private final Long tst;
    @JsonProperty("face")
    private final String face;

    // Card constructor with avatar
    public OwntracksFriendResponse(String tid, String name, byte[] avatarData, String mimeType) {
        this.type = "card";
        this.tid = tid;
        this.name = name;
        this.lat = null;
        this.lon = null;
        this.tst = null;
        this.face = avatarData != null ? createFaceData(avatarData, mimeType) : null;
    }

    // Location constructor
    public OwntracksFriendResponse(String tid, String name, double lat, double lon, long tst) {
        this.type = "location";
        this.tid = tid;
        this.name = name;
        this.lat = lat;
        this.lon = lon;
        this.tst = tst;
        this.face = null;
    }

    private String createFaceData(byte[] avatarData, String mimeType) {
        if (avatarData == null || mimeType == null) {
            return null;
        }
        return Base64.getEncoder().encodeToString(avatarData);
    }

    // Getters
    public String getType() { return type; }
    public String getTid() { return tid; }
    public String getName() { return name; }
    public Double getLat() { return lat; }
    public Double getLon() { return lon; }
    public Long getTst() { return tst; }
    public String getFace() { return face; }
}
