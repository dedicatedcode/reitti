package com.dedicatedcode.reitti.dto;

public class PhotoResponse {
    
    private String id;
    private String fileName;
    private String thumbnailUrl;
    private String fullImageUrl;
    private Double latitude;
    private Double longitude;
    private String dateTime;

    public PhotoResponse() {}

    public PhotoResponse(String id, String fileName, String thumbnailUrl, String fullImageUrl, 
                        Double latitude, Double longitude, String dateTime) {
        this.id = id;
        this.fileName = fileName;
        this.thumbnailUrl = thumbnailUrl;
        this.fullImageUrl = fullImageUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.dateTime = dateTime;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }

    public String getFullImageUrl() {
        return fullImageUrl;
    }

    public void setFullImageUrl(String fullImageUrl) {
        this.fullImageUrl = fullImageUrl;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public String getDateTime() {
        return dateTime;
    }

    public void setDateTime(String dateTime) {
        this.dateTime = dateTime;
    }
}
