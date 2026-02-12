# eCMR API versions
This file lists all versions of the eCMR API as defined by the openapi.yml file and implemented in the current
ecmr-backend. This file starts with the latest version.

0.2.1 (2026-02-11)
---------------------------
* Added:
  * Description for all tags

* Changed:
  * Order of tags

eCMR data model version: 1.1.1

0.2.0 (2026-02-05)
---------------------------
* Added
  * Anonymous:
    * GET /anonymous/ecmr/{ecmrId}/seal-metadata
  * Approved URLs:
    * GET /approved-url
    * POST /approved-url
    * POST /approved-url/multiple
    * PUT /approved-url/{id}
    * DELETE /approved-url/{approvedUrlId}
  * Document:
    * GET /document
    * POST /document
    * GET /document/{id}/download
    * DELETE /document/{id}
  * ECMR:
    * PATCH /ecmr/archive
    * DELETE /ecmr/selected
    * GET /ecmr/{ecmrId}/seal-metadata
  * ECMR Import:
    * GET /ecmr-import
    * GET /ecmr-import/pending-instances
    * PUT /ecmr-import/handle-approval
  * Sync:
    * PUT /sync
  * Mail Suffix:
    * POST /mail-suffix
    * PUT /mail-suffix/{id}
    * DELETE /mail-suffix/{id}
    * POST /mail-suffix/import/{approvedUrlId}
    * GET /mail-suffix/for-approved-url/{approvedUrlId}

* Removed
  * POST /ecmr/import-external
  * GET /ecmr/{ecmrId}/import
  * GET /sealed-document/{ecmrId}
  * GET /anonymous/sealed-document/{ecmrId}
  * GET /external/ecmr/{ecmrId}/export

eCMR data model version: 1.1.1

0.1.1 (2025-11-19)
---------------------------
* Breaking
  * Used new eCMR data model version 1.1.1

eCMR data model version: 1.1.1

0.1.0 (2025-09-17)
---------------------------
* Breaking
  * Renamed schema SharedInformationModel to ExternalUserInformationModel

eCMR data model version: 1.0.4

0.0.16 (2025-09-17)
---------------------------
* Added
  * First API version

eCMR data model version: 1.0.4
