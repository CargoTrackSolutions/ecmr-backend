/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services.documents;

import java.io.InputStream;
import java.util.UUID;

interface DocumentStorageProvider {
    /**
     *
     * @return Name of the storage provider implementation
     */
    String getStorageProviderName();

    /**
     *
     * @return Returns the unique identifier for this file that can later be used to download this file again
     */
    String uploadFile(UUID ecmrId, InputStream inputStream) throws StorageProviderException;

    /**
     *
     * @return Returns a stream that contains the content of the file
     */
    InputStream downloadFile(String documentId) throws StorageProviderException;

    default String generateUniqueFileId(UUID ecmrId) {
        return ecmrId.toString() + "--" + UUID.randomUUID().toString();
    }

    /**
     *
     * @param documentId
     * @return true when file was deleted, false when file does not exist
     */
    boolean deleteFile(String documentId);
}
