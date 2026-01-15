/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services.documents;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.openlogisticsfoundation.ecmr.domain.models.Document;
import org.openlogisticsfoundation.ecmr.domain.models.DocumentMimeType;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class FileToPdfConverter {

    public RandomAccessReadBuffer toPdf(Document document, InputStream input) {

        try {
            if (DocumentMimeType.PDF.getMimeTypes().contains(document.getMimeType())) {
                return new RandomAccessReadBuffer(input);
            }

            if (DocumentMimeType.IMAGE.getMimeTypes().contains(document.getMimeType())) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                imageToPdf(input, output);
                return new RandomAccessReadBuffer(output.toByteArray());
            }

            throw new IllegalArgumentException("Unsupported document type");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to convert document", e);
        }
    }

    private void imageToPdf(InputStream imageStream, OutputStream out)
            throws IOException {

        try (PDDocument doc = new PDDocument()) {
            byte[] imageBytes = imageStream.readAllBytes();

            if (imageBytes.length > 20 * 1024 * 1024) {
                throw new IllegalStateException("Image too large");
            }

            PDImageXObject image =
                    PDImageXObject.createFromByteArray(doc, imageBytes, null);

            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);

            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                cs.drawImage(
                        image,
                        0, 0,
                        PDRectangle.A4.getWidth(),
                        PDRectangle.A4.getHeight()
                );
            }

            doc.save(out);
        }
    }
}
