package grails.test.app.manyToMany

import grails.test.hibernate.HibernateSpec
import org.grails.gorm.graphql.Schema
import org.junit.Test


class StudentSchemaSpec extends HibernateSpec {

    @Override
    List<Class> getDomainClasses() {
        [Classes, Student]
    }

    void "test schema generation for many-to-many relationship with on side disabled for all operations"() {

        when:
        new Schema(hibernateDatastore.mappingContext)
                .generate()

        then:
        noExceptionThrown()
    }
}
