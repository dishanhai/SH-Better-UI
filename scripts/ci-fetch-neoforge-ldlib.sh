#!/usr/bin/env bash
set -euo pipefail

destination="${1:?Specify the staging JAR path}"
version=mc1.21.1-2.2.44-neoforge
primary="https://api.modrinth.com/maven/maven/modrinth/ldlib/$version/ldlib-$version.jar"
modrinth_cdn='https://cdn.modrinth.com/data/B1CBVXHX/versions/s7Uru4V0/ldlib2-neoforge-1.21.1-2.2.44-all.jar'
# CurseForge release 9304898: the publisher's matching all JAR, not an additional file.
curseforge_cdn='https://mediafilez.forgecdn.net/files/9304/898/ldlib2-neoforge-1.21.1-2.2.44-all.jar'
checksum=232e7e5ffcadbcd660894c055d7f53443c7b9105658229875b936a8c5eb8587f3ed9ef51b2897084709d4cd2873172ee4e34101e1e8c88aade1d837c63c67883

for source in "$primary" "$modrinth_cdn" "$curseforge_cdn"; do
  if curl --fail --silent --show-error --location --retry 1 --connect-timeout 10 --max-time 35 \
      "$source" --output "$destination" &&
      printf '%s  %s\n' "$checksum" "$destination" | sha512sum --check; then
    printf 'Verified LDLib %s from %s\n' "$version" "$source"
    exit 0
  fi
  printf 'Could not verify LDLib from %s\n' "$source" >&2
done

printf 'No verified LDLib download is available.\n' >&2
exit 1
