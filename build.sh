#!/bin/bash
set -e

rm -rf dist
mkdir -p dist
cp -r frontend/. dist/

if [ -n "$API_BASE_URL" ]; then
  sed -i "s|http://localhost:8081/api|${API_BASE_URL}|g" dist/js/app.js
  echo "API_BASE -> $API_BASE_URL"
else
  echo "Warning: API_BASE_URL not set, keeping localhost"
fi
