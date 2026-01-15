/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.pdfbox.io.RandomAccessReadBuffer;
import org.apache.pdfbox.multipdf.PDFMergerUtility;
import org.openlogisticsfoundation.ecmr.api.model.EcmrModel;
import org.openlogisticsfoundation.ecmr.api.model.SealMetadata;
import org.openlogisticsfoundation.ecmr.api.model.TransportRole;
import org.openlogisticsfoundation.ecmr.api.model.areas.ten.LogisticsShippingMarksCustomBarcode;
import org.openlogisticsfoundation.ecmr.api.model.compositions.Item;
import org.openlogisticsfoundation.ecmr.api.model.signature.Signature;
import org.openlogisticsfoundation.ecmr.domain.beans.ItemBean;
import org.openlogisticsfoundation.ecmr.domain.exceptions.EcmrNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.PdfCreationException;
import org.openlogisticsfoundation.ecmr.domain.mappers.EcmrPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.Document;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.domain.models.PdfFile;
import org.openlogisticsfoundation.ecmr.domain.services.documents.DocumentService;
import org.openlogisticsfoundation.ecmr.domain.services.documents.FileToPdfConverter;
import org.openlogisticsfoundation.ecmr.persistence.entities.EcmrEntity;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import net.sf.jasperreports.engine.data.JRBeanCollectionDataSource;
import net.sf.jasperreports.renderers.Renderable;
import net.sf.jasperreports.renderers.SimpleDataRenderer;

@Service
@RequiredArgsConstructor
@Log4j2
public class EcmrPdfService {

    private final ResourceLoader resourceLoader;
    private final EcmrService ecmrService;
    private final EcmrPersistenceMapper ecmrPersistenceMapper;
    private final SealMetadataService sealMetadataService;
    private final DocumentService documentService;
    private final FileToPdfConverter fileToPdfConverter;

    public PdfFile createJasperReportForEcmr(UUID id, InternalOrExternalUser internalOrExternalUser, boolean isCopy, boolean withDocuments)
            throws NoPermissionException, EcmrNotFoundException, PdfCreationException {
        EcmrModel ecmrModel = this.ecmrService.getEcmr(id, internalOrExternalUser);
        List<SealMetadata> sealMetadata = sealMetadataService.getSealMetadata(id, internalOrExternalUser);
        return this.createJasperReportForEcmr(ecmrModel, sealMetadata, isCopy, withDocuments);
    }

    public PdfFile createJasperReportForEcmrReader(UUID id, String shareToken, boolean isCopy, boolean withDocuments)
            throws NoPermissionException, EcmrNotFoundException, PdfCreationException {
        EcmrEntity ecmrEntity = this.ecmrService.getEcmrEntity(id);
        if (!ecmrEntity.getShareWithReaderToken().equals(shareToken)) {
            throw new NoPermissionException("Share Token mandatory");
        }

        List<SealMetadata> sealMetadata = sealMetadataService.getSealMetadata(id);
        return this.createJasperReportForEcmr(ecmrPersistenceMapper.toModel(ecmrEntity), sealMetadata, isCopy, withDocuments);
    }

    private PdfFile createJasperReportForEcmr(EcmrModel ecmrModel, List<SealMetadata> sealMetadata, boolean isCopy, boolean withDocuments)
            throws PdfCreationException {
        String filename = "eCMR-" + ecmrModel.getEcmrConsignment().getReferenceIdentificationNumber().getValue() + ".pdf";

        Consumer<OutputStream> pdfWriter = outputStream -> {
            try (InputStream ecmrReportStream = getClass().getResourceAsStream("/reports/ecmr.jrxml")) {
                JasperReport jasperReport = JasperCompileManager.compileReport(ecmrReportStream);

                List<ItemBean> itemBeans = convertToItemBeans(ecmrModel.getEcmrConsignment().getItemList());
                JRBeanCollectionDataSource itemDataSource = new JRBeanCollectionDataSource(itemBeans);

                Map<String, Object> parameters = setEcmrParameters(ecmrModel, sealMetadata, isCopy);
                parameters.put("items", itemDataSource);

                JasperPrint print = JasperFillManager.fillReport(jasperReport, parameters, new JREmptyDataSource());
                List<RandomAccessReadBuffer> pdfSources = new ArrayList<>();

                if (withDocuments) {
                    try (ByteArrayOutputStream jasperPdfTmp = new ByteArrayOutputStream()) {
                        JasperExportManager.exportReportToPdfStream(print, jasperPdfTmp);
                        pdfSources.add(new RandomAccessReadBuffer(jasperPdfTmp.toByteArray()));
                    }

                    List<RandomAccessReadBuffer> ecmrFiles = downloadEcmrFiles(UUID.fromString(ecmrModel.getEcmrId()));

                    pdfSources.addAll(ecmrFiles);

                    mergePdfs(pdfSources, outputStream);
                } else {
                    JasperExportManager.exportReportToPdfStream(print, outputStream);
                }
            } catch (JRException e) {
                throw new RuntimeException(new PdfCreationException("Error generating report"));
            } catch (IOException e) {
                throw new RuntimeException(new PdfCreationException("I/O error occurred"));
            }
        };

        return new PdfFile(filename, pdfWriter);
    }

