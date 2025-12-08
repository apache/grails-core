package org.grails.orm.hibernate.cfg.domainbinding

import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.TenantId
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.cfg.HibernateMappingContext
import org.grails.orm.hibernate.cfg.HibernatePersistentEntity
import org.grails.orm.hibernate.cfg.Mapping
import org.hibernate.MappingException
import spock.lang.Specification

class HibernateEntityWrapperSpec extends Specification {

    def "Test getMappedForm with different PersistentEntity types"() {
        given:
        def wrapper = new HibernateEntityWrapper()

        and: "a mock HibernatePersistentEntity"
        def hibernateEntity = Mock(HibernatePersistentEntity)
        def hibernateMapping = Mock(Mapping)
        hibernateEntity.getMappedForm() >> hibernateMapping

        and: "a mock HibernateEmbeddedPersistentEntity"
        def embeddedEntity = Mock(HibernateMappingContext.HibernateEmbeddedPersistentEntity)
        def embeddedMapping = Mock(Mapping)
        embeddedEntity.getMappedForm() >> embeddedMapping

        and: "a generic PersistentEntity implementation"
        def genericEntity = new TestPersistentEntity()

        when: "getMappedForm is called with a HibernatePersistentEntity"
        def resultForHibernateEntity = wrapper.getMappedForm(hibernateEntity)

        then: "it returns the correct mapping"
        resultForHibernateEntity == hibernateMapping

        when: "getMappedForm is called with a HibernateEmbeddedPersistentEntity"
        def resultForEmbeddedEntity = wrapper.getMappedForm(embeddedEntity)

        then: "it returns the correct mapping"
        resultForEmbeddedEntity == embeddedMapping

        when: "getMappedForm is called with a generic PersistentEntity"
        wrapper.getMappedForm(genericEntity)

        then: "a MappingException is thrown"
        thrown(MappingException)
    }

    // Helper class for the exception test
    private static class TestPersistentEntity implements PersistentEntity {
        @Override
        String getName() {
            return "TestEntity"
        }

        // Other methods can return null or default values as they are not called
        @Override
        Class getJavaClass() { return null }
        @Override
        boolean isInstance(Object obj) { return false }
        @Override
        String getDecapitalizedName() { return null }
        @Override
        List<String> getPersistentPropertyNames() { return [] }
        @Override
        List<PersistentProperty> getPersistentProperties() { return [] }
        @Override
        List<Association> getAssociations() { return [] }

        @Override
        List<Embedded> getEmbedded() {
            return null
        }

        @Override
        PersistentProperty getPropertyByName(String name) { return null }
        @Override
        PersistentProperty getIdentity() { return null }
        @Override
        PersistentProperty getVersion() { return null }
        @Override
        boolean isVersioned() { return false }


        @Override
        TenantId getTenantId() {
            return null
        }


        @Override
        PersistentEntity getParentEntity() { return null }
        @Override
        PersistentEntity getRootEntity() { return null }
        @Override
        boolean isRoot() { return false }
        @Override
        String getDiscriminator() { return null }
        @Override
        boolean isOwningEntity(PersistentEntity owner) { return false }
        @Override
        Object newInstance() { return null }
        @Override
        void initialize() { }

        @Override
        boolean isInitialized() {
            return false
        }

        @Override
        MappingContext getMappingContext() { return null }

        @Override
        boolean hasProperty(String name, Class type) {
            return false
        }

        @Override
        boolean isIdentityName(String propertyName) {
            return false
        }

        @Override
        EntityReflector getReflector() {
            return null
        }

        @Override
        boolean addOwner(Class type) {
            return false
        }

        @Override
        boolean isExternal() { return false }

        @Override
        boolean isMultiTenant() {
            return false
        }

        @Override
        PersistentProperty[] getCompositeIdentity() {
            return null
        }

        @Override
        void setExternal(boolean external) { }
        @Override
        ClassMapping getMapping() { return null }
    }
}
