#!/bin/bash
# $1 == GH_TOKEN

if [ -z "$SNAPSHOT_SUFFIX" ]; then
  SNAPSHOT_SUFFIX="-SNAPSHOT"
fi

if [ -z "$GIT_USER_EMAIL" ]; then
   GIT_USER_EMAIL="${GITHUB_ACTOR}@users.noreply.github.com"
fi

if [ -z "$GIT_USER_NAME" ]; then
   GIT_USER_NAME="grails-build"
fi

echo -n "Determining release version: "
if [ -z "$RELEASE_VERSION" ]; then
  release_version=${GITHUB_REF:11}
else
  release_version=${RELEASE_VERSION}
fi
echo $release_version

echo -n "Determining next version: "
next_version=`/increment_version.sh -p $release_version`
echo $next_version
echo "next_version=${next_version}" >> $GITHUB_OUTPUT

echo "Configuring git"
git config --global user.email "$GIT_USER_EMAIL"
git config --global user.name "$GIT_USER_NAME"
git config --global --add safe.directory /github/workspace
git fetch

echo -n "Determining target branch: "
if [ -z "$TARGET_BRANCH" ]; then
  target_branch=`cat $GITHUB_EVENT_PATH | jq '.release.target_commitish' | sed -e 's/^"\(.*\)"$/\1/g'`
else
  target_branch=${TARGET_BRANCH}
fi
echo $target_branch
git checkout $target_branch

echo -n "Retrieving current milestone number: "
milestone_number=`curl -s https://api.github.com/repos/$GITHUB_REPOSITORY/milestones | jq -c ".[] | select (.title == \"$release_version\") | .number" | sed -e 's/"//g'`
echo $milestone_number

echo "Closing current milestone"
curl -s --request PATCH -H "Authorization: Bearer $1" -H "Content-Type: application/json" https://api.github.com/repos/$GITHUB_REPOSITORY/milestones/$milestone_number --data '{"state":"closed"}'

echo "Getting issues closed"
issues_closed=`curl -s "https://api.github.com/repos/$GITHUB_REPOSITORY/issues?milestone=$milestone_number&state=closed" | jq '.[] | "* \(.title) (#\(.number))"' | sed -e 's/^"\(.*\)"$/\1/g'`
echo $issues_closed

echo -n "Getting release url: "
release_url=`cat $GITHUB_EVENT_PATH | jq '.release.url' | sed -e 's/^"\(.*\)"$/\1/g'`
echo $release_url

echo -n "Getting release body: "
release_body=`cat $GITHUB_EVENT_PATH | jq '.release.body' | sed -e 's/^"\(.*\)"$/\1/g'`
echo $release_body

echo -n "Updating release body: "
release_body="${release_body}\r\n${issues_closed}"
echo $release_body
curl -i --request PATCH -H "Authorization: Bearer $1" -H "Content-Type: application/json" $release_url --data "{\"body\": \"$release_body\"}"

echo "Creating new milestone"
curl -s --request POST -H "Authorization: Bearer $1" -H "Content-Type: application/json" "https://api.github.com/repos/$GITHUB_REPOSITORY/milestones" --data "{\"title\": \"$next_version\"}"

echo "Setting new snapshot version"
sed -i "s/^projectVersion.*$/projectVersion\=${next_version}$SNAPSHOT_SUFFIX/" gradle.properties
sed -i "s/assertEquals(\".*$/assertEquals(\"${next_version}$SNAPSHOT_SUFFIX\", GrailsUtil.getGrailsVersion());/" grails-core/src/test/groovy/grails/util/GrailsUtilTests.java
sed -n "/assertEquals(\".*/p" grails-core/src/test/groovy/grails/util/GrailsUtilTests.java
cat gradle.properties

echo "Committing and pushing"
git add gradle.properties
git add grails-core/src/test/groovy/grails/util/GrailsUtilTests.java
git commit -m "Back to ${next_version}$SNAPSHOT_SUFFIX"
git push origin $target_branch

echo "Setting release version back so that Maven Central sync can work"
sed -i "s/^projectVersion.*$/projectVersion\=${release_version}/" gradle.properties
cat gradle.properties
