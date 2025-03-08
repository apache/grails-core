package grails.gorm.tests.compositeid

import grails.gorm.annotation.Entity
import grails.gorm.hibernate.mapping.MappingBuilder
import grails.gorm.transactions.Rollback
import org.grails.orm.hibernate.HibernateDatastore
import org.springframework.transaction.PlatformTransactionManager
import spock.lang.AutoCleanup
import spock.lang.Issue
import spock.lang.Shared
import spock.lang.Specification

/**
 * Created by graemerocher on 26/01/2017.
 */
class CompositeIdWithDeepOneToManyMappingSpec extends Specification {

    @AutoCleanup @Shared HibernateDatastore datastore = new HibernateDatastore(GrandParent, Parent, Child)
    @Shared PlatformTransactionManager transactionManager = datastore.transactionManager

    @Rollback
    @Issue('https://github.com/grails/grails-data-mapping/issues/660')
    void 'test composite id with nested one-to-many mappings'() {
        when:
        def grandParent = new GrandParent(luckyNumber: 7, name: "Fred")
        def parent = new Parent(name: "Bob")
        grandParent.addToParents(parent)
        parent.addToChildren(name:"Chuck")
        grandParent.save(flush:true)

        then:
        Parent.count == 1
        GrandParent.count == 1
        Child.count == 1
        GrandParent.list().first().parents.first().children.first().parent != null
    }
}

@Entity
class Child implements Serializable {
    String name

    static belongsTo= [parent: Parent]

    static mapping = MappingBuilder.define {
        composite('parent', 'name')
    }
}

@Entity
class Parent implements Serializable {
    String name
    Collection<Child> children

    static belongsTo= [grandParent: GrandParent]
    static hasMany= [children: Child]

    static mapping= MappingBuilder.define {
        composite('grandParent', 'name')
    }
}

@Entity
class GrandParent implements Serializable {
    String name
    Integer luckyNumber
    Collection<Parent> parents

    static hasMany= [parents: Parent]

    static mapping= MappingBuilder.define {
        composite('name', 'luckyNumber')
    }
}