#!/usr/bin/env bash
# Requires: GraalVM 21+ (Java 21) with `native-image` installed.
#
# This script builds the Clojure uberjar via depstar, then runs native-image
# using reflection and resource configurations to output a self-contained `./thorold` binary.

set -euo pipefail

echo "==> 1. Clean previous build artifacts..."
rm -rf target/
rm -f thorold

echo "==> 2. Building uberjar with depstar..."
clojure -X:uberjar

echo "==> 3. Building self-contained native binary with native-image..."
# --no-fallback: force compilation to fail if fully static/standalone binary is not achievable, rather than fallback to JVM.
# -H:ConfigurationFileDirectories: path to reflection/resource/etc configs.
native-image \
  -jar target/thorold.jar \
  -H:ConfigurationFileDirectories=native-image-config \
  --no-fallback \
  -H:Name=thorold

echo "==> Build complete! Native binary created at ./thorold"
