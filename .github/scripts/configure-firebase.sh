#!/usr/bin/env bash
set -euo pipefail

if [[ -n "${GOOGLE_SERVICES_JSON:-}" ]]; then
  printf '%s' "$GOOGLE_SERVICES_JSON" > app/google-services.json
elif [[ -n "${GOOGLE_SERVICES_JSON_BASE64:-}" ]]; then
  printf '%s' "$GOOGLE_SERVICES_JSON_BASE64" | base64 --decode > app/google-services.json
else
  cat > app/google-services.json <<'JSON'
{
  "project_info": {
    "project_number": "123456789012",
    "project_id": "toplu-tasima-ci",
    "storage_bucket": "toplu-tasima-ci.appspot.com"
  },
  "client": [
    {
      "client_info": {
        "mobilesdk_app_id": "1:123456789012:android:0000000000000000000000",
        "android_client_info": {
          "package_name": "com.example.toplutasima"
        }
      },
      "oauth_client": [],
      "api_key": [
        {
          "current_key": "ci-placeholder-firebase"
        }
      ],
      "services": {
        "appinvite_service": {
          "other_platform_oauth_client": []
        }
      }
    }
  ],
  "configuration_version": "1"
}
JSON
fi
