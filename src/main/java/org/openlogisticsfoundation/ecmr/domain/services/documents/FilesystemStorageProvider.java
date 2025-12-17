/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services.documents;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@ConditionalOnProperty(prefix = "ecmr.storage", name = "provider", havingValue = "filesystem")
@Service
public class FilesystemStorageProvider implements DocumentStorageProvider {
    @Value("${ecmr.storage.filesystem.directory}")
    private String directory;

    @Override
    public String getStorageProviderName() {
        return "filesystem";
    }

    public String uploadFile(UUID ecmrId, InputStream inputStream) throws StorageProviderException {
        String documentIdentifier = generateUniqueFileId(ecmrId);
        try (FileOutputStream fileOutputStream = new FileOutputStream(getFullPath(documentIdentifier))) {
            inputStream.transferTo(fileOutputStream);
            return documentIdentifier;
        } catch (IOException e) {
            throw new StorageProviderException(e);
        }
    }

    @Override
    public InputStream downloadFile(String documentIdentifier) throws StorageProviderException {
        try {
            return new FileInputStream(getFullPath(documentIdentifier));
        } catch (FileNotFoundException e) {
            throw new StorageProviderException(e);
        }
    }

    @Override
    public boolean deleteFile(String documentIdentifier) {
        String fullPath = getFullPath(documentIdentifier);
        return new File(fullPath).delete();
    }

    private String getFullPath(String documentIdentifier) {
        return directory + File.separator + documentIdentifier;
    }
}
