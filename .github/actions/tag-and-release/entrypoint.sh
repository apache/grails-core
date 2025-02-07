#!/bin/bash
# $1 == Target Branch
# $2 == Grails Version

target_branch="$1"
grails_version="$2"
echo -n "Updating Grails version to: $grails_version"

if [ -z "$GIT_USER_EMAIL" ]; then
    GIT_USER_EMAIL="${GITHUB_ACTOR}@users.noreply.github.com"
fi

if [ -z "$GIT_USER_NAME" ]; then
   GIT_USER_NAME="${GITHUB_ACTOR}"
fi

echo "Configuring git"
git config --global user.email "$GIT_USER_EMAIL"
git config --global user.name "$GIT_USER_NAME"
git config --global --add safe.directory /github/workspace

git checkout $target_branch
git pull origin $target_branch

echo "Setting release version in gradle.properties"
sed -i "s/^grails\.version.*$/grails\.version\=${grails_version}/" gradle.properties
cat gradle.properties

echo "Pushing release version and recreating v${grails_version} tag"
git add gradle.properties
git commit -m "[skip ci] Release v${grails_version} docs"
git push origin $target_branch
git push origin :refs/tags/v${grails_version}
git tag -fa v${grails_version} -m "[skip ci] Release v${grails_version} docs"
git push origin $target_branch --tags
