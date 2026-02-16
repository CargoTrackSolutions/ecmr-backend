/*
 * Copyright Open Logistics Foundation
 *
 * Licensed under the Open Logistics Foundation License 1.3.
 * For details on the licensing terms, see the LICENSE file.
 * SPDX-License-Identifier: OLFL-1.3
 */

package org.openlogisticsfoundation.ecmr.persistence.migration;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import liquibase.change.custom.CustomTaskChange;
import liquibase.database.Database;
import liquibase.database.jvm.JdbcConnection;
import liquibase.exception.CustomChangeException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

//Used in liquibase migration id 20260213-02-migrate-barcodes
public class LiquibaseBarcodeMigration implements CustomTaskChange {
    @Override
    public void execute(Database database) throws CustomChangeException {
        JdbcConnection jdbcConnection = (JdbcConnection) database.getConnection();
        Connection connection = jdbcConnection.getUnderlyingConnection();

        try {

            Map<Long, List<String>> barcodeMap = new HashMap<>();

            // 1. Alle Barcodes laden
            try (PreparedStatement ps = connection.prepareStatement(
                    "SELECT item_id, barcode FROM logistics_shipping_marks_custom_barcode ORDER BY item_id, id");
                    ResultSet rs = ps.executeQuery()) {

                while (rs.next()) {
                    Long itemId = rs.getLong("item_id");
                    String barcode = rs.getString("barcode");

                    barcodeMap
                            .computeIfAbsent(itemId, k -> new ArrayList<>())
                            .add(barcode);
                }
            }

            // 2. Parent-Tabelle updaten
            try (PreparedStatement update = connection.prepareStatement(
                    "UPDATE item SET logistics_shipping_marks_custom_barcodes = ? WHERE id = ?")) {

                for (Map.Entry<Long, List<String>> entry : barcodeMap.entrySet()) {
                    String joined = String.join("|", entry.getValue());

                    update.setString(1, joined);
                    update.setLong(2, entry.getKey());
                    update.addBatch();
                }

                update.executeBatch();
            }

        } catch (Exception e) {
            throw new CustomChangeException("Migration failed", e);
        }
    }

    @Override
    public String getConfirmationMessage() {
        return "Barcodes successfully migrated.";
    }

    @Override
    public void setUp() {
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
    }

    @Override
    public ValidationErrors validate(Database database) {
        return null;
    }
}
