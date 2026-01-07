/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services.documents;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.openlogisticsfoundation.ecmr.domain.exceptions.DocumentNotFoundException;
import org.openlogisticsfoundation.ecmr.domain.exceptions.NoPermissionException;
import org.openlogisticsfoundation.ecmr.domain.mappers.DocumentPersistenceMapper;
import org.openlogisticsfoundation.ecmr.domain.models.AuthenticatedUser;
import org.openlogisticsfoundation.ecmr.domain.models.Document;
import org.openlogisticsfoundation.ecmr.domain.models.InternalOrExternalUser;
import org.openlogisticsfoundation.ecmr.domain.services.AuthorisationService;
import org.openlogisticsfoundation.ecmr.persistence.entities.DocumentEntity;
import org.openlogisticsfoundation.ecmr.persistence.repositories.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;

@Service
@RequiredArgsConstructor
@Log4j2
public class DocumentService {
    private final DocumentStorageProvider documentStorageProvider;
    private final AuthorisationService authorisationService;
    private final DocumentRepository documentRepository;
    private final DocumentPersistenceMapper documentPersistenceMapper;

    public Document uploadDocument(UUID ecmrId, MultipartFile file, AuthenticatedUser authenticatedUser) throws IOException, NoPermissionException {
        InternalOrExternalUser user = new InternalOrExternalUser(authenticatedUser.getUser());
        if (authorisationService.hasNoRole(user, ecmrId)) {
            throw new NoPermissionException("No permission to upload documents for ECMR: " + ecmrId);
        }
        String storageProvider = documentStorageProvider.getStorageProviderName();
        String uploadedDocumentId = documentStorageProvider.uploadFile(ecmrId, file.getInputStream());
        DocumentEntity documentEntity = new DocumentEntity();
        documentEntity.setDocumentId(uploadedDocumentId);
        documentEntity.setSize(file.getSize());
        documentEntity.setStorageProvider(storageProvider);
        documentEntity.setEcmrId(ecmrId);
        documentEntity.setFileName(file.getOriginalFilename());
        documentEntity.setMimeType(file.getContentType());
        documentEntity.setUploadDate(Instant.now());
        documentEntity = documentRepository.save(documentEntity);
        return documentPersistenceMapper.toDocument(documentEntity);
    }

    public List<Document> getDocumentsByEcmrId(UUID ecmrId, AuthenticatedUser authenticatedUser) throws NoPermissionException {
        InternalOrExternalUser user = new InternalOrExternalUser(authenticatedUser.getUser());
        if (authorisationService.hasNoRole(user, ecmrId)) {
            throw new NoPermissionException("No permission to get documents for ECMR: " + ecmrId);
        }
        return documentRepository.findByEcmrId(ecmrId).stream().map(documentPersistenceMapper::toDocument).toList();
    }

    public Document getDocument(long documentId, AuthenticatedUser authenticatedUser) throws NoPermissionException, DocumentNotFoundException {
        DocumentEntity documentEntity = documentRepository.findById(documentId).orElseThrow(() -> new DocumentNotFoundException(documentId));
        InternalOrExternalUser user = new InternalOrExternalUser(authenticatedUser.getUser());
        if (authorisationService.hasNoRole(user, documentEntity.getEcmrId())) {
            throw new NoPermissionException("No permission to get document for ECMR: " + documentEntity.getEcmrId());
        }
        return documentPersistenceMapper.toDocument(documentEntity);
    }

    public InputStream downloadDocument(long documentId, AuthenticatedUser authenticatedUser) throws NoPermissionException, DocumentNotFoundException {
        DocumentEntity documentEntity = documentRepository.findById(documentId).orElseThrow(() -> new DocumentNotFoundException(documentId));
        InternalOrExternalUser user = new InternalOrExternalUser(authenticatedUser.getUser());
        if (authorisationService.hasNoRole(user, documentEntity.getEcmrId())) {
            throw new NoPermissionException("No permission to download documents for ECMR: " + documentEntity.getEcmrId());
        }
        return documentStorageProvider.downloadFile(documentEntity.getDocumentId());
    }

    public void deleteDocument(long documentId, AuthenticatedUser authenticatedUser) throws NoPermissionException, DocumentNotFoundException {
        DocumentEntity documentEntity = documentRepository.findById(documentId).orElseThrow(() -> new DocumentNotFoundException(documentId));
        InternalOrExternalUser user = new InternalOrExternalUser(authenticatedUser.getUser());
        if (authorisationService.hasNoRole(user, documentEntity.getEcmrId())) {
            throw new NoPermissionException("No permission to delete document for ECMR: " + documentEntity.getEcmrId());
        }
        documentStorageProvider.deleteFile(documentEntity.getDocumentId());
        documentRepository.delete(documentEntity);
    }
}
