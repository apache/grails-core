package org.grails.orm.hibernate.cfg

import grails.gorm.specs.HibernateGormDatastoreSpec

class GrailsDomainBinderSpec extends HibernateGormDatastoreSpec {


    void "Test single class"() {
        when:

        def collector = getCollector()
        def grailsDomainBinder = getGrailsDomainBinder()
        def simpleName = "Book"
        def fieldProperties = [title: String]
        def persistentEntity = createPersistentEntity(simpleName, fieldProperties, [:])
        grailsDomainBinder.bindRoot(persistentEntity, collector,"sessionFactoryName")
        println("when")
        then:
        1 == 1
    }


}