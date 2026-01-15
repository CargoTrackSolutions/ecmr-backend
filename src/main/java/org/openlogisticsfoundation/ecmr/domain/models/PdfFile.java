/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.models;

import java.io.OutputStream;
import java.util.function.Consumer;

import lombok.AllArgsConstructor;
import lombok.Getter;

@AllArgsConstructor
@Getter
public class PdfFile {
    private String filename;
    private final Consumer<OutputStream> writer;

    public void writeTo(OutputStream out) {
        writer.accept(out);
    }
}
