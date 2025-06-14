package com.dedicatedcode.reitti.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

public class ImmichAsset {
    
    @JsonProperty("id")
    private String id;
    
    @JsonProperty("originalFileName")
    private String originalFileName;
    
    @JsonProperty("localDateTime")
    private String localDateTime;
    
    @JsonProperty("exifInfo")
    private ExifInfo exifInfo;
    
    @JsonProperty("thumbhash")
    private String thumbhash;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOriginalFileName() {
        return originalFileName;
    }

    public void setOriginalFileName(String originalFileName) {
        this.originalFileName = originalFileName;
    }

    public String getLocalDateTime() {
        return localDateTime;
    }

    public void setLocalDateTime(String localDateTime) {
        this.localDateTime = localDateTime;
    }

    public ExifInfo getExifInfo() {
        return exifInfo;
    }

    public void setExifInfo(ExifInfo exifInfo) {
        this.exifInfo = exifInfo;
    }

    public String getThumbhash() {
        return thumbhash;
    }

    public void setThumbhash(String thumbhash) {
        this.thumbhash = thumbhash;
    }

    public static class ExifInfo {
        @JsonProperty("latitude")
        private Double latitude;
        
        @JsonProperty("longitude")
        private Double longitude;
        
        @JsonProperty("dateTimeOriginal")
        private String dateTimeOriginal;

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

        public String getDateTimeOriginal() {
            return dateTimeOriginal;
        }

        public void setDateTimeOriginal(String dateTimeOriginal) {
            this.dateTimeOriginal = dateTimeOriginal;
        }
    }
}
