/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.openlogisticsfoundation.ecmr.domain.models.PdfFile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.lang.NonNull;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;

@Service
public class MailService {

    private final JavaMailSender mailSender;

    @Value("${mail.from}")
    String from;

    MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendMail(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        message.setFrom(from);
        mailSender.send(message);
    }

    public void sendMailWithPdfAttachment(String to, String subject, String text, @NonNull PdfFile pdfFile) throws MessagingException, MailException {
        MimeMessage mimeMessage = mailSender.createMimeMessage();
        MimeMessageHelper mimeMessageHelper = new MimeMessageHelper(mimeMessage, true);
        mimeMessageHelper.setTo(to);
        mimeMessageHelper.setSubject(subject);
        mimeMessageHelper.setText(text);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        pdfFile.writeTo(byteArrayOutputStream);
        ByteArrayResource byteArrayResource = new ByteArrayResource(byteArrayOutputStream.toByteArray());

        mimeMessageHelper.addAttachment(pdfFile.getFilename(), byteArrayResource, "application/pdf");
        mimeMessageHelper.setFrom(from);
        mailSender.send(mimeMessage);
    }
}
