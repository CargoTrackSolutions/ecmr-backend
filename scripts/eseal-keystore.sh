#!/usr/bin/env bash
# Creates the eSeal signing identity of this eCMR instance and prints the DNS TXT record other instances
# use to trust its seals.
#
#   scripts/eseal-keystore.sh <out-dir> <common-name> [validity-days]
#   e.g. ESEAL_KEYSTORE_PASSWORD=... scripts/eseal-keystore.sh ./eseal ecmr-api.cargotrack.ro 1095
#
# Output in <out-dir>:  eseal.p12 (PKCS12, alias cargotrack-ecmr)  and  eseal-cert.pem (public certificate)
# Self-signed RSA 3072 / SHA-256, like the upstream test certificate: trust comes from the DNS fingerprint,
# not from a CA. For a purchased (e.g. qualified) seal certificate, build the PKCS12 from it and run
# scripts/eseal-fingerprint.sh on the certificate instead.
#
# Requires OpenSSL 3. The private key never leaves the PKCS12; keep eseal.p12 and the password in the secret store.
set -euo pipefail

OUT="${1:?usage: $0 <out-dir> <common-name> [validity-days]}"
CN="${2:?usage: $0 <out-dir> <common-name> [validity-days]}"
DAYS="${3:-1095}"
ALIAS="cargotrack-ecmr"

if [ -z "${ESEAL_KEYSTORE_PASSWORD:-}" ]; then
  read -r -s -p "Keystore password: " ESEAL_KEYSTORE_PASSWORD; echo
fi
[ -n "$ESEAL_KEYSTORE_PASSWORD" ] || { echo "Empty password" >&2; exit 1; }
[ ! -e "$OUT/eseal.p12" ] || { echo "$OUT/eseal.p12 exists - refusing to overwrite a signing identity" >&2; exit 1; }

mkdir -p "$OUT"
umask 077
tmp="$(mktemp -d)"
trap 'rm -rf "$tmp"' EXIT

# MSYS2_ARG_CONV_EXCL: Git Bash on Windows would otherwise rewrite the "/C=..." subject into a file path
MSYS2_ARG_CONV_EXCL="/C=" openssl req -x509 -newkey rsa:3072 -sha256 -days "$DAYS" -nodes \
  -subj "/C=RO/O=Cargo Track Solutions S.R.L./OU=eCMR/CN=$CN" \
  -keyout "$tmp/key.pem" -out "$OUT/eseal-cert.pem"

export ESEAL_KEYSTORE_PASSWORD
openssl pkcs12 -export -name "$ALIAS" -inkey "$tmp/key.pem" -in "$OUT/eseal-cert.pem" \
  -out "$OUT/eseal.p12" -passout env:ESEAL_KEYSTORE_PASSWORD

echo "Created $OUT/eseal.p12 (alias $ALIAS) and $OUT/eseal-cert.pem, valid $DAYS days"
echo
"$(dirname "$0")/eseal-fingerprint.sh" "$OUT/eseal-cert.pem"
cat <<EOF

Backend environment:
  ECMR_SEAL_SIGN_KEYSTORETYPE=PKCS12
  ECMR_SEAL_SIGN_KEYSTOREPATH=/secrets/eseal.p12
  ECMR_SEAL_SIGN_KEYALIAS=$ALIAS
  ECMR_SEAL_SIGN_KEYSTOREPASSWORD=<password>
  ECMR_SEAL_SIGN_KEYPASSWORD=<password>
EOF
