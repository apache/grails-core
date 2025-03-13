import jakarta.servlet.ServletContext
import org.grails.datastore.mapping.mongo.MongoDatastore

class BootStrap {

    ServletContext servletContext
    MongoDatastore mongoDatastore

    def init = {
        mongoDatastore.mongoClient.getDatabase(mongoDatastore.defaultDatabase).drop()
    }

    def destroy = {
    }
}
