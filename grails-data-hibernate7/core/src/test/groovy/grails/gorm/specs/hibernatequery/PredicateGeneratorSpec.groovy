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
        List criteria = [new Query.Equals("firstName", "Bob")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with NotEquals criterion"() {
        given:
        List criteria = [new Query.NotEquals("firstName", "Bob")]
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

    def "test getPredicates with GreaterThan criterion"() {
        given:
        List criteria = [new Query.GreaterThan("age", 20)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with GreaterThanEquals criterion"() {
        given:
        List criteria = [new Query.GreaterThanEquals("age", 20)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThan criterion"() {
        given:
        List criteria = [new Query.LessThan("age", 20)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThanEquals criterion"() {
        given:
        List criteria = [new Query.LessThanEquals("age", 20)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with SizeEquals criterion"() {
        given:
        List criteria = [new Query.SizeEquals("pets", 1)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with SizeNotEquals criterion"() {
        given:
        List criteria = [new Query.SizeNotEquals("pets", 1)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with SizeGreaterThan criterion"() {
        given:
        List criteria = [new Query.SizeGreaterThan("pets", 1)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with SizeGreaterThanEquals criterion"() {
        given:
        List criteria = [new Query.SizeGreaterThanEquals("pets", 1)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with SizeLessThan criterion"() {
        given:
        List criteria = [new Query.SizeLessThan("pets", 1)]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with SizeLessThanEquals criterion"() {
        given:
        List criteria = [new Query.SizeLessThanEquals("pets", 1)]
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

    def "test getPredicates with ILike criterion"() {
        given:
        List criteria = [new Query.ILike("firstName", "B%")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with RLike criterion"() {
        given:
        List criteria = [new Query.RLike("firstName", "B.*")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with Like criterion"() {
        given:
        List criteria = [new Query.Like("firstName", "B%")]
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

    def "test getPredicates with NotIn criterion"() {
        given:
        List criteria = [new Query.NotIn("firstName", new DetachedCriteria(Person).eq("firstName", "Bob").property("firstName"))]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with IsNull criterion"() {
        given:
        List criteria = [new Query.IsNull("firstName")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with IsNotNull criterion"() {
        given:
        List criteria = [new Query.IsNotNull("firstName")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with IsEmpty criterion"() {
        given:
        List criteria = [new Query.IsEmpty("pets")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with IsNotEmpty criterion"() {
        given:
        List criteria = [new Query.IsNotEmpty("pets")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with EqualsProperty criterion"() {
        given:
        List criteria = [new Query.EqualsProperty("firstName", "lastName")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with NotEqualsProperty criterion"() {
        given:
        List criteria = [new Query.NotEqualsProperty("firstName", "lastName")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThanEqualsProperty criterion"() {
        given:
        List criteria = [new Query.LessThanEqualsProperty("age", "age")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThanProperty criterion"() {
        given:
        List criteria = [new Query.LessThanProperty("age", "age")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with GreaterThanEqualsProperty criterion"() {
        given:
        List criteria = [new Query.GreaterThanEqualsProperty("age", "age")]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with GreaterThanProperty criterion"() {
        given:
        List criteria = [new Query.GreaterThanProperty("age", "age")]
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
        List criteria = [conjunction]
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
        List criteria = [disjunction]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with Negation"() {
        given:
        var negation = new Query.Negation()
        negation.add(new Query.Equals("firstName", "Bob"))
        List criteria = [negation]
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
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with In criterion and subquery"() {
        given:
        List criteria = [new Query.In("firstName", new DetachedCriteria(Person).eq("lastName", "Builder").property("firstName"))]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with GreaterThanEqualsAll"() {
        given:
        List criteria = [new Query.GreaterThanEqualsAll("age", new DetachedCriteria(Person).eq("firstName", "Bob").property("age"))]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with GreaterThanAll"() {
        given:
        List criteria = [new Query.GreaterThanAll("age", new DetachedCriteria(Person).eq("firstName", "Bob").property("age"))]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThanEqualsAll"() {
        given:
        List criteria = [new Query.LessThanEqualsAll("age", new DetachedCriteria(Person).eq("firstName", "Bob").property("age"))]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThanAll"() {
        given:
        List criteria = [new Query.LessThanAll("age", new DetachedCriteria(Person).eq("firstName", "Bob").property("age"))]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with EqualsAll"() {
        given:
        List criteria = [new Query.EqualsAll("age", new DetachedCriteria(Person).eq("firstName", "Bob").property("age"))]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with GreaterThanEqualsSome"() {
        given:
        List criteria = [new Query.GreaterThanEqualsSome("age", new DetachedCriteria(Person).eq("firstName", "Bob").property("age"))]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with GreaterThanSome"() {
        given:
        List criteria = [new Query.GreaterThanSome("age", new DetachedCriteria(Person).eq("firstName", "Bob").property("age"))]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThanEqualsSome"() {
        given:
        List criteria = [new Query.LessThanEqualsSome("age", new DetachedCriteria(Person).eq("firstName", "Bob").property("age"))]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }

    def "test getPredicates with LessThanSome"() {
        given:
        List criteria = [new Query.LessThanSome("age", new DetachedCriteria(Person).eq("firstName", "Bob").property("age"))]
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

    def "test getPredicates with NotExists"() {
        given:
        List criteria = [new Query.NotExists(new DetachedCriteria(Pet).eq("name", "Lucky"))]
        when:
        def predicates = predicateGenerator.getPredicates(cb, query, root, criteria, fromProvider, personEntity)
        then:
        predicates.length == 1
    }
}
