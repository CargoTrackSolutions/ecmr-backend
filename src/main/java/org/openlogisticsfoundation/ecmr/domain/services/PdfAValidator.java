/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.services;

import java.io.IOException;
import java.io.InputStream;

import org.openlogisticsfoundation.ecmr.domain.exceptions.PdfaValidationException;
import org.verapdf.core.EncryptedPdfException;
import org.verapdf.core.ModelParsingException;
import org.verapdf.core.ValidationException;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.VeraPDFFoundry;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.ValidationResult;

import lombok.extern.log4j.Log4j2;

@Log4j2
public class PdfAValidator {

    static {
        VeraGreenfieldFoundryProvider.initialise();
    }

    public static ValidationResult validatePdfA(InputStream pdfStream) throws PdfaValidationException {
        StringBuilder builder = new StringBuilder();
        PDFAFlavour flavour = PDFAFlavour.PDFA_1_B;

        try (VeraPDFFoundry foundry = Foundries.defaultInstance();
                PDFAValidator validator = foundry.createValidator(flavour, false);
                PDFAParser parser = foundry.createParser(pdfStream, flavour)) {

            ValidationResult result = validator.validate(parser);

            if (!result.isCompliant()) {
                builder.append("PDF/A Validation - Valid=").append(result.isCompliant()).append("\n");

                result.getTestAssertions().stream()
                        .filter(a -> a.getStatus() == TestAssertion.Status.FAILED)
                        .forEach(a -> builder.append("FAIL: ").append(a.getRuleId()).append(" - ").append(a.getMessage()).append("\n"));

                log.warn(builder.toString());
            }

            return result;
        } catch (ModelParsingException | EncryptedPdfException | ValidationException | IOException e) {
            throw new PdfaValidationException("PDF/A validation failed | " + e.getMessage());
        }
    }
}
