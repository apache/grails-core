package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.datastore.mapping.model.PersistentProperty
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.engine.OptimisticLockStyle
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import java.util.function.BiFunction

class VersionBinderSpec extends HibernateGormDatastoreSpec {

    MetadataBuildingContext metadataBuildingContext
    SimpleValueBinder simpleValueBinder
    PropertyBinder propertyBinder
    BiFunction<MetadataBuildingContext, Table, BasicValue> basicValueFactory
    VersionBinder versionBinder

    def setup() {
        metadataBuildingContext = getGrailsDomainBinder().getMetadataBuildingContext()
        simpleValueBinder = Mock(SimpleValueBinder)
        propertyBinder = Mock(PropertyBinder)
        basicValueFactory = Mock(BiFunction)
        
        versionBinder = new VersionBinder(metadataBuildingContext, simpleValueBinder, propertyBinder, basicValueFactory)
    }

    def "should bind version property correctly"() {
        given:
        def rootClass = new RootClass(metadataBuildingContext)
        def table = new Table("TEST_TABLE")
        rootClass.setTable(table)
        
        def versionProperty = Mock(PersistentProperty) {
            getName() >> "version"
        }
        
        def basicValue = new BasicValue(metadataBuildingContext, table)
        
        when:
        versionBinder.bindVersion(versionProperty, rootClass)
        
        then:
        1 * basicValueFactory.apply(metadataBuildingContext, table) >> basicValue
        1 * simpleValueBinder.bindSimpleValue(versionProperty, null, basicValue, "")
        1 * propertyBinder.bindProperty(versionProperty, _)
        
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
        
        def versionProperty = Mock(PersistentProperty) {
            getName() >> "lastUpdated"
        }
        
        def basicValue = new BasicValue(metadataBuildingContext, table)
        
        when:
        versionBinder.bindVersion(versionProperty, rootClass)
        
        then:
        1 * basicValueFactory.apply(metadataBuildingContext, table) >> basicValue
        basicValue.getTypeName() == "timestamp"
    }
}
