#!/bin/bash
set -euo pipefail

commit="$(git rev-parse HEAD)"
version="$(sed -n 's/^version=//p' runelite-plugin.properties)"

cd "../plugin-hub"

if [[ -n "$(git status --porcelain)" ]]; then
    echo "The plugin-hub working tree must be clean." >&2
    exit 1
fi

git checkout master
git pull --ff-only upstream master
git checkout -B harbourmaster

sed -i '' "s/^commit=.*/commit=$commit/" plugins/harbourmaster
git add plugins/harbourmaster
git commit -m "update harbourmaster to version $version"
git push --force-with-lease --set-upstream origin harbourmaster
git open
