/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.models;

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
}
