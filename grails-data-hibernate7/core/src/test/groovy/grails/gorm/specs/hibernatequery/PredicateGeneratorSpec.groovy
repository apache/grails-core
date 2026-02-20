package grails.gorm.specs.hibernatequery

import grails.gorm.DetachedCriteria
import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import org.apache.grails.data.testing.tck.domains.Person
import org.apache.grails.data.testing.tck.domains.Pet
import org.apache.grails.data.testing.tck.domains.Face
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.query.JpaFromProvider
import org.grails.orm.hibernate.query.PredicateGenerator
import org.hibernate.query.criteria.HibernateCriteriaBuilder

class PredicateGeneratorSpec extends HibernateGormDatastoreSpec {

    PredicateGenerator predicateGenerator
    HibernateCriteriaBuilder cb
    CriteriaQuery<Person> query
    Root<Person> root
    JpaFromProvider fromProvider
    PersistentEntity personEntity

    def setup() {
        predicateGenerator = new PredicateGenerator()
        cb = sessionFactory.getCriteriaBuilder()
        query = cb.createQuery(Person)
        root = query.from(Person)
        personEntity = manager.hibernateDatastore.getMappingContext().getPersistentEntity(Person.typeName)
        fromProvider = new JpaFromProvider(new DetachedCriteria(Person), query, root)
    }

    def setupSpec() {
        manager.addAllDomainClasses([Person, Pet, Face])
    }

    def "test getPredicates with Equals criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.Equals("firstName", "Bob")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with NotEquals criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.NotEquals("firstName", "Bob")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with IdEquals criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.IdEquals(1L)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with GreaterThan criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.GreaterThan("age", 20)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with GreaterThanEquals criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.GreaterThanEquals("age", 20)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThan criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.LessThan("age", 20)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThanEquals criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.LessThanEquals("age", 20)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with SizeEquals criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.SizeEquals("pets", 1)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with SizeNotEquals criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.SizeNotEquals("pets", 1)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with SizeGreaterThan criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.SizeGreaterThan("pets", 1)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with SizeGreaterThanEquals criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.SizeGreaterThanEquals("pets", 1)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with SizeLessThan criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.SizeLessThan("pets", 1)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with SizeLessThanEquals criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.SizeLessThanEquals("pets", 1)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with Between criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.Between("age", 18, 30)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with ILike criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.ILike("firstName", "B%")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with RLike criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.RLike("firstName", "B.*")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with Like criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.Like("firstName", "B%")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with In criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.In("firstName", ["Bob", "Fred"])]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with NotIn criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.NotIn("firstName", new DetachedCriteria(Person).eq("firstName", "Bob").property("firstName"))]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with IsNull criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.IsNull("firstName")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with IsNotNull criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.IsNotNull("firstName")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with IsEmpty criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.IsEmpty("pets")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with IsNotEmpty criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.IsNotEmpty("pets")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with EqualsProperty criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.EqualsProperty("firstName", "lastName")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with NotEqualsProperty criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.NotEqualsProperty("firstName", "lastName")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThanEqualsProperty criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.LessThanEqualsProperty("age", "age")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThanProperty criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.LessThanProperty("age", "age")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with GreaterThanEqualsProperty criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.GreaterThanEqualsProperty("age", "age")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with GreaterThanProperty criterion"() {
        given:
        List<Query.Criterion> criteria = [new Query.GreaterThanProperty("age", "age")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with DistinctProjection criterion"() {
        given:
        def distinct = new Query.DistinctProjection()
        def criteriaList = [distinct]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteriaList, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with Conjunction"() {
        given:
        var conjunction = new Query.Conjunction()
        conjunction.add(new Query.Equals("firstName", "Bob"))
        conjunction.add(new Query.GreaterThan("age", 20))
        List<Query.Criterion> criteria = [conjunction]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with Disjunction"() {
        given:
        var disjunction = new Query.Disjunction()
        disjunction.add(new Query.Equals("firstName", "Bob"))
        disjunction.add(new Query.Equals("firstName", "Fred"))
        List<Query.Criterion> criteria = [disjunction]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with Negation"() {
        given:
        var negation = new Query.Negation()
        negation.add(new Query.Equals("firstName", "Bob"))
        List<Query.Criterion> criteria = [negation]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with DetachedAssociationCriteria"() {
        given:
        var association = personEntity.getPropertyByName("pets")
        var associationCriteria = new org.grails.datastore.gorm.query.criteria.DetachedAssociationCriteria(Pet, association, "pets")
        associationCriteria.eq("name", "Lucky")
        List criteria = [associationCriteria]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, (List<Query.Criterion>)criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    private jakarta.persistence.criteria.Predicate[] callGetPredicatesUntyped(List criteria) {
        return predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
    }
}
