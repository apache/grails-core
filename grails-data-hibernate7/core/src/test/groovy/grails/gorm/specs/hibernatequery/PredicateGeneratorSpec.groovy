package grails.gorm.specs.hibernatequery

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import grails.orm.HibernateCriteriaBuilder
import org.grails.orm.hibernate.query.JpaFromProvider
import org.grails.orm.hibernate.query.PredicateGenerator
import spock.lang.Shared
import grails.gorm.annotation.Entity
import org.grails.datastore.gorm.GormEntity

class PredicateGeneratorSpec extends HibernateGormDatastoreSpec {

    PredicateGenerator predicateGenerator = new PredicateGenerator()
    HibernateCriteriaBuilder cb
    CriteriaQuery query
    Root root
    JpaFromProvider fromProvider
    PersistentEntity personEntity

    void setupSpec() {
        manager.addAllDomainClasses([PredicateGeneratorSpecPerson, PredicateGeneratorSpecPet, PredicateGeneratorSpecFace])
    }

    void setup() {
        cb = sessionFactory.getCriteriaBuilder()
        query = cb.createQuery(PredicateGeneratorSpecPerson)
        root = query.from(PredicateGeneratorSpecPerson)
        personEntity = session.datastore.mappingContext.getPersistentEntity(PredicateGeneratorSpecPerson.name)
        fromProvider = new JpaFromProvider(new DetachedCriteria(PredicateGeneratorSpecPerson), query, root)
    }

    def "test getPredicates with Equals criterion"() {
        given:
        List criteria = [new Query.Equals("firstName", "Bob")]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Between criterion"() {
        given:
        List criteria = [new Query.Between("age", 20, 30)]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with In criterion"() {
        given:
        List criteria = [new Query.In("firstName", ["Bob", "Alice"])]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Conjunction"() {
        given:
        List criteria = [new Query.Conjunction()
                                 .add(new Query.Equals("firstName", "Bob"))
                                 .add(new Query.Equals("lastName", "Smith"))]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Exists"() {
        given:
        List criteria = [new Query.Exists(new DetachedCriteria(PredicateGeneratorSpecPet).eq("name", "Lucky"))]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with subquery isolated provider"() {
        given: "a subquery with association reference"
        def subCriteria = new DetachedCriteria(PredicateGeneratorSpecPet).eq("face.name", "Funny")
        List criteria = [new Query.In("id", subCriteria)]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then: "no exception thrown during subquery join creation"
        noExceptionThrown()
        predicates.length == 1
    }
}

@Entity
class PredicateGeneratorSpecPerson implements GormEntity<PredicateGeneratorSpecPerson> {
    Long id
    String firstName
    String lastName
    Integer age
    PredicateGeneratorSpecFace face
}

@Entity
class PredicateGeneratorSpecPet implements GormEntity<PredicateGeneratorSpecPet> {
    Long id
    String name
    PredicateGeneratorSpecFace face
    static belongsTo = [owner: PredicateGeneratorSpecPerson]
}

@Entity
class PredicateGeneratorSpecFace implements GormEntity<PredicateGeneratorSpecFace> {
    Long id
    String name
}
