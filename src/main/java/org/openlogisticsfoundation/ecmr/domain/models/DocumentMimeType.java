package org.openlogisticsfoundation.ecmr.domain.models;

import java.util.Arrays;
import java.util.Set;

import lombok.Getter;

@Getter
public enum DocumentMimeType {
    PDF("application/pdf"),
    IMAGE("image/png", "image/jpeg", "image/jpg");

    private final Set<String> mimeTypes;

    DocumentMimeType(String... mimeTypes) {
        this.mimeTypes = Set.of(mimeTypes);
    }

    public boolean matches(String contentType) {
        return contentType != null && mimeTypes.contains(contentType);
    }

    public static DocumentMimeType fromContentType(String contentType) {
        return Arrays.stream(values())
                .filter(type -> type.matches(contentType))
                .findFirst()
                .orElseThrow(() ->
                        new IllegalArgumentException("Unsupported content type: " + contentType));
    }
}
