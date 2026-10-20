#!/usr/bin/env bash
# Prints the DNS TXT record that lets other eCMR instances trust the seals signed with this certificate.
#
#   scripts/eseal-fingerprint.sh <certificate.pem>
#
# Value: Base64 of the SHA-256 digest of the DER certificate - the same digest the seal carries in its
# x5t#S256 header. The verifying instance looks it up as TXT on the host of the sealing instance's
# APP_ORIGIN_URL, then on each parent domain (e.g. ecmr-api.cargotrack.ro, then cargotrack.ro).
set -euo pipefail

CERT="${1:?usage: $0 <certificate.pem>}"
FP="$(openssl x509 -in "$CERT" -outform DER | openssl dgst -sha256 -binary | openssl base64 -A)"

echo "Certificate: $(openssl x509 -in "$CERT" -noout -subject -enddate | tr '\n' ' ')"
echo "DNS TXT record (on the backend host name of APP_ORIGIN_URL):"
echo "  ecmr-sha256-fingerprint=$FP"
