package grails.gorm.tests

import org.grails.datastore.gorm.query.transform.ApplyDetachedCriteriaTransform
import org.grails.datastore.mapping.reflect.FieldEntityAccess

@ApplyDetachedCriteriaTransform
class WhereMethodEmbeddedInAssociationSpec extends GormDatastoreSpec {

    def gcl


    @Override
    List getDomainClasses() {
        def list = super.getDomainClasses()

        gcl = new GroovyClassLoader()

        gcl.parseClass('''
import grails.gorm.tck.*
import grails.gorm.annotation.*
import grails.persistence.*
import grails.gorm.DetachedCriteria

@Entity
class Address {
    String country
    String city
}

@Entity
class Contact {
    Address address

    static embedded = ['address']
    

}

@Entity
class Partner {
    Contact contact

    static DetachedCriteria<Partner> cityNameILike(String str) {
        where {
            contact.address.city =~ "$str"
        }
    }
}
''')

        def Partner = this.gcl.loadClass("Partner")
        def Contact = this.gcl.loadClass("Contact")
        def Address = this.gcl.loadClass("Address")

        list << Partner
        list << Contact
        list << Address

        return list

    }

    def setup() {
        FieldEntityAccess.clearReflectors()
    }

    def "Test error when using embedded domain property of an association"() {
        when: "A an unknown domain class property of an association is referenced"
        def Partner = this.gcl.loadClass("Partner")
        def criteria = Partner.cityNameILike("Paris")

        then:
        criteria.list() == []
    }
}
