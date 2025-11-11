/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.domain.exceptions;

public class MailSuffixNotFoundException extends Exception {
    public MailSuffixNotFoundException(Long id) {
        super("MailSuffix with id " + id + " not found");
    }
}
