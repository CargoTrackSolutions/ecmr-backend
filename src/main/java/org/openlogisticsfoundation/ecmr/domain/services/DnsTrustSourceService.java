/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.domain.services;

import java.security.cert.X509Certificate;
import java.util.List;

import org.jetbrains.annotations.NotNull;

import ecmr.seal.verify.dss.TrustSourceService;
import eu.europa.esig.dss.model.x509.CertificateToken;
import eu.europa.esig.dss.spi.x509.CommonTrustedCertificateSource;
import eu.europa.esig.dss.spi.x509.ListCertificateSource;

public class DnsTrustSourceService implements TrustSourceService {

    private final List<X509Certificate> certificates;

    public DnsTrustSourceService(List<X509Certificate> certificates) {
        this.certificates = certificates;
    }

    public @NotNull ListCertificateSource getTrustSources() {
        CommonTrustedCertificateSource trustedCertificateSource = new CommonTrustedCertificateSource();
        for (X509Certificate certificate : certificates) {
            trustedCertificateSource.addCertificate(new CertificateToken(certificate));
        }
        ListCertificateSource listCertificateSource = new ListCertificateSource();
        listCertificateSource.add(trustedCertificateSource);

        return listCertificateSource;
    }
}
