#!/usr/bin/env bash
# Author: dishanhai
set -euo pipefail
cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.."

gradle_version="${1:?Specify the Gradle version}"
project_dir="${2:?Specify the project directory}"
mode="${3:-build}"
case "$mode:$project_dir" in
  build:.|build:neoforge-*|test:.|test:neoforge-*|test:bridge) ;;
  *) printf 'Unsupported CI mode/project: %s:%s\n' "$mode" "$project_dir" >&2; exit 2 ;;
esac
case "$gradle_version:$project_dir" in
  8.8:.)
    gradle_sha=a4b4158601f8636cdeeab09bd76afb640030bb5b144aafe261a5e8af027dc612
    ;;
  9.2.1:neoforge-1.21.1|9.2.1:neoforge-26.1.2|9.2.1:neoforge-26.2|9.2.1:bridge)
    gradle_sha=72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f
    ;;
  *)
    printf 'Unsupported Gradle/project pair: %s:%s\n' "$gradle_version" "$project_dir" >&2
    exit 2
    ;;
esac

download() {
  curl --fail --silent --show-error --location --retry 3 --connect-timeout 20 --max-time 180 "$1" --output "$2"
}

staging=$(mktemp -d)
trap 'rm -rf -- "$staging"' EXIT
download "https://downloads.gradle.org/distributions/gradle-${gradle_version}-bin.zip" "$staging/gradle.zip"
printf '%s  %s\n' "$gradle_sha" "$staging/gradle.zip" | sha256sum --check
unzip -q "$staging/gradle.zip" -d "$staging"

if [[ $project_dir == . ]]; then
  # Forge declares these exact local artifacts; use the publishers' versioned downloads.
  kff=kotlinforforge-4.12.0-all.jar
  ldlib=ldlib-forge-1.20.1-1.0.33.b.jar
  download "https://cdn.modrinth.com/data/ordsPcFz/versions/Zsh14XeQ/$kff" "$staging/$kff"
  download "https://cdn.modrinth.com/data/B1CBVXHX/versions/FX8CaaJ8/$ldlib" "$staging/$ldlib"
  printf '%s  %s\n' \
    767f40030ed4b370a3838fa65ae1b79974f31979342fff7cc834a0d5f39eb28612642519352ff2a1dd5226ce1f745f8b3156ee3625ffde01219137d064c1ee40 "$staging/$kff" \
    dc35720db23003c41d5df7c8365c5a2962e952b45da1349f3e3896864aa1b9c6b5871eb8d2e662583c5297ee1539c914f6530e845e9a015f6eaf97c67e4dd602 "$staging/$ldlib" | sha512sum --check
  mkdir -p libs
  cp -- "$staging/$kff" "$staging/$ldlib" libs/
fi

dependency_args=()
if [[ $project_dir == neoforge-1.21.1 ]]; then
  bash scripts/ci-fetch-neoforge-ldlib.sh "$staging/ldlib-1.21.1.jar"
  dependency_args+=("-PshbuiCiLdlibJar=$staging/ldlib-1.21.1.jar")
fi

toolchains=()
if [[ -n ${JAVA_HOME_17_X64:-} && -n ${JAVA_HOME_21_X64:-} ]]; then
  toolchains+=("-Porg.gradle.java.installations.paths=$JAVA_HOME_17_X64,$JAVA_HOME_21_X64")
fi
case "$mode:$project_dir" in
  build:*) tasks=(test kernelTest build verifyLibraryJar) ;;
  test:bridge) tasks=(--continue test) ;;
  test:*) tasks=(--continue test kernelTest) ;;
esac
"$staging/gradle-$gradle_version/bin/gradle" -p "$project_dir" \
  --no-daemon --console=plain --max-workers 3 "${toolchains[@]}" "${dependency_args[@]}" \
  "${tasks[@]}"
