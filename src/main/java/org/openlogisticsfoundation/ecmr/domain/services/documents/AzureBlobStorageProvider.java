/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services.documents;

import java.io.InputStream;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobServiceClient;
import com.azure.storage.blob.BlobServiceClientBuilder;

@ConditionalOnProperty(prefix = "ecmr.storage", name = "provider", havingValue = "azureblob")
@Service
class AzureBlobStorageProvider implements DocumentStorageProvider {
    private static final String CONTAINER_NAME = "ecmr-documents";

    private final BlobContainerClient blobContainerClient;

    public AzureBlobStorageProvider(@Value("${ecmr.storage.azureblob.connection-string}") String connectionString) {
        BlobServiceClient blobServiceClient = new BlobServiceClientBuilder()
                .connectionString(connectionString)
                .buildClient();
        blobContainerClient = blobServiceClient.createBlobContainerIfNotExists(CONTAINER_NAME);
    }

    @Override
    public String getStorageProviderName() {
        return "azureblob";
    }

    @Override
    public String uploadFile(UUID ecmrId, InputStream inputStream) {
        String fileId = generateUniqueFileId(ecmrId);
        BlobClient blobClient = blobContainerClient.getBlobClient(fileId);
        blobClient.upload(inputStream);
        blobClient.setTags(Map.of("EcmrId", ecmrId.toString()));
        return fileId;
    }

    @Override
    public InputStream downloadFile(String documentIdentifier) throws StorageProviderException {
        BlobClient blobClient = blobContainerClient.getBlobClient(documentIdentifier);
        return blobClient.openInputStream();
    }

    @Override
    public boolean deleteFile(String documentIdentifier) {
        BlobClient blobClient = blobContainerClient.getBlobClient(documentIdentifier);
        return blobClient.deleteIfExists();
    }
}
