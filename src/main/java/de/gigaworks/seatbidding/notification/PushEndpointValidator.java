package de.gigaworks.seatbidding.notification;

import de.gigaworks.seatbidding.exception.ApplicationProblem;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;
import java.time.Instant;
import java.util.Base64;

@ApplicationScoped
public class PushEndpointValidator {

    static final int MAX_ENDPOINT_LENGTH = 4096;
    static final int MAX_KEY_LENGTH = 512;
    static final int MAX_DEVICE_LABEL_CODE_POINTS = 120;

    @Inject
    EndpointAddressResolver addressResolver;

    public ValidatedSubscription validate(String endpoint, String p256dh, String auth, Instant expiresAt,
            String deviceLabel, Instant now) {
        URI uri = validateEndpoint(endpoint);
        validatePublicResolution(uri);
        byte[] p256dhBytes = decodeKey("keys.p256dh", p256dh, 65);
        if (p256dhBytes[0] != 4) {
            throw invalid("keys.p256dh", "must be an uncompressed P-256 public key");
        }
        decodeKey("keys.auth", auth, 16);
        if (expiresAt != null && !expiresAt.isAfter(now)) {
            throw invalid("expirationTime", "must be in the future when provided");
        }
        String label = sanitizeLabel(deviceLabel);
        return new ValidatedSubscription(uri.toASCIIString(), p256dh, auth, expiresAt, label);
    }

    public URI validateForDelivery(String endpoint) {
        URI uri = validateEndpoint(endpoint);
        validatePublicResolution(uri);
        return uri;
    }

    private static URI validateEndpoint(String endpoint) {
        if (endpoint == null || endpoint.isBlank() || endpoint.length() > MAX_ENDPOINT_LENGTH) {
            throw invalid("endpoint", "must be a bounded HTTPS URL");
        }
        try {
            URI uri = new URI(endpoint);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null || uri.getRawUserInfo() != null
                    || uri.getRawFragment() != null || uri.getPort() != -1) {
                throw invalid("endpoint", "must be an HTTPS push-service URL without credentials, fragment, or custom port");
            }
            return uri.normalize();
        }
        catch (URISyntaxException exception) {
            throw invalid("endpoint", "must be a valid HTTPS push-service URL");
        }
    }

    private void validatePublicResolution(URI endpoint) {
        try {
            var addresses = addressResolver.resolve(endpoint.getHost());
            if (addresses.isEmpty() || addresses.stream().anyMatch(PushEndpointValidator::isInternalAddress)) {
                throw invalid("endpoint", "must resolve only to public network addresses");
            }
        }
        catch (UnknownHostException exception) {
            throw invalid("endpoint", "must resolve to a public push service");
        }
    }

    static boolean isInternalAddress(InetAddress address) {
        if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet6Address) {
            return (bytes[0] & 0xfe) == 0xfc;
        }
        int first = Byte.toUnsignedInt(bytes[0]);
        int second = Byte.toUnsignedInt(bytes[1]);
        return first == 0 || first == 10 || first == 127 || first == 169 && second == 254
                || first == 172 && second >= 16 && second <= 31 || first == 192 && second == 168
                || first >= 224;
    }

    private static byte[] decodeKey(String field, String value, int expectedLength) {
        if (value == null || value.isBlank() || value.length() > MAX_KEY_LENGTH
                || !value.matches("[A-Za-z0-9_-]+")) {
            throw invalid(field, "must be unpadded base64url data");
        }
        try {
            byte[] decoded = Base64.getUrlDecoder().decode(value);
            if (decoded.length != expectedLength) {
                throw invalid(field, "has an invalid decoded length");
            }
            return decoded;
        }
        catch (IllegalArgumentException exception) {
            throw invalid(field, "must be valid base64url data");
        }
    }

    private static String sanitizeLabel(String value) {
        if (value == null) {
            throw invalid("deviceLabel", "is required");
        }
        String label = value.strip().replaceAll("[\\p{Cc}\\p{Cf}]", "");
        if (label.isBlank() || label.codePointCount(0, label.length()) > MAX_DEVICE_LABEL_CODE_POINTS) {
            throw invalid("deviceLabel", "must contain between 1 and 120 visible characters");
        }
        return label;
    }

    private static ApplicationProblem invalid(String field, String message) {
        return ApplicationProblem.badRequest("INVALID_PUSH_SUBSCRIPTION", "Invalid push subscription",
                field + " " + message + ".");
    }

    public record ValidatedSubscription(String endpoint, String p256dh, String auth, Instant expiresAt,
            String deviceLabel) {
    }

}