package com.dedicatedcode.reitti.dto;

public record ReittiRemoteInfo(RemoteUserInfo userInfo, RemoteServerInfo serverInfo) {

    public record RemoteUserInfo(Long id, String username, String displayName, Long version) {}
    public record RemoteServerInfo(String name, String version, java.time.LocalDateTime systemTime) {}
}
