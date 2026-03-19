package grails.gorm.specs.hibernatequery

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.apache.grails.data.testing.tck.domains.Face
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.query.JpaFromProvider
import org.grails.orm.hibernate.query.PredicateGenerator
import org.hibernate.query.criteria.HibernateCriteriaBuilder
import org.springframework.core.convert.support.DefaultConversionService
import spock.lang.Unroll

/**
 * Combined Spec for PredicateGenerator validation.
 * Ensures compatibility with Hibernate 7 SQM strictness.
 */
class PredicateGeneratorSpec extends HibernateGormDatastoreSpec {

    PredicateGenerator predicateGenerator
    HibernateCriteriaBuilder cb
    CriteriaQuery<Person> query
    Root<Person> root
    JpaFromProvider fromProvider
    PersistentEntity personEntity

    def setup() {
        predicateGenerator = new PredicateGenerator(new DefaultConversionService())
        cb = sessionFactory.getCriteriaBuilder()
        query = cb.createQuery(Person)
        root = query.from(Person)
        personEntity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName)
        fromProvider = new JpaFromProvider(new DetachedCriteria(Person), query, root)
    }

    def setupSpec() {
        manager.addAllDomainClasses([Person, Pet, Face])
    }

    // --- Validation and Error Handling ---

    def "test getPredicates with non-existent property throws ConfigurationException"() {
        given:
        List criteria = [new Query.Equals("invalidProperty", "value")]

        when:
        predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        def e = thrown(ConfigurationException)
        e.message.contains("is not a valid property")
    }

    @Unroll
    def "test getPredicates with malformed finder property [#property] throws ConfigurationException"() {
        given:
        // This simulates the behavior of the TCK failures where suffixes like _LessThan 
        // are not stripped before reaching the PredicateGenerator
        List criteria = [new Query.LessThan(property, "Z")]

        when:
        predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        thrown(ConfigurationException)

        where:
        property << ["author_LessThan", "firstName_InList", "age_GreaterThan"]
    }

    def "test gt with String value that cant be coerced to Number"() {
        given:
        List criteria = [new Query.GreaterThan("age", "Bobby")]

        when:
        predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        thrown(ConfigurationException)
    }

    // --- Functional Query Tests ---

    def "test getPredicates with Equals criterion"() {
        given:
        List criteria = [new Query.Equals("firstName", "Bob")]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with IdEquals criterion"() {
        given:
        List criteria = [new Query.IdEquals(1L)]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Between criterion"() {
        given:
        List criteria = [new Query.Between("age", 18, 30)]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with In criterion"() {
        given:
        List criteria = [new Query.In("firstName", ["Bob", "Fred"])]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Conjunction"() {
        given:
        var conjunction = new Query.Conjunction()
        conjunction.add(new Query.Equals("firstName", "Bob"))
        conjunction.add(new Query.GreaterThan("age", 20))
        List criteria = [conjunction]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with Exists"() {
        given:
        List criteria = [new Query.Exists(new DetachedCriteria(Pet).eq("name", "Lucky"))]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then:
        predicates.length == 1
    }

    def "test getPredicates with subquery isolated provider"() {
        given: "a subquery with association reference"
        def subCriteria = new DetachedCriteria(Pet).eq("face.name", "Funny")
        List criteria = [new Query.In("id", subCriteria)]

        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)

        then: "no exception thrown during subquery join creation"
        noExceptionThrown()
        predicates.length == 1
    }
}