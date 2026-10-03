package com.dedicatedcode.reitti.service.integration;

import com.dedicatedcode.reitti.dto.SubscriptionResponse;
import com.dedicatedcode.reitti.model.Role;
import com.dedicatedcode.reitti.model.UserType;
import com.dedicatedcode.reitti.model.security.User;
import com.dedicatedcode.reitti.service.security.OutboundUrlValidator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ReittiSubscriptionServiceTest {

    private final ReittiSubscriptionService service = new ReittiSubscriptionService(new OutboundUrlValidator(true, "", "", 6379, ""));
    private final User user = new User(1L, "token-holder", null, "Token Holder", null, null, Role.USER, UserType.NORMAL, 1L);
    private final User otherUser = new User(2L, "other", null, "Other", null, null, Role.USER, UserType.NORMAL, 1L);

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1:8080",
            "http://localhost:8080/",
            "http://169.254.169.254/latest/meta-data",
            "http://[::1]/",
            "file:///etc/passwd",
            "not a url",
    })
    void rejectsCallbackUrlsPointingToForbiddenTargets(String callbackUrl) {
        assertThatThrownBy(() -> service.createSubscription(user, callbackUrl))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void acceptsRegularCallbackUrls() {
        SubscriptionResponse response = service.createSubscription(user, "http://192.168.1.30:8080");

        assertThat(service.getSubscription(response.getSubscriptionId())).isNotNull();
        assertThat(service.getSubscription(response.getSubscriptionId()).getCallbackUrl()).isEqualTo("http://192.168.1.30:8080");
    }

    @Test
    void limitsTheNumberOfSubscriptionsPerUser() throws InterruptedException {
        SubscriptionResponse otherUsersSubscription = service.createSubscription(otherUser, "http://192.168.1.31:8080");
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < ReittiSubscriptionService.MAX_SUBSCRIPTIONS_PER_USER + 5; i++) {
            ids.add(service.createSubscription(user, "http://192.168.1.30:8080").getSubscriptionId());
            // distinct creation timestamps so the oldest subscriptions are evicted deterministically
            Thread.sleep(2);
        }

        long remaining = ids.stream().filter(id -> service.getSubscription(id) != null).count();
        assertThat(remaining).isEqualTo(ReittiSubscriptionService.MAX_SUBSCRIPTIONS_PER_USER);
        assertThat(service.getSubscription(ids.getFirst())).isNull();
        assertThat(service.getSubscription(ids.getLast())).isNotNull();
        assertThat(service.getSubscription(otherUsersSubscription.getSubscriptionId())).isNotNull();
    }
}
