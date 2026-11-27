#!/bin/bash
set -e

EXIT_STATUS=0

cd profile

touch settings.gradle

echo "Test: create-command"

./grailsw create-command cmd || EXIT_STATUS=$?

if [ $EXIT_STATUS -ne 0 ]; then
  exit $EXIT_STATUS
fi

echo "Test: create-creator-command"

./grailsw create-creator-command creatorcmd src || EXIT_STATUS=$?

if [ $EXIT_STATUS -ne 0 ]; then
  exit $EXIT_STATUS
fi

echo "Test: create-feature"

./grailsw create-feature foofeature || EXIT_STATUS=$?

if [ $EXIT_STATUS -ne 0 ]; then
  exit $EXIT_STATUS
fi

echo "Test: create-generator-command"

./grailsw create-generator-command generatorcmd src || EXIT_STATUS=$?

if [ $EXIT_STATUS -ne 0 ]; then
  exit $EXIT_STATUS
fi

echo "Test: create-gradle-command"

./grailsw create-gradle-command gradlecmd gradlecmd || EXIT_STATUS=$?

if [ $EXIT_STATUS -ne 0 ]; then
  exit $EXIT_STATUS
fi

echo "Test: create-template"

./grailsw create-template tpl || EXIT_STATUS=$?

if [ $EXIT_STATUS -ne 0 ]; then
  exit $EXIT_STATUS
fi

cd ..

rm -rf profile

exit $EXIT_STATUS