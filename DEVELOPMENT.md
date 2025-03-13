# Various properties that control which tests run
* onlyHibernate5Tests 
* onlyMongodbTests
* onlyDatastoreTests
* skipHibernate5Tests
* skipMongodbTests
* skipDatastoreTests

# Start a mongo docker container
docker run -d  --name mongo-on-docker  -p 27017:27017 mongo