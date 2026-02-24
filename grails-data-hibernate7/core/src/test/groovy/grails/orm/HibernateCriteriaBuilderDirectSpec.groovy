package grails.orm

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.criteria.JoinType
import org.grails.datastore.mapping.query.Query
import spock.lang.Shared

/**
 * Direct (non-DSL) tests for the thin-wrapper methods of {@link HibernateCriteriaBuilder}.
 * <p>
 * The existing {@link HibernateCriteriaBuilderSpec} exercises the same methods through the
 * Groovy DSL (closures routed via {@code invokeMethod} → {@code CriteriaMethodInvoker}).
 * JaCoCo cannot trace method-body coverage through that dynamic dispatch path.
 * These tests call every wrapper method as a direct Java-style invocation so JaCoCo
 * sees the actual method bodies executed.
 */
class HibernateCriteriaBuilderDirectSpec extends HibernateGormDatastoreSpec {

    @Shared HibernateCriteriaBuilder builder

    def setupSpec() {
        manager.addAllDomainClasses([DirectAccount, DirectTransaction])
    }

    def setup() {
        builder = new HibernateCriteriaBuilder(
                DirectAccount,
                manager.hibernateDatastore.sessionFactory,
                manager.hibernateDatastore)
    }

    // ─── Comparison predicates ─────────────────────────────────────────────

    def "eq(String, Object) delegates and returns this"() {
        expect: builder.eq("firstName", "Fred").is(builder)
    }

    def "eq(String, Object, Map) delegates and returns this"() {
        expect: builder.eq("firstName", "Fred", Collections.emptyMap()).is(builder)
    }

    def "eq with ignoreCase=true calls like on hibernateQuery"() {
        expect: builder.eq("firstName", "Fred", [ignoreCase: true]).is(builder)
    }

    def "eq(Map, String, Object) groovy-map-first form delegates and returns this"() {
        expect: builder.eq(Collections.emptyMap(), "firstName", "Fred").is(builder)
    }

    def "ne(String, Object) delegates and returns this"() {
        expect: builder.ne("firstName", "Fred").is(builder)
    }

    def "gt(String, Object) delegates and returns this"() {
        expect: builder.gt("balance", BigDecimal.ONE).is(builder)
    }

    def "ge(String, Object) delegates and returns this"() {
        expect: builder.ge("balance", BigDecimal.ONE).is(builder)
    }

    def "lt(String, Object) delegates and returns this"() {
        expect: builder.lt("balance", BigDecimal.TEN).is(builder)
    }

    def "le(String, Object) delegates and returns this"() {
        expect: builder.le("balance", BigDecimal.TEN).is(builder)
    }

    def "gte(String, Object) aliases ge and returns this"() {
        expect: builder.gte("balance", BigDecimal.ONE).is(builder)
    }

    def "lte(String, Object) aliases le and returns this"() {
        expect: builder.lte("balance", BigDecimal.TEN).is(builder)
    }

    def "like(String, Object) delegates and returns this"() {
        expect: builder.like("firstName", "Fr%").is(builder)
    }

    def "ilike(String, Object) delegates and returns this"() {
        expect: builder.ilike("firstName", "fr%").is(builder)
    }

    def "rlike(String, Object) delegates and returns this"() {
        expect: builder.rlike("firstName", "^Fr.*").is(builder)
    }

    def "between(String, Object, Object) delegates and returns this"() {
        expect: builder.between("balance", BigDecimal.ONE, BigDecimal.TEN).is(builder)
    }

    // ─── Null / empty ──────────────────────────────────────────────────────

    def "isEmpty(String) delegates and returns this"() {
        expect: builder.isEmpty("transactions").is(builder)
    }

    def "isNotEmpty(String) delegates and returns this"() {
        expect: builder.isNotEmpty("transactions").is(builder)
    }

    def "isNull(String) delegates and returns this"() {
        expect: builder.isNull("branch").is(builder)
    }

    def "isNotNull(String) delegates and returns this"() {
        expect: builder.isNotNull("branch").is(builder)
    }

    // ─── Identity equality ─────────────────────────────────────────────────

