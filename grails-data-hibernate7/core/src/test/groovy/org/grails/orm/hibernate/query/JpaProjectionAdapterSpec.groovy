package org.grails.orm.hibernate.query

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import jakarta.persistence.Tuple
import jakarta.persistence.criteria.CriteriaQuery
import jakarta.persistence.criteria.Root
import jakarta.persistence.criteria.Selection
import jakarta.persistence.criteria.Subquery
import org.grails.datastore.mapping.query.Query
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity

class JpaProjectionAdapterSpec extends HibernateGormDatastoreSpec {

    def setupSpec() {
        manager.addAllDomainClasses([AdapterTestEntity])
    }

    void "test adapt single property projection"() {
        given:
        def cb = getCriteriaBuilder()
        def query = cb.createQuery(String.class)
        def root = query.from(AdapterTestEntity)
        def context = JpaQueryContext.forRoot(root)
        def adapter = new JpaProjectionAdapter(cb, context)
        
        def projectionList = new Query.ProjectionList()
        projectionList.property("name")

        when:
        adapter.adapt(projectionList, query)

        then:
        query.getSelection() != null
        query.getSelection().getJavaType() == String.class
    }

    void "test adapt multiple property projections (Tuple)"() {
        given:
        def cb = getCriteriaBuilder()
        def query = cb.createTupleQuery()
        def root = query.from(AdapterTestEntity)
        def context = JpaQueryContext.forRoot(root)
        def adapter = new JpaProjectionAdapter(cb, context)
        
        def projectionList = new Query.ProjectionList()
        projectionList.property("name")
        projectionList.property("amount")

        when:
        adapter.adapt(projectionList, query)

        then:
        query.getSelection() != null
        query.getSelection().isCompoundSelection()
        query.getSelection().getCompoundSelectionItems().size() == 2
    }

    void "test adapt aggregate projections"() {
        given:
        def cb = getCriteriaBuilder()
        def query = cb.createQuery(Number.class)
        def root = query.from(AdapterTestEntity)
        def context = JpaQueryContext.forRoot(root)
        def adapter = new JpaProjectionAdapter(cb, context)
        
        def projectionList = new Query.ProjectionList()
        projectionList.sum("amount")

        when:
        adapter.adapt(projectionList, query)

        then:
        query.getSelection() != null
        // JPA sum returns Long or Double usually
    }

    void "test adapt distinct projection"() {
        given:
        def cb = getCriteriaBuilder()
        def query = cb.createQuery(String.class)
        def root = query.from(AdapterTestEntity)
        def context = JpaQueryContext.forRoot(root)
        def adapter = new JpaProjectionAdapter(cb, context)
        
        def projectionList = new Query.ProjectionList()
        projectionList.distinct("category")

        when:
        adapter.adapt(projectionList, query)

        then:
        query.isDistinct()
        query.getSelection() != null
    }

    void "test adapt subquery projections selects first and aliases"() {
        given:
        def cb = getCriteriaBuilder()
        def mainQuery = cb.createQuery(AdapterTestEntity)
        def subquery = mainQuery.subquery(String.class)
        def root = subquery.from(AdapterTestEntity)
        def context = JpaQueryContext.forSubquery(null, root)
        def adapter = new JpaProjectionAdapter(cb, context)
        
        def projectionList = new Query.ProjectionList()
        projectionList.property("name")
        projectionList.property("amount")

        when:
        adapter.adapt(projectionList, subquery)

        then:
        subquery.getSelection() != null
        !subquery.getSelection().isCompoundSelection()
        subquery.getSelection().getAlias() == "col_0"
    }
}

@Entity
class AdapterTestEntity {
    Long id
    String name
    Integer amount
    String category
}
