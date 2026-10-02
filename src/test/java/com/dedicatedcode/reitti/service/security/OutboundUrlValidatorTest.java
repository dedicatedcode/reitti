package com.dedicatedcode.reitti.service.security;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OutboundUrlValidatorTest {

    private final OutboundUrlValidator lanAllowed = new OutboundUrlValidator(true,
            "jdbc:postgresql://10.1.2.3:5432/reittidb", "10.1.2.4", 6379, "http://10.1.2.5");
    private final OutboundUrlValidator lanBlocked = new OutboundUrlValidator(false,
            "jdbc:postgresql://10.1.2.3:5432/reittidb", "10.1.2.4", 6379, "http://10.1.2.5");

    @ParameterizedTest
    @ValueSource(strings = {
            "http://127.0.0.1/",
            "http://127.1.2.3:8080/api",
            "https://localhost/",
            "http://LOCALHOST:2283",
            "http://localhost./",
            "http://immich.localhost/",
            "http://a_b.localhost:2283/",
            "http://[::1]/",
            "http://0.0.0.0/",
            "http://0.1.2.3/",
            "http://[::]/",
            "http://169.254.169.254/latest/meta-data/",
            "http://user:pw@169.254.169.254/",
            "http://[fe80::1]/",
            "http://224.0.0.1/",
            "http://255.255.255.255/",
            "http://[::ffff:127.0.0.1]/",
            "http://[::127.0.0.1]/",
            "http://[64:ff9b::7f00:1]/",
            "http://[2002:7f00:1::]/",
            "http://100.100.100.200/latest/meta-data/",
            "http://[fd00:ec2::254]/",
            "http://10.1.2.3:5432/",
            "http://10.1.2.4:6379/",
            "http://10.1.2.5/custom/",
    })
    void alwaysBlocksLoopbackLinkLocalMetadataAndInternalServices(String url) {
        assertThatThrownBy(() -> lanAllowed.validate(url)).isInstanceOf(UnsafeUrlException.class);
        assertThatThrownBy(() -> lanBlocked.validate(url)).isInstanceOf(UnsafeUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "file:///etc/passwd",
            "ftp://8.8.8.8/",
            "gopher://8.8.8.8:6379/_INFO",
            "jar:http://8.8.8.8/a.jar!/",
            "javascript:alert(1)",
            "//8.8.8.8/",
            "8.8.8.8",
            "",
            "   ",
            "http://",
            "http://exa mple.com/",
    })
    void rejectsNonHttpOrMalformedUrls(String url) {
        assertThatThrownBy(() -> lanAllowed.validate(url)).isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void rejectsNull() {
        assertThatThrownBy(() -> lanAllowed.validate((String) null)).isInstanceOf(UnsafeUrlException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://8.8.8.8/",
            "https://1.1.1.1:8443/path?q=1",
            "HTTPS://[2606:4700:4700::1111]/",
            "http://203.0.113.10/",
    })
    void allowsPublicAddresses(String url) {
        assertThatCode(() -> lanAllowed.validate(url)).doesNotThrowAnyException();
        assertThatCode(() -> lanBlocked.validate(url)).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "http://192.168.1.10:2283",
            "http://10.0.0.5/",
            "http://172.16.0.1/",
            "http://172.31.255.254/",
            "http://100.64.0.1/",
            "http://100.127.255.254/",
            "http://[fd12:3456::1]:2283/",
            "http://10.1.2.3:2283/",
            "http://10.1.2.4:2283/",
    })
    void privateNetworksDependOnConfiguration(String url) {
        assertThatCode(() -> lanAllowed.validate(url)).doesNotThrowAnyException();
        assertThatThrownBy(() -> lanBlocked.validate(url)).isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void validatesTemplatesWithPlaceholders() {
        assertThatCode(() -> lanAllowed.validateTemplate("http://203.0.113.10/{z}/{x}/{y}.png?key={key}")).doesNotThrowAnyException();
        assertThatThrownBy(() -> lanAllowed.validateTemplate("http://127.0.0.1/{z}/{x}/{y}.png")).isInstanceOf(UnsafeUrlException.class);
        assertThatThrownBy(() -> lanAllowed.validateTemplate("file:///{z}/{x}/{y}.png")).isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void validatesPlainHosts() {
        assertThatCode(() -> lanAllowed.validateHost("192.168.1.20", 1883)).doesNotThrowAnyException();
        assertThatThrownBy(() -> lanBlocked.validateHost("192.168.1.20", 1883)).isInstanceOf(UnsafeUrlException.class);
        assertThatThrownBy(() -> lanAllowed.validateHost("localhost", 1883)).isInstanceOf(UnsafeUrlException.class);
        assertThatThrownBy(() -> lanAllowed.validateHost("127.0.0.1", 1883)).isInstanceOf(UnsafeUrlException.class);
        assertThatThrownBy(() -> lanAllowed.validateHost("10.1.2.4", 6379)).isInstanceOf(UnsafeUrlException.class);
        assertThatThrownBy(() -> lanAllowed.validateHost("", 1883)).isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void blocksUnresolvableHosts() {
        assertThatThrownBy(() -> lanAllowed.validate("http://does-not-exist.invalid/")).isInstanceOf(UnsafeUrlException.class);
    }

    @Test
    void isAllowedReflectsValidation() {
        assertThat(lanAllowed.isAllowed("http://8.8.8.8/")).isTrue();
        assertThat(lanAllowed.isAllowed("http://127.0.0.1/")).isFalse();
    }
}
