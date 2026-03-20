package grails.gorm.specs.hibernatequery

import org.hibernate.query.criteria.HibernateCriteriaBuilder

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query

import org.grails.orm.hibernate.query.JpaFromProvider
import org.grails.orm.hibernate.query.PredicateGenerator
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
        fromProvider = new JpaFromProvider(new DetachedCriteria(PredicateGeneratorSpecPerson),[], root)
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

    def "test getPredicates with Disjunction"() {
        given:
        List criteria = [new Query.Disjunction()
                                 .add(new Query.Equals("firstName", "Bob"))
                                 .add(new Query.Equals("firstName", "Alice"))]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Negation"() {
        given:
        List criteria = [new Query.Negation().add(new Query.Equals("firstName", "Bob"))]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Property Comparison"() {
        given:
        List criteria = [new Query.EqualsProperty("firstName", "lastName")]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Like and ILike"() {
        given:
        List criteria = [
            new Query.Like("firstName", "B%"),
            new Query.ILike("firstName", "b%")
        ]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 2
    }

    def "test getPredicates with Size Comparison"() {
        given:
        List criteria = [new Query.SizeEquals("pets", 2)]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with In on basic collection"() {
        given:
        List criteria = [new Query.In("nicknames", ["Bob", "Alice"])]
        
        // Ensure nicknames is joined in fromProvider
        fromProvider = new JpaFromProvider(new DetachedCriteria(PredicateGeneratorSpecPerson), [], root)

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
        predicates[0] instanceof org.hibernate.query.sqm.tree.predicate.SqmInListPredicate
    }
}

@Entity
class PredicateGeneratorSpecPerson implements GormEntity<PredicateGeneratorSpecPerson> {
    Long id
    String firstName
    String lastName
    Integer age
    PredicateGeneratorSpecFace face
    Set<String> nicknames
    static hasMany = [pets: PredicateGeneratorSpecPet, nicknames: String]
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
