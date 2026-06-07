package com.dedicatedcode.reitti.dto.timeline;

import java.util.List;

public record TimelineData(
        List<UserTimelineData> users
) {
}
