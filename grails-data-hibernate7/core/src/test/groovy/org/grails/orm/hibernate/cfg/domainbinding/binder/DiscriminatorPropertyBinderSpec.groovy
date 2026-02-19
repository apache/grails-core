package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.DiscriminatorConfig
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.Formula
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.SimpleValue
import org.hibernate.mapping.Table
import spock.lang.Shared

class DiscriminatorPropertyBinderSpec extends HibernateGormDatastoreSpec {

    DiscriminatorPropertyBinder binder
    MetadataBuildingContext metadataBuildingContext

    void setup() {
        manager.addAllDomainClasses([DiscriminatorTestEntity])
        def gdb = getGrailsDomainBinder()
        metadataBuildingContext = gdb.getMetadataBuildingContext()
        binder = new DiscriminatorPropertyBinder(
                metadataBuildingContext,
                new SimpleValueColumnBinder(),
                new ColumnConfigToColumnBinder()
        )
    }

    def "test bindDiscriminatorProperty with default values"() {
        given:
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(DiscriminatorTestEntity.name)
        rootClass.setClassName(DiscriminatorTestEntity.name)
        rootClass.setTable(new Table("DISCRIMINATOR_TEST_ENTITY"))
        def mapping = new Mapping()

        when:
        binder.bindDiscriminatorProperty(rootClass, mapping)

        then:
        rootClass.getDiscriminator() != null
        rootClass.getDiscriminatorValue() == DiscriminatorTestEntity.name
        rootClass.getDiscriminator().getTypeName() == "string"
        rootClass.getDiscriminator().getColumnSpan() == 1
        rootClass.getDiscriminator().getColumns().iterator().next().getName() == GrailsDomainBinder.JPA_DEFAULT_DISCRIMINATOR_TYPE
    }

    def "test bindDiscriminatorProperty with custom value"() {
        given:
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(DiscriminatorTestEntity.name)
        rootClass.setClassName(DiscriminatorTestEntity.name)
        rootClass.setTable(new Table("DISCRIMINATOR_TEST_ENTITY"))
        def mapping = new Mapping()
        mapping.setDiscriminator(new DiscriminatorConfig(value: "CUSTOM_VALUE"))

        when:
        binder.bindDiscriminatorProperty(rootClass, mapping)

        then:
        rootClass.getDiscriminatorValue() == "CUSTOM_VALUE"
    }

    def "test bindDiscriminatorProperty with custom type"() {
        given:
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(DiscriminatorTestEntity.name)
        rootClass.setClassName(DiscriminatorTestEntity.name)
        rootClass.setTable(new Table("DISCRIMINATOR_TEST_ENTITY"))
        def mapping = new Mapping()
        mapping.setDiscriminator(new DiscriminatorConfig(type: "integer"))

        when:
        binder.bindDiscriminatorProperty(rootClass, mapping)

        then:
        rootClass.getDiscriminator().getTypeName() == "integer"
    }

    def "test bindDiscriminatorProperty with formula"() {
        given:
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(DiscriminatorTestEntity.name)
        rootClass.setClassName(DiscriminatorTestEntity.name)
        rootClass.setTable(new Table("DISCRIMINATOR_TEST_ENTITY"))
        def mapping = new Mapping()
        mapping.setDiscriminator(new DiscriminatorConfig(formula: "case when type=1 then 'A' else 'B' end"))

        when:
        binder.bindDiscriminatorProperty(rootClass, mapping)

        then:
        rootClass.getDiscriminator().getSelectables().iterator().next() instanceof Formula
        ((Formula)rootClass.getDiscriminator().getSelectables().iterator().next()).getFormula() == "case when type=1 then 'A' else 'B' end"
    }

    def "test bindDiscriminatorProperty with custom column name"() {
        given:
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(DiscriminatorTestEntity.name)
        rootClass.setClassName(DiscriminatorTestEntity.name)
        rootClass.setTable(new Table("DISCRIMINATOR_TEST_ENTITY"))
        def mapping = new Mapping()
        mapping.setDiscriminator(new DiscriminatorConfig(column: [name: "MY_DISCRIMINATOR"]))

        when:
        binder.bindDiscriminatorProperty(rootClass, mapping)

        then:
        rootClass.getDiscriminator().getColumns().iterator().next().getName() == "MY_DISCRIMINATOR"
    }
}

@Entity
class DiscriminatorTestEntity {
    Long id
    String name
}
