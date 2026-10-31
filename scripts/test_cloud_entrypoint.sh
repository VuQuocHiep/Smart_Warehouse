#!/bin/sh
# Run inside the backend image with the repository mounted read-only at /repo.
set -eu
keytool -genkeypair -alias test-ca -dname CN=warehouse-test-ca -keyalg RSA -keysize 2048 \
    -validity 2 -ext bc=ca:true -keystore /tmp/test-ca.p12 -storetype PKCS12 \
    -storepass changeit -keypass changeit >/dev/null 2>&1
APP_CLOUD=true
AIVEN_CA_PEM="$(keytool -exportcert -rfc -alias test-ca -keystore /tmp/test-ca.p12 -storepass changeit)"
export APP_CLOUD AIVEN_CA_PEM
# Exercise only certificate setup, without starting a second Spring process.
mkdir -p /tmp/test-bin
printf '#!/bin/sh\nexit 0\n' > /tmp/test-bin/java
chmod +x /tmp/test-bin/java
PATH="/tmp/test-bin:$PATH"
export PATH
sh /repo/backend/iot/docker-entrypoint.sh
sh /repo/backend/iot/docker-entrypoint.sh
keytool -list -alias aiven -keystore /tmp/aiven-truststore.p12 -storepass changeit >/dev/null
echo 'PASS: valid CA imported; repeated startup replaces the truststore successfully'
