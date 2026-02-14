package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.engine.OptimisticLockStyle
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Property
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import java.util.function.BiFunction

import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.VersionBinder

class VersionBinderSpec extends HibernateGormDatastoreSpec {

    MetadataBuildingContext metadataBuildingContext
    SimpleValueBinder simpleValueBinder
    PropertyBinder propertyBinder
    BiFunction<MetadataBuildingContext, Table, BasicValue> basicValueFactory
    VersionBinder versionBinder

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        simpleValueBinder = Mock(SimpleValueBinder)
        propertyBinder = Spy(PropertyBinder)
        basicValueFactory = Mock(BiFunction)
        
        versionBinder = new VersionBinder(metadataBuildingContext, simpleValueBinder, propertyBinder, basicValueFactory)
    }

    def "should bind version property correctly"() {
        given:
        def rootClass = new RootClass(metadataBuildingContext)
        def table = new Table("TEST_TABLE")
        rootClass.setTable(table)
        
        def versionProperty = new StubGrailsHibernatePersistentProperty("version")
        
        def basicValue = new BasicValue(metadataBuildingContext, table)
        
        when:
        versionBinder.bindVersion(versionProperty, rootClass)
        
        then:
        1 * basicValueFactory.apply(metadataBuildingContext, table) >> basicValue
        1 * simpleValueBinder.bindSimpleValue(versionProperty, null, basicValue, "")
        1 * propertyBinder.bindProperty(versionProperty, basicValue)
        
        rootClass.getVersion() != null
        rootClass.getDeclaredVersion() != null
        rootClass.getOptimisticLockStyle() == OptimisticLockStyle.VERSION
        rootClass.getVersion().getValue() == basicValue
        basicValue.getTypeName() == "integer"
    }

    def "should set optimistic lock style to NONE if version is null"() {
        given:
        def rootClass = new RootClass(metadataBuildingContext)
        
        when:
        versionBinder.bindVersion(null, rootClass)
        
        then:
        rootClass.getOptimisticLockStyle() == OptimisticLockStyle.NONE
        rootClass.getVersion() == null
    }

    def "should default type to timestamp if version property name is not 'version'"() {
        given:
        def rootClass = new RootClass(metadataBuildingContext)
        def table = new Table("TEST_TABLE")
        rootClass.setTable(table)
        
        def versionProperty = new StubGrailsHibernatePersistentProperty("lastUpdated")
        
        def basicValue = new BasicValue(metadataBuildingContext, table)
        
        when:
        versionBinder.bindVersion(versionProperty, rootClass)
        
        then:
        1 * basicValueFactory.apply(metadataBuildingContext, table) >> basicValue
        1 * propertyBinder.bindProperty(versionProperty, basicValue)
        basicValue.getTypeName() == "timestamp"
    }

    static class StubGrailsHibernatePersistentProperty implements GrailsHibernatePersistentProperty {
        String name
        StubGrailsHibernatePersistentProperty(String name) { this.name = name }
        @Override String getName() { name }
        @Override String getCapitilizedName() { name.capitalize() }
        @Override Class getType() { Object }
        @Override boolean isNullable() { false }
        @Override PersistentEntity getOwner() { null }
        @Override PropertyMapping getMapping() { null }
        @Override PropertyConfig getMappedForm() { new PropertyConfig() }
        @Override boolean isInherited() { false }
        @Override EntityReflector.PropertyReader getReader() { null }
        @Override EntityReflector.PropertyWriter getWriter() { null }
        @Override String getOwnerClassName() { "Test" }
        @Override boolean isLazyAble() { false }
        @Override boolean isBidirectionalManyToOneWithListMapping(Property prop) { false }
    }
}
