package grails.orm

import groovy.lang.Closure
import groovy.lang.MissingMethodException
import org.grails.orm.hibernate.query.HibernateQuery
import org.hibernate.SessionFactory
import spock.lang.Specification

class CriteriaMethodInvokerSpec extends Specification {

    HibernateCriteriaBuilder builder = Mock(HibernateCriteriaBuilder)
    HibernateQuery query = Mock(HibernateQuery)
    CriteriaMethodInvoker invoker = new CriteriaMethodInvoker(builder)

    def setup() {
        builder.getHibernateQuery() >> query
        _ * builder.isPaginationEnabledList() >> false
    }

    void "test invokeMethod handles list call"() {
        given:
        def closure = { eq("foo", "bar") }

        when:
        invoker.invokeMethod("list", [closure] as Object[])

        then:
        1 * builder.isUniqueResult() >> false
        1 * builder.isDistinct() >> false
        1 * builder.isCount() >> false
        1 * query.list() >> []
        1 * builder.isParticipate() >> true
    }

    void "test invokeMethod handles get call"() {
        given:
        def closure = { eq("foo", "bar") }

        when:
        invoker.invokeMethod("get", [closure] as Object[])

        then:
        1 * builder.setUniqueResult(true)
        1 * builder.isUniqueResult() >> true
        1 * query.singleResult() >> null
        1 * builder.isParticipate() >> true
    }

    void "test invokeMethod handles count call"() {
        given:
        def closure = { eq("foo", "bar") }
        def projectionList = new org.grails.datastore.mapping.query.Query.ProjectionList()

        when:
        invoker.invokeMethod("count", [closure] as Object[])

        then:
        1 * builder.setCount(true)
        1 * builder.isUniqueResult() >> false
        1 * builder.isCount() >> true
        1 * query.projections() >> projectionList
        1 * query.singleResult() >> 0L
        1 * builder.isParticipate() >> true
    }

    void "test invokeMethod handles listDistinct call"() {
        given:
        def closure = { eq("foo", "bar") }

        when:
        invoker.invokeMethod("listDistinct", [closure] as Object[])

        then:
        1 * builder.setDistinct(true)
        1 * builder.isUniqueResult() >> false
        1 * builder.isDistinct() >> true
        1 * query.distinct()
        1 * query.list() >> []
        1 * builder.isParticipate() >> true
    }

    void "test invokeMethod handles pagination"() {
        given:
        def params = [max: 10, offset: 5]
        def closure = { }

        when:
        invoker.invokeMethod("list", [params, closure] as Object[])

        then:
        _ * builder.isPaginationEnabledList() >> false // initially false
        1 * builder.setPaginationEnabledList(true)
        1 * query.maxResults(10)
        1 * query.firstResult(5)
        1 * builder.isUniqueResult() >> false
        _ * builder.isPaginationEnabledList() >> true // then true
    }

    void "test invokeMethod handles criteria methods"() {
        when:
        invoker.invokeMethod("eq", ["prop", "value"] as Object[])

        then:
        _ * builder.isPaginationEnabledList() >> false
        _ * builder.getMetaClass() >> GroovySystem.metaClassRegistry.getMetaClass(HibernateCriteriaBuilder)
        1 * builder.eq("prop", "value")
    }

    void "test invokeMethod handles projections block"() {
        given:
        def closure = { sum("balance") }

        when:
        invoker.invokeMethod("projections", [closure] as Object[])

        then:
        _ * builder.isPaginationEnabledList() >> false
        1 * builder.getHibernateQuery() >> query
        // The projections block calls invokeClosureNode which delegates to the builder
    }

    void "test invokeMethod handles association query"() {
        given:
        def closure = { eq("amount", 10) }

        when:
        invoker.invokeMethod("transactions", [closure] as Object[])

        then:
        _ * builder.isPaginationEnabledList() >> false
        _ * builder.getTargetClass() >> InvokerAccount
        1 * builder.getSessionFactory() >> Mock(SessionFactory) {
            getMetamodel() >> Mock(jakarta.persistence.metamodel.Metamodel) {
                entity(InvokerAccount) >> Mock(jakarta.persistence.metamodel.EntityType) {
                    getAttribute("transactions") >> Mock(jakarta.persistence.metamodel.Attribute) {
                        isAssociation() >> true
                    }
                }
            }
        }
        1 * builder.getClassForAssociationType(_) >> InvokerTransaction
        1 * query.join("transactions", _)
        1 * query.in("transactions", _)
    }

    void "test invokeMethod handles and/or/not junctions"() {
        given:
        def closure = { eq("foo", "bar") }

        when:
        invoker.invokeMethod("and", [closure] as Object[])
        invoker.invokeMethod("or", [closure] as Object[])
        invoker.invokeMethod("not", [closure] as Object[])

        then:
        1 * builder.and(closure)
        1 * builder.or(closure)
        1 * builder.not(closure)
    }

    void "test invokeMethod throws MissingMethodException"() {
        when:
        invoker.invokeMethod("nonExistent", [] as Object[])

        then:
        _ * builder.isPaginationEnabledList() >> false
        _ * builder.getMetaClass() >> GroovySystem.metaClassRegistry.getMetaClass(HibernateCriteriaBuilder)
        thrown(MissingMethodException)
    }
}

class InvokerAccount {
    String firstName
    Set<InvokerTransaction> transactions
}
class InvokerTransaction {}

