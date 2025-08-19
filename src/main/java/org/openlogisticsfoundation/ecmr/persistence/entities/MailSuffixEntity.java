/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */
package org.openlogisticsfoundation.ecmr.persistence.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "MAIL_SUFFIX", indexes = {
        @Index(columnList = "mailSuffix")
})
@Getter
@Setter
@NoArgsConstructor
public class MailSuffixEntity extends BaseEntity {
    @Column(nullable = false, unique = true)
    private String mailSuffix;
    @Column(nullable = false)
    private String url;
}
