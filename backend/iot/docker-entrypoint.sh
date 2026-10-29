#!/bin/sh
set -eu

# Render secret env accepts the PEM downloaded from Aiven. Only the CA is written here.
if [ "${APP_CLOUD:-false}" = "true" ]; then
    : "${AIVEN_CA_PEM:?Set AIVEN_CA_PEM to the Aiven project CA certificate}"
    printf '%s\n' "$AIVEN_CA_PEM" > /tmp/aiven-ca.pem
    # Rebuild atomically: a restart can retain /tmp and an existing alias must not break boot.
    trust_directory="$(mktemp -d /tmp/warehouse-tls.XXXXXX)"
    keytool -importcert -noprompt -alias aiven -file /tmp/aiven-ca.pem \
        -keystore "$trust_directory/store.p12" -storetype PKCS12 -storepass changeit >/dev/null
    mv "$trust_directory/store.p12" /tmp/aiven-truststore.p12
    rmdir "$trust_directory"
fi
exec java -jar /app/app.jar
