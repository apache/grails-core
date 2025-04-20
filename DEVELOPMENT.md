# Various properties that control which & how tests run
* onlyFunctionalTests - runs only grails-test-examples/* tests
* onlyHibernate5Tests - runs only a hibernate5 related test
* onlyMongodbTests - runs only a mongodb related test
* onlyCoreTests - runs tests that do not include mongo, hibernate, or functional
* skipFunctionalTests - does not run the functional tests
* skipHibernate5Tests - does not run hibernate5 related tests
* skipMongodbTests - does not run mongo related tests
* skipCoreTests - does not run the "core" tests
* serializeMongoTests - if true, only integration tests from one mongo project will run at a time
* skipTests - no tests will run

# Start a mongo docker container (containers will start by default)
`docker run -d  --name mongo-on-docker  -p 27017:27017 mongo`