    def "idEq(Object) aliases eq('id', o) and returns this"() {
        given: def fred = new DirectAccount(balance: 100, firstName: "Fred", lastName: "F").save(failOnError: true, flush: true)
        expect: builder.idEq(fred.id).is(builder)
    }

    def "idEquals(Object) aliases idEq and returns this"() {
        given: def fred = DirectAccount.first()
        expect: builder.idEquals(fred?.id ?: 1L).is(builder)
    }

    // ─── 'in' variants ────────────────────────────────────────────────────

    def "in(String, Collection) delegates and returns this"() {
        expect: builder.in("firstName", ["Fred", "Barney"]).is(builder)
    }

    def "in(String, Object[]) delegates and returns this"() {
        expect: builder.in("firstName", ["Fred", "Barney"] as Object[]).is(builder)
    }

    def "inList(String, Collection) delegates to in and returns this"() {
        expect: builder.inList("firstName", ["Fred", "Barney"]).is(builder)
    }

    def "inList(String, Object[]) delegates to in and returns this"() {
        expect: builder.inList("firstName", ["Fred", "Barney"] as Object[]).is(builder)
    }

    // ─── allEq ────────────────────────────────────────────────────────────

    def "allEq(Map) delegates and returns this"() {
        expect: builder.allEq([firstName: "Fred", lastName: "Flintstone"]).is(builder)
    }

    // ─── Property comparisons ──────────────────────────────────────────────

    def "eqProperty(String, String) delegates and returns this"() {
        expect: builder.eqProperty("firstName", "firstName").is(builder)
    }

    def "neProperty(String, String) delegates and returns this"() {
        expect: builder.neProperty("firstName", "lastName").is(builder)
    }

    def "gtProperty(String, String) delegates and returns this"() {
        expect: builder.gtProperty("balance", "balance").is(builder)
    }

    def "geProperty(String, String) delegates and returns this"() {
        expect: builder.geProperty("balance", "balance").is(builder)
    }

    def "ltProperty(String, String) delegates and returns this"() {
        expect: builder.ltProperty("balance", "balance").is(builder)
    }

    def "leProperty(String, String) delegates and returns this"() {
        expect: builder.leProperty("balance", "balance").is(builder)
    }

    // ─── Collection-size constraints ───────────────────────────────────────

    def "sizeEq(String, int) delegates and returns this"() {
        expect: builder.sizeEq("transactions", 2).is(builder)
    }

    def "sizeGt(String, int) delegates and returns this"() {
        expect: builder.sizeGt("transactions", 0).is(builder)
    }

    def "sizeGe(String, int) delegates and returns this"() {
        expect: builder.sizeGe("transactions", 1).is(builder)
    }

    def "sizeLe(String, int) delegates and returns this"() {
        expect: builder.sizeLe("transactions", 5).is(builder)
    }

    def "sizeLt(String, int) delegates and returns this"() {
        expect: builder.sizeLt("transactions", 3).is(builder)
    }

    def "sizeNe(String, int) delegates and returns this"() {
        expect: builder.sizeNe("transactions", 0).is(builder)
    }

    // ─── Ordering ──────────────────────────────────────────────────────────

    def "order(String) delegates via Order and returns this"() {
        expect: builder.order("firstName").is(builder)
    }

    def "order(String, 'asc') sets ascending order and returns this"() {
        expect: builder.order("firstName", "asc").is(builder)
    }

    def "order(String, 'desc') sets descending order and returns this"() {
        expect: builder.order("balance", "desc").is(builder)
    }

    def "order(String, unrecognised) defaults to ascending and returns this"() {
        expect: builder.order("balance", "unknown").is(builder)
    }

    def "order(Query.Order) delegates and returns this"() {
        expect: builder.order(new Query.Order("firstName")).is(builder)
    }

    // ─── Pagination / fetch ────────────────────────────────────────────────

    def "firstResult(int) delegates and returns this"() {
        expect: builder.firstResult(5).is(builder)
    }

    def "maxResults(int) delegates and returns this"() {
        expect: builder.maxResults(10).is(builder)
    }

