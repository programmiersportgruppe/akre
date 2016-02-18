#!/usr/bin/env bash

set -e
set -u
set -o pipefail


usage() {
    cat <<EOF
usage: $0 [options] [--major | --minor | --patch | [--] <version>]

    options:
        --allow-binary-incompatibility
        --ignore-unpushed-commits
        --ignore-upstream-commits

EOF
}

error() {
    echo "Error: $1"
    if [ $# -gt 1 ]; then
        shift
        printf '%s\n' "$@"
    fi
    exit 1
} >&2

usage-error() {
    error "$1" "$(usage)" "" "Use -? or --help for help, or -- to separate arguments from options"
}

change=explicit
fail_on_binary_incompatibility=true
ignore_unpushed_commits=false
ignore_upstream_commits=false

set-change() {
    [ "$1" != "${change}" ] || return
    [ "${change}" = explicit ] || usage-error "multiple version specifiers (got ${change} and now getting $*)"
    change="$1"
}

while (( $# > 0 )); do
    case "$1" in
        --allow-binary-incompatibility) fail_on_binary_incompatibility=false;;
        --ignore-unpushed-commits) ignore_unpushed_commits=true;;
        --ignore-upstream-commits) ignore_upstream_commits=true;;
        --major) set-change major;;
        --minor) set-change minor;;
        --patch) set-change patch;;
        -'?' | --help) usage; exit 0;;
        --) shift; break;;
        -*) usage-error "unknown option $1";;
        *) break;;
    esac
    shift
done

case $# in
    0) [ "${change}" != explicit ] || usage-error "missing version specifier";;
    1) set-change explicit "$1"; version="$1";;
    *) usage-error "too many arguments";;
esac


dirty=$(git status --porcelain)
[ -z "${dirty}" ] || error "cannot release because these files are dirty:" "${dirty}"

release_commit="$(git rev-parse HEAD)"
starting_point="$(git rev-parse --abbrev-ref --symbolic-full-name HEAD)"
echo "Building and releasing commit ${release_commit} (seen from starting point as ${starting_point})"


upstream="$(git rev-parse --abbrev-ref --symbolic-full-name '@{upstream}')"
remote_branch="${upstream#*/}"
[ "${remote_branch}" != "${upstream}" ] || error "upstream ${upstream} doesn't seem to be a remote branch"

remote="${upstream%/*}"
[ "${remote}/${remote_branch}" = "${upstream}" ] || error "remote ${remote} and branch ${remote_branch} do not add up to upstream ${upstream}"
echo "Found upstream branch ${remote_branch} on remote ${remote}"

echo "Fetching…"
git fetch

upstream_commit="$(git rev-parse "${upstream}")"
echo "Upstream ${upstream} is at ${upstream_commit}"

merge_base="$(git merge-base "${release_commit}" "${upstream_commit}")"
[ "${merge_base}" = "${release_commit}" ] || [ "${ignore_unpushed_commits}" = true ] || error "merge-base is ${merge_base}, which is not the same as the release commit. Please ensure the release commit has been pushed upstream to ${upstream}."
[ "${merge_base}" = "${upstream_commit}" ] || [ "${ignore_upstream_commits}" = true ] || error "merge-base is ${merge_base}, which is not the same as the current upstream commit. Please incorporate the upstream changes, or --ignore-upstream-commits."

previous_release_tag="$(git describe --tags --match="v*" --abbrev=0 2>/dev/null ||:)"
previous_release_version="${previous_release_tag#v}"
typeset -i major minor patch
major="${previous_release_version%%.*}"
patch="${previous_release_version##*.}"
minor="${previous_release_version:$((${#major}+1)):$((${#previous_release_version}-${#major}-${#patch}-2))}"
[ "v${major}.${minor}.${patch}" = "${previous_release_tag}" ] || error "previous release tag ${previous_release_tag} was reconstructed as v${major}.${minor}.${patch} after parsing"

if [ "${change}" = explicit ]; then
    error "explicit version releases aren't yet implemented; you might want to edit the release script to have it do the right thing"
else
    let "${change}++"
    version="${major}.${minor}.${patch}"
fi

tag="v${version}"
if [ -z "${previous_release_tag}" ]; then
    echo "This is the first release, and will be tagged as ${tag}"
else
    echo "The previous release was tagged as ${previous_release_tag}, and this one will be tagged as ${tag}"
fi


echo "Pruning target directories…"
find . -name target -prune -exec rm -r {} \;

echo "Building and publishing…"
sbt \
    "set previousVersion := Some(\"${previous_release_version}\")" \
    "set version in Global := \"${version}\"" \
    +mimaPreviousClassfiles \
    +test \
    "set failOnBinaryIncompatibility := ${fail_on_binary_incompatibility}" \
    +mimaReportBinaryIssues \
    +publishSigned \
    ;

dirty=$(git status --porcelain)
[ -z "${dirty}" ] || error "building and releasing made the repository dirty! Please fix this tragedy and then tag the release and update the readme." "${dirty}"



mkdir -p cache/draft-release-notes
release_notes_file="cache/draft-release-notes/${tag}-derived-from-${previous_release_tag}-to-${release_commit}.md"
if [ -e "${release_notes_file}" ]; then
    echo "Editing existing release notes in ${release_notes_file}"
else
    echo "Preparing release notes in ${release_notes_file}"
    {
        echo
        echo "Please replace this file's contents with release notes for ${tag}"
        echo
        echo
        if [ -n "${previous_release_tag}" ]; then
            echo "Changes since ${previous_release_tag}:"
        fi
        git log --reverse "${previous_release_tag}..${release_commit}"
        echo
        echo
        echo
        find . -type f \( -path '*/mimaFindBinaryIssues/*' -o -path '*/mimaReportBinaryIssues/*' \) -print0 | xargs -0 cat
    } >"${release_notes_file}"
fi
"${EDITOR-vim}" "${release_notes_file}"

release_notes="$(cat "${release_notes_file}")"



echo "Tagging as ${tag}"
git tag -f "${tag}" --annotate --message "${release_notes}"

echo "Pushing tag"
git push "${remote}" "${tag}"



echo "Fetching to update README.md on latest ${upstream}"
git fetch
branch="release-branch-${tag}"
git branch --force "${branch}" "${release_commit}"
git checkout "${branch}"
git branch --set-upstream-to="${upstream}"

echo "Updating current version in README.md to ${version}"
sed -i "" -E 's/(.*"org.programmiersportgruppe.akre" %% "[^"]*" % ")[^"]*(.*)/\1'"${version}"'\2/' README.md

git commit -m "Update current version in README.md to ${version}" README.md

git push "${remote}" "${branch}:${remote_branch}"

echo "Release ${tag} SUCCESSFULLY COMPLETED"

echo "Checking out starting point ${starting_point}"
git checkout "${starting_point}"

echo "Rebasing"
git rebase

echo "Deleting ${branch}"
git branch -d "${branch}"

echo "Removing all release notes drafts"
rm cache/draft-release-notes/*.md
rmdir cache/draft-release-notes
