package org.openlogisticsfoundation.ecmr.domain.services.documents;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.openlogisticsfoundation.ecmr.domain.models.DocumentMimeType;
import org.openlogisticsfoundation.ecmr.persistence.entities.DocumentEntity;
import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;

@Service
@Log4j2
public class FileToPdfConverter {

    public void toPdf(DocumentEntity document, byte[] data, OutputStream output) {

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
            if (document.getMimeType() == DocumentMimeType.PDF) {
                inputStream.transferTo(output);
            }

            if (document.getMimeType() == DocumentMimeType.IMAGE) {
                imageToPdf(inputStream, output);
            }

            throw new IllegalArgumentException("Unsupported document type");
        } catch (IOException e) {
            throw new IllegalStateException("Failed to convert document", e);
        }
    }

    public void imageToPdf(InputStream imageStream, OutputStream out)
            throws IOException {
        try (PDDocument doc = new PDDocument()) {

            PDImageXObject image = PDImageXObject.createFromByteArray(doc, imageStream.readAllBytes(), null);

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
