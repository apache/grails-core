package org.grails.orm.hibernate.cfg

import grails.gorm.specs.HibernateGormDatastoreSpec

class GrailsDomainBinderSpec extends HibernateGormDatastoreSpec {


    void "Test single class"() {
        when:

        def collector = getCollector()
        def grailsDomainBinder = getGrailsDomainBinder()
        def simpleName = "Book"
        def fieldProperties = [title: String]
        def persistentEntity = createPersistentEntity(grailsDomainBinder,simpleName, fieldProperties, [:])

                grailsDomainBinder.bindRoot(persistentEntity, collector,"sessionFactoryName")

                println("when")

                then:

                1 == 1

            }

        

            void "Test isSaveUpdateCascade"() {

                given:

                def binder = getGrailsDomainBinder()

        

                expect:

                binder.isSaveUpdateCascade(cascade) == expected

        

                where:

                cascade              | expected

                "all"                | true

                "all-delete-orphan"  | true

                "persist,merge"      | true

                "save-update"        | true

                "merge,persist"      | true

                "merge"              | false

                "persist"            | false

                "none"               | false

                "delete"             | false

                "lock"               | false

                "evict"              | false

                "replicate"          | false

                "all,delete"         | true

                "persist,merge,lock" | true

            }

        }