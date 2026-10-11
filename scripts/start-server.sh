#!/bin/sh
set -eu

# Optional Aiven CA import into a COPY of the standard JVM trust store.
# This preserves trust in Atlas, Cloudinary, Resend and payment HTTPS endpoints.
# It does not weaken TLS verification or include the CA in the Docker image.
if [ -n "${AIVEN_CA_CERT_BASE64:-}" ]; then
  temp_dir="$(mktemp -d)"
  printf '%s' "$AIVEN_CA_CERT_BASE64" | base64 -d > "$temp_dir/aiven-ca.pem"
  cp "$JAVA_HOME/lib/security/cacerts" "$temp_dir/cacerts"
  keytool -importcert -trustcacerts -noprompt -alias aiven-mysql \
    -file "$temp_dir/aiven-ca.pem" -keystore "$temp_dir/cacerts" \
    -storepass changeit >/dev/null
  rm "$temp_dir/aiven-ca.pem"
  exec java -Djavax.net.ssl.trustStore="$temp_dir/cacerts" \
    -Djavax.net.ssl.trustStorePassword=changeit \
    -XX:MaxRAMPercentage=75 -jar app.jar --server.port="${PORT:-10000}"
fi

exec java -XX:MaxRAMPercentage=75 -jar app.jar --server.port="${PORT:-10000}"
