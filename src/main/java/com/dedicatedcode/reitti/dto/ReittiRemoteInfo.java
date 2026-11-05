package com.dedicatedcode.reitti.dto;

import java.util.List;

public record ReittiRemoteInfo(RemoteUserInfo userInfo, RemoteServerInfo serverInfo) {

    public record RemoteUserInfo(Long id, String username, String displayName, Long version) {}

    public record RemoteServerInfo(String name, String version, java.time.LocalDateTime systemTime, List<Integer> apiVersions) {
        public boolean supportsApiVersion(int version) {
            return apiVersions != null && apiVersions.contains(version);
        }
    }
}
