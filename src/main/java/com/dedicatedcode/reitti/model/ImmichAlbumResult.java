package com.dedicatedcode.reitti.model;

import com.dedicatedcode.reitti.dto.ImmichAlbum;

import java.util.List;

public record ImmichAlbumResult(Status status, List<ImmichAlbum> albums, String message) {

    public enum Status {
        OK, PERMISSION_DENIED, AUTH_FAILED, FAILED
    }

    public static ImmichAlbumResult ok(List<ImmichAlbum> albums) {
        return new ImmichAlbumResult(Status.OK, albums, null);
    }

    public static ImmichAlbumResult permissionDenied() {
        return new ImmichAlbumResult(Status.PERMISSION_DENIED, null, null);
    }

    public static ImmichAlbumResult authFailed() {
        return new ImmichAlbumResult(Status.AUTH_FAILED, null, null);
    }

    public static ImmichAlbumResult failed(String message) {
        return new ImmichAlbumResult(Status.FAILED, null, message);
    }
}