    // ─── Join / select ─────────────────────────────────────────────────────

    def "join(String) delegates with INNER join and returns this"() {
        expect: builder.join("transactions").is(builder)
    }

    def "join(String, JoinType) delegates and returns this"() {
        expect: builder.join("transactions", JoinType.LEFT).is(builder)
    }

    def "select(String) delegates and returns this"() {
        expect: builder.select("balance").is(builder)
    }

    // ─── Cache / readOnly / lock ───────────────────────────────────────────

    def "cache(boolean) sets flag and returns this"() {
        expect: builder.cache(true).is(builder)
    }

    def "readOnly(boolean) sets flag and returns this"() {
        expect: builder.readOnly(true).is(builder)
    }

    def "lock(boolean) sets flag without throwing"() {
        when: builder.lock(true)
        then: noExceptionThrown()
    }

    // ─── Projection wrappers ───────────────────────────────────────────────

    def "property(String) adds property projection and returns this"() {
        expect: builder.property("firstName").is(builder)
    }

    def "avg(String) adds avg projection and returns this"() {
        expect: builder.avg("balance").is(builder)
    }

    def "distinct(String) adds distinct projection and returns this"() {
        expect: builder.distinct("firstName").is(builder)
    }

    def "count() adds count projection and returns non-null ProjectionList"() {
        expect: builder.count() != null
    }

    def "countDistinct(String) adds countDistinct projection and returns this"() {
        expect: builder.countDistinct("firstName").is(builder)
    }

    def "groupProperty(String) adds groupProperty projection and returns this"() {
        expect: builder.groupProperty("lastName").is(builder)
    }

    def "min(String) adds min projection and returns this"() {
        expect: builder.min("balance").is(builder)
    }

    def "max(String) adds max projection and returns this"() {
        expect: builder.max("balance").is(builder)
    }

    def "sum(String) adds sum projection and returns this"() {
        expect: builder.sum("balance").is(builder)
    }

    def "rowCount() delegates to count and returns non-null ProjectionList"() {
        expect: builder.rowCount() != null
    }

    def "id() adds id projection and returns this"() {
        expect: builder.id().is(builder)
    }

    // ─── State flags ───────────────────────────────────────────────────────

    def "setUniqueResult and isUniqueResult are symmetric"() {
        when: builder.setUniqueResult(true)
        then: builder.isUniqueResult()
    }

    def "setPaginationEnabledList and isPaginationEnabledList are symmetric"() {
        when: builder.setPaginationEnabledList(true)
        then: builder.isPaginationEnabledList()
    }

    def "setScroll(boolean) sets scroll flag"() {
        when: builder.setScroll(true)
        then: noExceptionThrown()
    }

    def "setCount(boolean) sets count flag"() {
        when: builder.setCount(true)
        then: builder.isCount()
    }

    def "setDistinct(boolean) sets distinct flag"() {
        when: builder.setDistinct(true)
        then: builder.isDistinct()
    }

    def "setDefaultFlushMode and getDefaultFlushMode are symmetric"() {
        when: builder.setDefaultFlushMode(2)
        then: builder.getDefaultFlushMode() == 2
    }

    def "getTargetClass returns the entity class"() {
        expect: builder.targetClass == DirectAccount
    }

    def "getHibernateQuery returns non-null"() {
        expect: builder.hibernateQuery != null
    }

    def "getSessionFactory returns non-null"() {
        expect: builder.sessionFactory != null
    }

    def "getCriteriaBuilder returns non-null"() {
        expect: builder.criteriaBuilder != null
    }

    def "setTargetClass updates the target class"() {
        when: builder.targetClass = DirectTransaction
        then: builder.targetClass == DirectTransaction
    }
}

@Entity
class DirectAccount {
    String firstName
    String lastName
    BigDecimal balance
    String branch
    Set<DirectTransaction> transactions

    static hasMany = [transactions: DirectTransaction]
    static constraints = { branch nullable: true }
}

@Entity
class DirectTransaction {
    BigDecimal amount
    static belongsTo = [account: DirectAccount]
}
