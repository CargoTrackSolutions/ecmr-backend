/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

import org.springframework.stereotype.Service;

import lombok.extern.log4j.Log4j2;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.export.JRPdfExporter;
import net.sf.jasperreports.export.SimpleExporterInput;
import net.sf.jasperreports.export.SimpleOutputStreamExporterOutput;
import net.sf.jasperreports.export.SimplePdfExporterConfiguration;
import net.sf.jasperreports.export.type.PdfaConformanceEnum;

@Service
@Log4j2
public class PdfAService {

    public void exportJasperToPdfA(JasperPrint print, OutputStream output) throws JRException, IOException {
        JRPdfExporter exporter = new JRPdfExporter();
        exporter.setExporterInput(new SimpleExporterInput(print));
        exporter.setExporterOutput(new SimpleOutputStreamExporterOutput(output));
        SimplePdfExporterConfiguration config = new SimplePdfExporterConfiguration();
        config.setPdfaConformance(PdfaConformanceEnum.PDFA_1B);
        config.setIccProfilePath(getIccProfilePath());
        exporter.setConfiguration(config);
        exporter.exportReport();
    }

    private String getIccProfilePath() throws IOException {
        try (InputStream icc = getClass().getResourceAsStream("/color/sRGB.icc")) {
            if (icc == null) throw new IOException("sRGB ICC profile not found");
            File tempFile = File.createTempFile("sRGB", ".icc");
            tempFile.deleteOnExit();
            Files.copy(icc, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return tempFile.getAbsolutePath();
        }
    }
}
