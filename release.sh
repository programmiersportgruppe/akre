#!/usr/bin/env bash

set -e
set -u
set -o pipefail


if [[ -z "${1-}" ]]; then
    echo "Missing parameter version">&2
    exit 1
fi

version="$1"

dirty=$(git status --porcelain)
if [ "${dirty}" ]; then
    echo "Cannot release because these files are dirty:"
    echo "${dirty}"
    exit 1
fi >&2

find . -name target -prune -exec rm -r {} \;

sbt "set version in ThisBuild := \"${version}\"" +test +publishSigned

tag="v${version}"


sed -i "" -E 's/(.*"org.programmiersportgruppe.akre" %% "[^"]*" % ")[^"]*(.*)/\1'"${version}"'\2/' README.md

git commit -a -m "Releasing ${version}."

git tag -f "${tag}"

git push origin "${tag},master"
