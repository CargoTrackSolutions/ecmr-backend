/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services;

import java.net.URI;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.xbill.DNS.DClass;
import org.xbill.DNS.Name;
import org.xbill.DNS.Record;
import org.xbill.DNS.TextParseException;
import org.xbill.DNS.Type;
import org.xbill.DNS.lookup.LookupResult;
import org.xbill.DNS.lookup.LookupSession;

@Service
public class SealDnsFingerprintVerificationService {

    private static final String DNS_ENTRY_NAME = "ecmr-sha256-fingerprint=";

    public List<byte[]> getTrustedSha256Fingerprints(URI uri) throws TextParseException, ExecutionException, InterruptedException {
        LookupSession s = LookupSession.defaultBuilder().build();
        String uriString = uri.toString();
        uriString = uriString.endsWith(".") ? uriString : uriString + ".";

        List<byte[]> fingerprints;

        do {
            Record record = Record.newRecord(Name.fromString(uriString), Type.TXT, DClass.IN);
            LookupResult lookupResult = s.lookupAsync(record).toCompletableFuture().get();
            fingerprints = lookupResult.getRecords().stream()
                    .map(Record::rdataToString)
                    .filter(Objects::nonNull)
                    .map(recordData -> StringUtils.strip(recordData, "\""))
                    .map(String::toLowerCase)
                    .filter(recordData -> recordData.startsWith(DNS_ENTRY_NAME))
                    .map(recordData -> recordData.substring(DNS_ENTRY_NAME.length()))
                    .map(sha256FingerprintBase64 -> Base64.getDecoder().decode(sha256FingerprintBase64))
                    .toList();

            if (fingerprints.isEmpty()) {
                int firstDotIndex = uriString.indexOf(".");
                uriString = uriString.substring(firstDotIndex + 1);
            }

        } while (fingerprints.isEmpty() && StringUtils.countMatches(uriString, ".") > 1);
        return fingerprints;
    }

}
