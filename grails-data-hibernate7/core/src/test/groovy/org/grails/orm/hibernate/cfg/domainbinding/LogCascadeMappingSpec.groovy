package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.ManyToOne
import org.grails.datastore.mapping.model.types.OneToMany
import org.grails.datastore.mapping.model.types.OneToOne
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateManyToOneProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateOneToOneProperty
import org.slf4j.Logger
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

import org.grails.orm.hibernate.cfg.domainbinding.util.CascadeBehavior
import org.grails.orm.hibernate.cfg.domainbinding.util.LogCascadeMapping

class LogCascadeMappingSpec extends Specification {

    Logger log = Mock(Logger)
    
    @Subject
    LogCascadeMapping loggerHelper = new LogCascadeMapping(log)

    @Unroll
    def "should log correctly for association type #typeDescription when debug is enabled"() {
        given:
        log.isDebugEnabled() >> true
        
        def association = Mock(associationClass)
        def owner = Mock(PersistentEntity)
        def associatedEntity = Mock(PersistentEntity)
        
        association.getOwner() >> owner
        association.getName() >> "testProperty"
        association.getAssociatedEntity() >> associatedEntity
        owner.getName() >> "OwnerClass"
        associatedEntity.getJavaClass() >> TargetClass
        
        def cascadeBehavior = CascadeBehavior.ALL

        when:
        loggerHelper.logCascadeMapping(association, cascadeBehavior)

        then:
        1 * log.debug("Mapping cascade strategy for {} property {}.{} referencing type [{}] -> [CASCADE: {}]",
                typeDescription, "OwnerClass", "testProperty", TargetClass.name, cascadeBehavior)

        where:
        associationClass             | typeDescription
        HibernateManyToManyProperty  | "many-to-many"
        HibernateOneToManyProperty   | "one-to-many"
        HibernateOneToOneProperty    | "one-to-one"
        HibernateManyToOneProperty   | "many-to-one"
    }

    def "should log unknown for unrecognized association type"() {
        given:
        log.isDebugEnabled() >> true
        def association = Mock(Association)
        def owner = Mock(PersistentEntity)
        def associatedEntity = Mock(PersistentEntity)
        
        association.getOwner() >> owner
        association.getName() >> "testProperty"
        association.getAssociatedEntity() >> associatedEntity
        owner.getName() >> "OwnerClass"
        associatedEntity.getJavaClass() >> TargetClass
        
        def cascadeBehavior = CascadeBehavior.ALL

        when:
        loggerHelper.logCascadeMapping(association, cascadeBehavior)

        then:
        1 * log.debug("Mapping cascade strategy for {} property {}.{} referencing type [{}] -> [CASCADE: {}]",
                "unknown", "OwnerClass", "testProperty", TargetClass.name, cascadeBehavior)
    }

    def "should not log if debug is disabled"() {
        given:
        log.isDebugEnabled() >> false
        def association = Mock(Association)

        when:
        loggerHelper.logCascadeMapping(association, CascadeBehavior.ALL)

        then:
        0 * log.debug(*_)
    }

    static class TargetClass {}
}
