#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
mkdir -p .buildtools
if [ ! -x .buildtools/gradle-2.14.1/bin/gradle ]; then
  curl --fail --location --retry 3 -o .buildtools/gradle.zip https://services.gradle.org/distributions/gradle-2.14.1-bin.zip
  unzip -q -o .buildtools/gradle.zip -d .buildtools
  rm .buildtools/gradle.zip
fi
.buildtools/gradle-2.14.1/bin/gradle --no-daemon clean build
echo "JAR: build/libs/ByteBitShop-1.0.2.jar"