    private List<RandomAccessReadBuffer> downloadEcmrFiles(UUID ecmrId) {
        List<Document> documents = documentService.getDocumentsByEcmrId(ecmrId);
        List<RandomAccessReadBuffer> sources = new ArrayList<>();

        documents.forEach(document -> {
            try (InputStream documentStream = documentService.downloadDocument(document.getId())) {
                sources.add(fileToPdfConverter.toPdf(document, documentStream));
            } catch (Exception e) {
                log.warn("Failed to process document with id {} | {}", document.getId(), e.getMessage());
            }
        });

        return sources;
    }

    private void mergePdfs(List<RandomAccessReadBuffer> sources, OutputStream destination) {
        PDFMergerUtility merger = new PDFMergerUtility();
        merger.setDestinationStream(destination);

        List<RandomAccessReadBuffer> buffers = new ArrayList<>();

        try {
            for (RandomAccessReadBuffer buffer : sources) {
                buffers.add(buffer);
                merger.addSource(buffer);
            }

            merger.mergeDocuments(null);
        } catch (IOException e) {
            throw new IllegalStateException("PDF merge failed", e);

        } finally {
            buffers.forEach(buffer -> {
                try {
                    buffer.close();
                } catch (IOException ignored) {
                }
            });
        }
    }

    private HashMap<String, Object> setEcmrParameters(EcmrModel ecmrModel, List<SealMetadata> sealMetadata, boolean isCopy) throws IOException {
        HashMap<String, Object> parameters = new HashMap<>();

        //sender data
        parameters.put("senderCompanyName", ecmrModel.getEcmrConsignment().getSenderInformation().getSenderCompanyName());
        parameters.put("senderPersonName", ecmrModel.getEcmrConsignment().getSenderInformation().getSenderPersonName());
        parameters.put("senderStreet", ecmrModel.getEcmrConsignment().getSenderInformation().getSenderStreet());
        parameters.put("senderPostCode", ecmrModel.getEcmrConsignment().getSenderInformation().getSenderPostcode());
        parameters.put("senderCity", ecmrModel.getEcmrConsignment().getSenderInformation().getSenderCity());
        parameters.put("senderCountry", ecmrModel.getEcmrConsignment().getSenderInformation().getSenderCountryCode().getValue());

        //consignee Data
        if (!ecmrModel.getEcmrConsignment().getMultiConsigneeShipment().getIsMultiConsigneeShipment()) {
            parameters.put("consigneeCompanyName", ecmrModel.getEcmrConsignment().getConsigneeInformation().getConsigneeCompanyName());
            parameters.put("consigneePersonName", ecmrModel.getEcmrConsignment().getConsigneeInformation().getConsigneePersonName());
            parameters.put("consigneeStreet", ecmrModel.getEcmrConsignment().getConsigneeInformation().getConsigneeStreet());
            parameters.put("consigneePostcode", ecmrModel.getEcmrConsignment().getConsigneeInformation().getConsigneePostcode());
            parameters.put("consigneeCity", ecmrModel.getEcmrConsignment().getConsigneeInformation().getConsigneeCity());
            parameters.put("consigneeCountryCode", ecmrModel.getEcmrConsignment().getConsigneeInformation().getConsigneeCountryCode().getValue());
        } else {
            parameters.put("multiConsigneeShipmentNotice", getMultiConsigneeShipmentText());
        }

        //taking over The goods
        parameters.put("takingOverTheGoodsPlace", ecmrModel.getEcmrConsignment().getTakingOverTheGoods().getTakingOverTheGoodsPlace());
        if (ecmrModel.getEcmrConsignment().getTakingOverTheGoods().getLogisticsTimeOfArrivalDateTime() != null)
            parameters.put("logisticsTimeOfArrivalDateTime",
                    Date.from(ecmrModel.getEcmrConsignment().getTakingOverTheGoods().getLogisticsTimeOfArrivalDateTime()));
        if (ecmrModel.getEcmrConsignment().getTakingOverTheGoods().getLogisticsTimeOfDepartureDateTime() != null)
            parameters.put("logisticsTimeOfDepartureDateTime",
                    Date.from(ecmrModel.getEcmrConsignment().getTakingOverTheGoods().getLogisticsTimeOfDepartureDateTime()));

        //carrier Data
        parameters.put("carrierCompanyName", ecmrModel.getEcmrConsignment().getCarrierInformation().getCarrierCompanyName());
        parameters.put("carrierDriverName", ecmrModel.getEcmrConsignment().getCarrierInformation().getCarrierDriverName());
        parameters.put("carrierPostcode", ecmrModel.getEcmrConsignment().getCarrierInformation().getCarrierPostcode());
        parameters.put("carrierStreet", ecmrModel.getEcmrConsignment().getCarrierInformation().getCarrierStreet());
        parameters.put("carrierCity", ecmrModel.getEcmrConsignment().getCarrierInformation().getCarrierCity());
        parameters.put("carrierCountry", ecmrModel.getEcmrConsignment().getCarrierInformation().getCarrierCountryCode().getValue());
        parameters.put("carrierLicensePlate", ecmrModel.getEcmrConsignment().getCarrierInformation().getCarrierLicensePlate());

        //successive carrier Data
        parameters.put("successiveCarrierCompanyName",
                ecmrModel.getEcmrConsignment().getSuccessiveCarrierInformation().getSuccessiveCarrierCompanyName());
        parameters.put("successiveCarrierDriverName",
                ecmrModel.getEcmrConsignment().getSuccessiveCarrierInformation().getSuccessiveCarrierDriverName());
        parameters.put("successiveCarrierStreetName", ecmrModel.getEcmrConsignment().getSuccessiveCarrierInformation().getSuccessiveCarrierStreet());
        parameters.put("successiveCarrierPostcode", ecmrModel.getEcmrConsignment().getSuccessiveCarrierInformation().getSuccessiveCarrierPostcode());
        parameters.put("successiveCarrierCity", ecmrModel.getEcmrConsignment().getSuccessiveCarrierInformation().getSuccessiveCarrierCity());
        parameters.put("successiveCarrierCountryCode",
                ecmrModel.getEcmrConsignment().getSuccessiveCarrierInformation().getSuccessiveCarrierCountryCode().getValue());

        //Carriers reservations
        parameters.put("carrierReservationsObservations",
                ecmrModel.getEcmrConsignment().getCarriersReservationsAndObservationsOnTakingOverTheGoods().getCarrierReservationsObservations());

        //Delivery of the goods
        parameters.put("deliveryOfTheGoodsPlace", ecmrModel.getEcmrConsignment().getDeliveryOfTheGoods().getLogisticsLocationCity());
        parameters.put("deliveryOfTheGoodsOpeningHours", ecmrModel.getEcmrConsignment().getDeliveryOfTheGoods().getLogisticsLocationOpeningHours());

        //Senders Instructions
        parameters.put("sendersInstructions", ecmrModel.getEcmrConsignment().getSendersInstructions().getTransportInstructionsDescription());

        //To be paid by
        parameters.put("customChargeCarriageValue", ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeCarriage().getValue());
        parameters.put("customChargeCarriageCurrency", ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeCarriage().getCurrency());
        if (ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeCarriage().getPayer() != null)
            parameters.put("customChargeCarriagePayer",
                    ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeCarriage().getPayer().toString());
        parameters.put("customChargeSupplementaryValue", ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeSupplementary().getValue());
        parameters.put("customChargeSupplementaryCurrency",
                ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeSupplementary().getCurrency());
        if (ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeSupplementary().getPayer() != null)
            parameters.put("customChargeSupplementaryPayer",
                    ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeSupplementary().getPayer().toString());
        parameters.put("customChargeCustomsDutiesValue", ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeCustomsDuties().getValue());
        parameters.put("customChargeCustomsDutiesCurrency",
                ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeCustomsDuties().getCurrency());
        if (ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeCustomsDuties().getPayer() != null)
            parameters.put("customChargeCustomsDutiesPayer",
                    ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeCustomsDuties().getPayer().toString());
        parameters.put("customChargeOtherValue", ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeOther().getValue());
        parameters.put("customChargeOtherCurrency", ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeOther().getCurrency());
        if (ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeOther().getPayer() != null)
            parameters.put("customChargeOtherPayer", ecmrModel.getEcmrConsignment().getToBePaidBy().getCustomChargeOther().getPayer().toString());

        //Documents
        parameters.put("documentsRemarks", ecmrModel.getEcmrConsignment().getDocumentsHandedToCarrier().getDocumentsRemarks());

        //Special Agreements
        parameters.put("customSpecialAgreement", ecmrModel.getEcmrConsignment().getSpecialAgreementsSenderCarrier().getCustomSpecialAgreement());

        //Particulars
        parameters.put("customParticulars", ecmrModel.getEcmrConsignment().getOtherUsefulParticulars().getCustomParticulars());

        //Cash on delivery
        parameters.put("customCashOnDelivery", ecmrModel.getEcmrConsignment().getCashOnDelivery().getCustomCashOnDelivery());

        //Established
        if (ecmrModel.getEcmrConsignment().getEstablished().getCustomEstablishedDate() != null) {
            parameters.put("customEstablishedDate", Date.from(ecmrModel.getEcmrConsignment().getEstablished().getCustomEstablishedDate()));
        }
        parameters.put("customEstablishedIn", ecmrModel.getEcmrConsignment().getEstablished().getCustomEstablishedIn());

        Optional<SealMetadata> senderSealMetaData = sealMetadata.stream().filter(x -> x.getRole() == TransportRole.SENDER).findFirst();
        Optional<SealMetadata> carrierSealMetaData = sealMetadata.stream().filter(x -> x.getRole() == TransportRole.CARRIER).findFirst();
        Optional<SealMetadata> consigneeSealMetaData = sealMetadata.stream().filter(x -> x.getRole() == TransportRole.CONSIGNEE).findFirst();

        //Sender Seal
        if (senderSealMetaData.isPresent()) {
            parameters.put("senderSealText", getSealText(senderSealMetaData.get()));
        }

        //Carrier Seal
        if (carrierSealMetaData.isPresent()) {
            parameters.put("carrierSealText", getSealText(carrierSealMetaData.get()));

            //Fields filled by the Consignee
            parameters.put("consigneeSigningLocation",
                    ecmrModel.getEcmrConsignment().getGoodsReceived().getConfirmedLogisticsLocationName());
            if (ecmrModel.getEcmrConsignment().getGoodsReceived().getConsigneeSignatureDate() != null) {
                parameters.put("consigneeSignatureDate",
                        Date.from(ecmrModel.getEcmrConsignment().getGoodsReceived().getConsigneeSignatureDate()));
            }
            parameters.put("consigneeReservationsObservations",
                    ecmrModel.getEcmrConsignment().getGoodsReceived().getConsigneeReservationsObservations());
        }

        //Consignee Signature
        if (consigneeSealMetaData.isPresent()) {
            parameters.put("consigneeSealText", getSealText(consigneeSealMetaData.get()));
        } else if (ecmrModel.getEcmrConsignment().getGoodsReceived().getConsigneeSignature() != null) {
            Renderable renderableSignature =
                    this.decodeImage(ecmrModel.getEcmrConsignment().getGoodsReceived().getConsigneeSignature().getData());
            parameters.put("consigneeSignatureImage", renderableSignature);
            parameters.put("consigneeSignatureText", getSignatureText(ecmrModel.getEcmrConsignment().getGoodsReceived().getConsigneeSignature()));
        }

        //National International Information Text
        EcmrTransportType ecmrTransportType = getEcmrTransportType(ecmrModel);
        if (ecmrTransportType != EcmrTransportType.UNKNOWN) {
            boolean isNational = (ecmrTransportType == EcmrTransportType.NATIONAL);
            parameters.put("DE_InternationalNationalTransport", getInformationText("DE", isNational));
            parameters.put("EN_InternationalNationalTransport", getInformationText("EN", isNational));
        }

        parameters.put("nonContractualCarrierRemarks",
                ecmrModel.getEcmrConsignment().getNonContractualPartReservedForTheCarrier().getNonContractualCarrierRemarks());
        parameters.put("referenceId", ecmrModel.getEcmrConsignment().getReferenceIdentificationNumber().getValue());
        parameters.put("ecmrId", ecmrModel.getEcmrId());

        //eCmr Logo
        if (EcmrTransportType.INTERNATIONAL == ecmrTransportType) {
            InputStream imageStream = resourceLoader.getResource("classpath:/images/cmrLogo.png").getInputStream();
            byte[] waterMarkBytes = imageStream.readAllBytes();
            Renderable renderableWaterMark = SimpleDataRenderer.getInstance(waterMarkBytes);
            parameters.put("ecmrLogo", renderableWaterMark);
        }

        //Copy Watermark
        if (isCopy) {
            InputStream imageStream = resourceLoader.getResource("classpath:/images/Copy-Wasserzeichen-DIN4.png").getInputStream();
            byte[] waterMarkBytes = imageStream.readAllBytes();
            Renderable renderableWaterMark = SimpleDataRenderer.getInstance(waterMarkBytes);
            parameters.put("watermark", renderableWaterMark);
        }

        return parameters;
    }

