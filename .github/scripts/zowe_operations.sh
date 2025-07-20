#!/bin/bash

LOWERCASE_USERNAME=$(echo "$ZOWE_USERNAME" | tr '[:upper:]' '[:lower:]')

# Directory check and create
if ! zowe zos-files list uss-files "/z/$LOWERCASE_USERNAME/cobolcheck" \
    --host "$ZOSMF_HOST" \
    --port "$ZOSMF_PORT" \
    --user "$ZOWE_USERNAME" \
    --password "$ZOWE_PASSWORD" \
    --reject-unauthorized false &>/dev/null; then
  echo "Creating directory..."
  zowe zos-files create uss-directory "/z/$LOWERCASE_USERNAME/cobolcheck" \
    --host "$ZOSMF_HOST" \
    --port "$ZOSMF_PORT" \
    --user "$ZOWE_USERNAME" \
    --password "$ZOWE_PASSWORD" \
    --reject-unauthorized false
fi

# Upload files
zowe zos-files upload dir-to-uss "./cobol-check" "/z/$LOWERCASE_USERNAME/cobolcheck" \
  --recursive --binary-files "cobol-check-0.2.18.jar" \
  --host "$ZOSMF_HOST" \
  --port "$ZOSMF_PORT" \
  --user "$ZOWE_USERNAME" \
  --password "$ZOWE_PASSWORD" \
  --reject-unauthorized false
# Verify upload
echo "Verifying upload:"
zowe zos-files list uss-files "/z/$LOWERCASE_USERNAME/cobolcheck" \
  --host "$ZOSMF_HOST" \
  --port "$ZOSMF_PORT" \
  --user "$ZOWE_USERNAME" \
  --password "$ZOWE_PASSWORD" \
  --reject-unauthorized false