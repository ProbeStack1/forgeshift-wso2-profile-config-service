package com.forgeshift.profile.config.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

/**
 * Parses GCP service-account JSON bytes into {@link ServiceAccountInfo}.
 * The JSON shape is documented at
 * https://cloud.google.com/iam/docs/service-account-creds#key-types
 *
 * Required keys: type, project_id, private_key_id, private_key, client_email,
 *                client_id.
 */
@Slf4j
@Component
public class ServiceAccountFileProcessor {

    private final ObjectMapper objectMapper = new ObjectMapper();

    public ServiceAccountInfo parse(byte[] jsonBytes) {
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> m = objectMapper.readValue(jsonBytes, Map.class);
            String type = str(m.get("type"));
            if (!"service_account".equals(type)) {
                throw new IllegalArgumentException(
                        "Not a service-account JSON. Expected type=service_account, got: " + type);
            }
            return ServiceAccountInfo.builder()
                    .projectId(str(m.get("project_id")))
                    .clientEmail(str(m.get("client_email")))
                    .privateKeyId(str(m.get("private_key_id")))
                    .clientId(str(m.get("client_id")))
                    .build();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException(
                    "Failed to parse service-account JSON: " + e.getMessage(), e);
        }
    }

    public ServiceAccountInfo parseBase64(String base64) {
        return parse(Base64.getDecoder().decode(base64));
    }

    public String toBase64(byte[] jsonBytes) {
        return Base64.getEncoder().encodeToString(jsonBytes);
    }

    public byte[] fromBase64(String base64) {
        return Base64.getDecoder().decode(base64);
    }

    public String utf8(byte[] bytes) {
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static String str(Object o) { return o == null ? null : o.toString(); }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServiceAccountInfo {
        private String projectId;
        private String clientEmail;
        private String privateKeyId;
        private String clientId;
    }
}
