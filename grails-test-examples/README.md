# Functional tests for Grails Core

A Suite of functional tests for Grails.  

The following properties exist to not run or only run tests in these directories: 
- `onlyFunctionalTests` - Only run tests in this directory
- `skipFunctionalTests` - Skip tests in this directory

For example, at the root directory to only run the functional tests:

    ./gradlew -PonlyFunctionalTests build

Or to skip the functional tests:

    ./gradlew -PskipFunctionalTests build