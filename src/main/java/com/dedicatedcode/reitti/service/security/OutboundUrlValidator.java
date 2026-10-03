package com.dedicatedcode.reitti.service.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Guards outgoing requests to URLs and hosts supplied by users (integrations, map styles, geocoders, federation
 * callbacks, OIDC avatars, ...).
 * <p>
 * Loopback, link-local (incl. cloud metadata), unspecified and multicast addresses as well as the backing services of
 * this instance (database, redis, tile cache) are always rejected. Private networks (RFC 1918, CGNAT, IPv6 ULA) are
 * only rejected when {@code reitti.security.outbound.allow-private-networks} is {@code false}, because many
 * self-hosted setups legitimately point integrations at LAN services.
 * <p>
 * Endpoints configured by the administrator (tile cache, Panoramax, ...) are not passed through this validator.
 */
@Component
public class OutboundUrlValidator {
    private static final Logger log = LoggerFactory.getLogger(OutboundUrlValidator.class);

    private static final Set<String> ALLOWED_SCHEMES = Set.of("http", "https");
    private static final String NOT_ALLOWED = "The URL points to an address that is not allowed";

    private final boolean allowPrivateNetworks;
    private final List<InternalEndpoint> internalEndpoints;

    /**
     * A backing service of this instance. {@code port == -1} matches every port.
     */
    private record InternalEndpoint(String host, int port) {
    }

    private record HostAndPort(String host, int port) {
    }

    public OutboundUrlValidator(@Value("${reitti.security.outbound.allow-private-networks:true}") boolean allowPrivateNetworks,
                                @Value("${spring.datasource.url:}") String datasourceUrl,
                                @Value("${spring.data.redis.host:}") String redisHost,
                                @Value("${spring.data.redis.port:6379}") int redisPort,
                                @Value("${reitti.ui.tiles.cache.url:}") String tileCacheUrl) {
        this.allowPrivateNetworks = allowPrivateNetworks;
        List<InternalEndpoint> endpoints = new ArrayList<>(datasourceEndpoints(datasourceUrl));
        if (StringUtils.hasText(redisHost)) {
            endpoints.add(new InternalEndpoint(normalizeHost(redisHost), redisPort));
        }
        if (StringUtils.hasText(tileCacheUrl)) {
            try {
                URI uri = new URI(tileCacheUrl.trim());
                HostAndPort hostAndPort = hostAndPort(uri);
                endpoints.add(new InternalEndpoint(normalizeHost(hostAndPort.host()), hostAndPort.port()));
            } catch (URISyntaxException | UnsafeUrlException e) {
                log.warn("Unable to parse tile cache url [{}] for outbound request validation", tileCacheUrl);
            }
        }
        this.internalEndpoints = List.copyOf(endpoints);
    }