    private String getMultiConsigneeShipmentText() throws IOException {
        String language = "EN";
        String filePath = "reports/texts/" + language + "_MultiConsigneeShipment.txt";
        InputStream resource = new ClassPathResource(filePath).getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(resource));
        return reader.lines().collect(Collectors.joining());
    }

    private String getInformationText(String language, boolean isNational) throws IOException {
        String filePath = "reports/texts/" + language + (isNational ? "_NationalTransport.txt" : "_InternationalTransport.txt");
        InputStream resource = new ClassPathResource(filePath).getInputStream();
        BufferedReader reader = new BufferedReader(new InputStreamReader(resource));
        return reader.lines().collect(Collectors.joining());
    }

    private EcmrTransportType getEcmrTransportType(EcmrModel ecmrModel) {
        String senderCountry = ecmrModel.getEcmrConsignment().getSenderInformation().getSenderCountryCode().getValue();
        String consigneeCountry = ecmrModel.getEcmrConsignment().getConsigneeInformation().getConsigneeCountryCode().getValue();

        if (senderCountry == null || consigneeCountry == null) {
            return EcmrTransportType.UNKNOWN;
        }

        return senderCountry.equals(consigneeCountry) ? EcmrTransportType.NATIONAL : EcmrTransportType.INTERNATIONAL;
    }

    private enum EcmrTransportType {
        INTERNATIONAL,
        NATIONAL,
        UNKNOWN
    }

    private Renderable decodeImage(String base64Image) throws IOException {
        try {
            if (base64Image == null || !base64Image.contains(",")) {
                throw new IllegalArgumentException("Invalid base64 image string");
            }
            String base64ImageString = base64Image.split(",")[1];
            byte[] imageBytes = Base64.getDecoder().decode(base64ImageString);
            return SimpleDataRenderer.getInstance(imageBytes);
        } catch (Exception e) {
            log.error("Error while decoding image", e);
            throw new IOException(e);
        }
    }

    private String getSealText(SealMetadata sealMetadata) {
        String sealerText = sealMetadata.getSealer() != null ? sealMetadata.getSealer() : "";
        String sealerCompany = sealMetadata.getSealerCompany() != null ? "\r\n" + sealMetadata.getSealerCompany() : "";
        String formattedDate = this.getFormattedDate(sealMetadata.getTimestamp());
        return "Signed with eSeal on:\r\n" + formattedDate + "\r\nBy:\r\n" + sealerText + sealerCompany;
    }

    private String getSignatureText(Signature signature) {
        return signature.getUserName() + " - " + this.getFormattedDate(signature.getTimestamp());
    }

    private String getFormattedDate(Instant timestamp) {
        if (timestamp == null) {
            return "-";
        }
        Date date = Date.from(timestamp);
        SimpleDateFormat formatter = new SimpleDateFormat("dd.MM.yyyy");
        return formatter.format(date);
    }

    private List<ItemBean> convertToItemBeans(List<Item> items) {
        List<ItemBean> itemBeans = new ArrayList<>();

        for (Item item : items) {
            ItemBean itemBean = new ItemBean();

            itemBean.setLogisticsShippingMarksMarking(item.getMarksAndNos().getLogisticsShippingMarksMarking());
            itemBean.setLogisticsShippingMarksCustomBarcode(item.getMarksAndNos().getLogisticsShippingMarksCustomBarcodeList().stream()
                    .map(LogisticsShippingMarksCustomBarcode::getBarcode).collect(Collectors.joining(", ")));
            itemBean.setLogisticsPackageItemQuantity(item.getNumberOfPackages().getLogisticsPackageItemQuantity());
            itemBean.setLogisticsPackageType(item.getMethodOfPacking().getLogisticsPackageType());
            itemBean.setTransportCargoIdentification(item.getNatureOfTheGoods().getTransportCargoIdentification());
            itemBean.setSupplyChainConsignmentItemGrossWeight(item.getGrossWeightInKg().getSupplyChainConsignmentItemGrossWeight());
            itemBean.setSupplyChainConsignmentItemGrossVolume(item.getVolumeInM3().getSupplyChainConsignmentItemGrossVolume());
            itemBeans.add(itemBean);
        }
        return itemBeans;
    }
}
