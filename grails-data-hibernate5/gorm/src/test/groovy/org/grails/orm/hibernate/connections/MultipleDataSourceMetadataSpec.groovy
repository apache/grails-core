package org.grails.orm.hibernate.connections

import grails.gorm.annotation.Entity
import org.grails.datastore.mapping.core.DatastoreUtils
import org.grails.orm.hibernate.HibernateDatastore
import org.hibernate.boot.Metadata
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class MultipleDataSourceMetadataSpec extends Specification {

    @Shared
    Map config = [
            "dataSources.apples.url": "jdbc:h2:mem:apples;LOCK_TIMEOUT=10000",
            "dataSources.oranges.url": "jdbc:h2:mem:oranges;LOCK_TIMEOUT=10000"
    ]

    @AutoCleanup
    @Shared
    HibernateDatastore datastore = new HibernateDatastore(DatastoreUtils.createPropertyResolver(config), Apple, Orange)

    void "test metadata retrieval for multiple dataSources"() {
        when: "the metadata for the default dataSource is retrieved"
        Metadata metadataDefault = datastore.metadata

        then: "the metadata is set and does not contain entityBindings or tableMappings"
        metadataDefault.entityBindings.size() == 0
        metadataDefault.collectTableMappings().size() == 0

        when: "the metadata for the apples dataSource is retrieved"
        Metadata metadataApples = datastore.getDatastoreForConnection("apples").metadata

        then: "the metadata is set and does contain the correct entityBinding and tableMapping"
        metadataApples.entityBindings.size() == 1
        metadataApples.entityBindings.first().getMappedClass() == Apple
        metadataApples.collectTableMappings().size() == 1
        metadataApples.collectTableMappings().first().name == "apple"

        when: "the metadata for the oranges dataSource is retrieved"
        Metadata metadataOranges = datastore.getDatastoreForConnection("oranges").metadata

        then: "the metadata is set and does contain the correct entityBinding and tableMapping"
        metadataOranges.entityBindings.size() == 1
        metadataOranges.entityBindings.first().getMappedClass() == Orange
        metadataOranges.collectTableMappings().size() == 1
        metadataOranges.collectTableMappings().first().name == "orange"
    }
}

@Entity
class Apple {

    String name

    static mapping = {
        datasource "apples"
    }

}

@Entity
class Orange {

    Integer age

    static mapping = {
        datasource "oranges"
    }

}


