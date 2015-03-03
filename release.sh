#!/usr/bin/env bash

set -e
set -u
set -o pipefail


error() {
    echo "Error: $1"
    if [ $# -gt 1 ]; then
        shift
        printf '%s\n' "$@"
    fi
    exit 1
} >&2



[ -n "${1-}" ] || error "missing parameter version"

version="$1"
tag="v${version}"



dirty=$(git status --porcelain)
[ -z "${dirty}" ] || error "cannot release because these files are dirty:" "${dirty}"


starting_point="$(git rev-parse --abbrev-ref --symbolic-full-name HEAD)"

upstream="$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}')"
remote_branch="${upstream#*/}"
[ "${remote_branch}" != "${upstream}" ] || error "upstream ${upstream} doesn't seem to be a remote branch"

remote="${upstream%/*}"
[ "${remote}/${remote_branch}" = "${upstream}" ] || error "remote ${remote} and branch ${remote_branch} do not add up to upstream ${upstream}"
echo "Found upstream branch ${remote_branch} on remote ${remote}"

echo "Fetching…"
git fetch

release_commit="$(git rev-parse HEAD)"
echo "Building and releasing commit ${release_commit} as ${tag}"

upstream_commit="$(git rev-parse "${upstream}")"
echo "Upstream ${upstream} is at ${upstream_commit}"

merge_base="$(git merge-base "${release_commit}" "${upstream_commit}")"
[ "${merge_base}" = "${release_commit}" ] || error "merge-base is ${merge_base}, which is not the same as the release commit. Please ensure the release commit has been pushed upstream to ${upstream}."

previous_release_tag="$(git describe --tags --match="v*" --abbrev=0 2>/dev/null ||:)"
if [ -z "${previous_release_tag}" ]; then
    echo "This is the first release, and will be tagged as ${tag}"
else
    echo "The previous release was tagged as ${previous_release_tag}, and this one will be tagged as ${tag}"
fi



echo "Pruning target directories…"
find . -name target -prune -exec rm -r {} \;

echo "Building and publishing…"
sbt "set version in ThisBuild := \"${version}\"" +test +publishSigned

dirty=$(git status --porcelain)
[ -z "${dirty}" ] || error "building and releasing made the repository dirty! Please fix this tragedy and then tag the release and update the readme." "${dirty}"



release_notes_file="$(mktemp -t release-notes-v1.0.0-candidate.txt)"
{
    echo "Please replace this file's contents with release notes for ${tag}"
    echo
    if [ -n "${previous_release_tag}" ]; then
        echo "Changes since ${previous_release_tag}:"
    fi
    git log "${previous_release_tag}..HEAD"
} >"${release_notes_file}"
"${EDITOR-vim}" "${release_notes_file}"

release_notes="$(cat "${release_notes_file}")"
rm "${release_notes_file}"

git tag -f "${tag}" --annotate --message "${release_notes}"

git push "${remote}" "${tag}"



echo "Fetching to update README.md on latest ${upstream}"
git fetch
branch="release-branch-${tag}"
git branch --force --track "${branch}" "${upstream}"
git checkout "${branch}"

echo "Updating current version in README.md to ${version}"
sed -i "" -E 's/(.*"org.programmiersportgruppe.akre" %% "[^"]*" % ")[^"]*(.*)/\1'"${version}"'\2/' README.md

git commit -m "Update current version in README.md to ${version}"

git push "${remote}" "${branch}:${remote_branch}"

echo "Release ${tag} SUCCESSFULLY COMPLETED"

echo "Checking out starting point ${starting_point}"
git checkout "${starting_point}"

echo "Rebasing"
git rebase

echo "Deleting ${branch}"
git branch -d "${branch}"
