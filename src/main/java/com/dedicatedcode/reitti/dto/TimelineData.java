package com.dedicatedcode.reitti.dto;

import java.util.List;

public record TimelineData(
        List<UserTimelineData> users
) {
}