    /**
     * Validates a user supplied http(s) URL.
     *
     * @return the parsed URI
     * @throws UnsafeUrlException if the URL must not be requested
     */
    public URI validate(String url) {
        if (url == null || url.isBlank()) {
            throw new UnsafeUrlException("The URL must not be empty");
        }
        URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            // RestTemplate encodes String URLs before sending them, so parse them the same way
            try {
                uri = UriComponentsBuilder.fromUriString(url.trim()).encode().build().toUri();
            } catch (IllegalArgumentException | IllegalStateException ex) {
                throw new UnsafeUrlException("The URL is malformed");
            }
        }
        return validate(uri);
    }

    /**
     * Validates a user supplied http(s) URI.
     *
     * @return the given URI
     * @throws UnsafeUrlException if the URI must not be requested
     */
    public URI validate(URI uri) {
        String scheme = uri.getScheme();
        if (scheme == null || !ALLOWED_SCHEMES.contains(scheme.toLowerCase(Locale.ROOT))) {
            throw new UnsafeUrlException("Only http and https URLs are allowed");
        }
        HostAndPort hostAndPort = hostAndPort(uri);
        validateHost(hostAndPort.host(), hostAndPort.port());
        return uri;
    }

    /**
     * Validates a URL template with placeholders, e.g. {@code https://tiles.example.com/{z}/{x}/{y}.png}.
     */
    public URI validateTemplate(String template) {
        if (template == null || template.isBlank()) {
            throw new UnsafeUrlException("The URL must not be empty");
        }
        return validate(template.replace("{s}", "a").replaceAll("\\{[^}]*}", "0"));
    }

    public boolean isAllowed(String url) {
        try {
            validate(url);
            return true;
        } catch (UnsafeUrlException e) {
            return false;
        }
    }

    /**
     * Validates a user supplied host for non-http protocols (e.g. MQTT).
     *
     * @throws UnsafeUrlException if the host must not be contacted
     */
    public void validateHost(String host, int port) {
        String normalized = normalizeHost(host);
        if (normalized.isEmpty()) {
            throw new UnsafeUrlException("The host must not be empty");
        }
        if (normalized.equals("localhost") || normalized.endsWith(".localhost")) {
            throw new UnsafeUrlException(NOT_ALLOWED);
        }
        InetAddress[] addresses;
        try {
            addresses = InetAddress.getAllByName(normalized);
        } catch (UnknownHostException | SecurityException e) {
            throw new UnsafeUrlException("The host could not be resolved");
        }
        for (InetAddress address : addresses) {
            checkAddress(address);
        }
        checkInternalEndpoints(normalized, port, addresses);
    }

    private void checkAddress(InetAddress address) {
        if (address instanceof Inet6Address inet6) {
            InetAddress embedded = embeddedIpv4(inet6);
            if (embedded != null) {
                checkAddress(embedded);
            }
        }
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isMulticastAddress()
                || isReservedIpv4(address)
                || isCloudMetadata(address)) {
            throw new UnsafeUrlException(NOT_ALLOWED);
        }
        if (!allowPrivateNetworks && isPrivate(address)) {
            throw new UnsafeUrlException("The URL points to a private network address, which is disabled on this instance");
        }
    }

    private void checkInternalEndpoints(String host, int port, InetAddress[] addresses) {
        for (InternalEndpoint endpoint : internalEndpoints) {
            if (endpoint.port() != -1 && endpoint.port() != port) {
                continue;
            }
            if (endpoint.host().equals(host)) {
                throw new UnsafeUrlException(NOT_ALLOWED);
            }
            for (InetAddress endpointAddress : resolveQuietly(endpoint.host())) {
                for (InetAddress address : addresses) {
                    if (endpointAddress.equals(address)) {
                        throw new UnsafeUrlException(NOT_ALLOWED);
                    }
                }
            }
        }
    }

    private static InetAddress[] resolveQuietly(String host) {
        try {
            return InetAddress.getAllByName(host);
        } catch (UnknownHostException | SecurityException e) {
            return new InetAddress[0];
        }
    }

    private static boolean isReservedIpv4(InetAddress address) {
        if (!(address instanceof Inet4Address)) {
            return false;
        }
        byte[] b = address.getAddress();
        // 0.0.0.0/8 ("this network") and the limited broadcast address
        return (b[0] & 0xFF) == 0 || ((b[0] & 0xFF) == 255 && (b[1] & 0xFF) == 255 && (b[2] & 0xFF) == 255 && (b[3] & 0xFF) == 255);
    }

    private static boolean isCloudMetadata(InetAddress address) {
        byte[] b = address.getAddress();
        if (address instanceof Inet4Address) {
            // Alibaba Cloud metadata service (inside the CGNAT range, so not covered by the link-local check)
            return (b[0] & 0xFF) == 100 && (b[1] & 0xFF) == 100 && (b[2] & 0xFF) == 100 && (b[3] & 0xFF) == 200;
        }
        // AWS IPv6 metadata service fd00:ec2::254
        return (b[0] & 0xFF) == 0xFD && b[1] == 0 && (b[2] & 0xFF) == 0x0E && (b[3] & 0xFF) == 0xC2
                && isZero(b, 4, 14) && b[14] == 0x02 && (b[15] & 0xFF) == 0x54;
    }

    private static boolean isPrivate(InetAddress address) {
        byte[] b = address.getAddress();
        if (address.isSiteLocalAddress()) {
            return true;
        }
        if (address instanceof Inet4Address) {
            // 100.64.0.0/10 carrier grade NAT (also used by Tailscale)
            return (b[0] & 0xFF) == 100 && (b[1] & 0xC0) == 64;
        }
        // fc00::/7 unique local addresses
        return (b[0] & 0xFE) == 0xFC;
    }

    /**
     * IPv4 addresses embedded in IPv6 addresses that end up being routed to the IPv4 address
     * (IPv4-compatible ::a.b.c.d, NAT64 64:ff9b::a.b.c.d and 6to4 2002:aabb:ccdd::). IPv4-mapped addresses
     * are already returned as {@link Inet4Address} by the JDK.
     */
    private static InetAddress embeddedIpv4(Inet6Address address) {
        byte[] b = address.getAddress();
        try {
            if (isZero(b, 0, 12)) {
                return InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]});
            }
            if (b[0] == 0x00 && b[1] == 0x64 && (b[2] & 0xFF) == 0xFF && (b[3] & 0xFF) == 0x9B && isZero(b, 4, 12)) {
                return InetAddress.getByAddress(new byte[]{b[12], b[13], b[14], b[15]});
            }
            if (b[0] == 0x20 && b[1] == 0x02) {
                return InetAddress.getByAddress(new byte[]{b[2], b[3], b[4], b[5]});
            }
        } catch (UnknownHostException e) {
            return null;
        }
        return null;
    }

    private static boolean isZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static HostAndPort hostAndPort(URI uri) {
        String host = uri.getHost();
        int port = uri.getPort();
        if (host == null) {
            // java.net.URI does not accept e.g. underscores in host names (docker container names), while the
            // HTTP clients do. Parse the authority the same lenient way.
            String authority = uri.getRawAuthority();
            if (authority == null || authority.isBlank()) {
                throw new UnsafeUrlException("The URL has no host");
            }
            String hostPort = authority.substring(authority.lastIndexOf('@') + 1);
            String portPart = null;
            if (hostPort.startsWith("[")) {
                int end = hostPort.indexOf(']');
                if (end < 0) {
                    throw new UnsafeUrlException("The URL is malformed");
                }
                host = hostPort.substring(1, end);
                String rest = hostPort.substring(end + 1);
                if (rest.startsWith(":")) {
                    portPart = rest.substring(1);
                }
            } else {
                int colon = hostPort.lastIndexOf(':');
                host = colon >= 0 ? hostPort.substring(0, colon) : hostPort;
                portPart = colon >= 0 ? hostPort.substring(colon + 1) : null;
            }
            if (StringUtils.hasText(portPart)) {
                try {
                    port = Integer.parseInt(portPart);
                } catch (NumberFormatException e) {
                    throw new UnsafeUrlException("The URL is malformed");
                }
            }
        }
        if (port == -1) {
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            port = scheme.equals("https") ? 443 : 80;
        }
        return new HostAndPort(host, port);
    }

    private static String normalizeHost(String host) {
        if (host == null) {
            return "";
        }
        String normalized = host.trim().toLowerCase(Locale.ROOT);
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }
        while (normalized.endsWith(".")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }

    /**
     * Extracts host and port pairs from a JDBC URL like {@code jdbc:postgresql://host1:5432,host2/db}.
     */
    private static List<InternalEndpoint> datasourceEndpoints(String datasourceUrl) {
        List<InternalEndpoint> result = new ArrayList<>();
        if (!StringUtils.hasText(datasourceUrl)) {
            return result;
        }
        int start = datasourceUrl.indexOf("//");
        if (start < 0) {
            return result;
        }
        String rest = datasourceUrl.substring(start + 2);
        int end = rest.length();
        for (char terminator : new char[]{'/', '?'}) {
            int index = rest.indexOf(terminator);
            if (index >= 0 && index < end) {
                end = index;
            }
        }
        for (String hostPort : rest.substring(0, end).split(",")) {
            if (hostPort.isBlank()) {
                continue;
            }
            String host = hostPort;
            int port = 5432;
            if (hostPort.startsWith("[")) {
                int close = hostPort.indexOf(']');
                host = close > 0 ? hostPort.substring(1, close) : hostPort;
                if (close > 0 && hostPort.length() > close + 2 && hostPort.charAt(close + 1) == ':') {
                    port = parsePort(hostPort.substring(close + 2), port);
                }
            } else if (hostPort.contains(":")) {
                host = hostPort.substring(0, hostPort.lastIndexOf(':'));
                port = parsePort(hostPort.substring(hostPort.lastIndexOf(':') + 1), port);
            }
            result.add(new InternalEndpoint(normalizeHost(host), port));
        }
        return result;
    }

    private static int parsePort(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
