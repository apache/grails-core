package org.grails.orm.hibernate.cfg.domainbinding.binder

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.DiscriminatorConfig
import org.grails.orm.hibernate.cfg.Mapping
import org.hibernate.boot.spi.MetadataBuildingContext
import org.hibernate.mapping.BasicValue
import org.hibernate.mapping.Formula
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Table
import spock.lang.Subject

class DiscriminatorPropertyBinderSpec extends HibernateGormDatastoreSpec {

    @Subject
    DiscriminatorPropertyBinder binder

    MetadataBuildingContext metadataBuildingContext
    ConfiguredDiscriminatorBinder configuredBinder
    DefaultDiscriminatorBinder defaultBinder

    def setup() {
        def domainBinder = getGrailsDomainBinder()
        metadataBuildingContext = domainBinder.getMetadataBuildingContext()
        def simpleValueColumnBinder = new SimpleValueColumnBinder()
        def columnConfigToColumnBinder = new ColumnConfigToColumnBinder()
        configuredBinder = new ConfiguredDiscriminatorBinder(simpleValueColumnBinder, columnConfigToColumnBinder)
        defaultBinder = new DefaultDiscriminatorBinder(simpleValueColumnBinder)
        binder = new DiscriminatorPropertyBinder(
                metadataBuildingContext,
                domainBinder.getMappingCacheHolder(),
                configuredBinder,
                defaultBinder
        )
    }

    private RootClass createRootClass() {
        def rootClass = new RootClass(metadataBuildingContext)
        rootClass.setEntityName(DiscriminatorPropertyBinderSpecEntity.name)
        rootClass.setJpaEntityName(DiscriminatorPropertyBinderSpecEntity.name)
        rootClass.setClassName(DiscriminatorPropertyBinderSpecEntity.name)
        rootClass.setTable(new Table("orm", "DISCRIMINATOR_TEST_ENTITY"))
        return rootClass
    }

    def "test bindDiscriminatorProperty with no discriminator config uses default binder"() {
        given:
        def rootClass = createRootClass()
        def mapping = new Mapping()
        getGrailsDomainBinder().getMappingCacheHolder().cacheMapping(DiscriminatorPropertyBinderSpecEntity, mapping)

        when:
        binder.bindDiscriminatorProperty(rootClass)

        then:
        rootClass.getDiscriminator() != null
        rootClass.getDiscriminator() instanceof BasicValue
        rootClass.getDiscriminatorValue() == DiscriminatorPropertyBinderSpecEntity.name
        rootClass.getDiscriminator().getColumns().iterator().next().getName() == GrailsDomainBinder.JPA_DEFAULT_DISCRIMINATOR_TYPE
    }

    def "test bindDiscriminatorProperty with discriminator config uses configured binder"() {
        given:
        def rootClass = createRootClass()
        def mapping = new Mapping()
        def discriminatorConfig = new DiscriminatorConfig(value: "TEST")
        mapping.setDiscriminator(discriminatorConfig)
        getGrailsDomainBinder().getMappingCacheHolder().cacheMapping(DiscriminatorPropertyBinderSpecEntity, mapping)

        when:
        binder.bindDiscriminatorProperty(rootClass)

        then:
        rootClass.getDiscriminator() != null
        rootClass.getDiscriminator() instanceof BasicValue
        rootClass.getDiscriminatorValue() == "TEST"
        rootClass.getDiscriminator().getColumns().iterator().next().getName() == GrailsDomainBinder.JPA_DEFAULT_DISCRIMINATOR_TYPE
    }

    def "test bindDiscriminatorProperty with custom discriminator column name"() {
        given:
        def rootClass = createRootClass()
        def mapping = new Mapping()
        def discriminatorConfig = new DiscriminatorConfig(value: "TEST", column: [name: "MY_TYPE"])
        mapping.setDiscriminator(discriminatorConfig)
        getGrailsDomainBinder().getMappingCacheHolder().cacheMapping(DiscriminatorPropertyBinderSpecEntity, mapping)

        when:
        binder.bindDiscriminatorProperty(rootClass)

        then:
        rootClass.getDiscriminator().getColumns().iterator().next().getName() == "MY_TYPE"
    }

    def "test bindDiscriminatorProperty with formula"() {
        given:
        def rootClass = createRootClass()
        def mapping = new Mapping()
        def discriminatorConfig = new DiscriminatorConfig(value: "TEST", formula: "case when type=1 then 'A' else 'B' end")
        mapping.setDiscriminator(discriminatorConfig)
        getGrailsDomainBinder().getMappingCacheHolder().cacheMapping(DiscriminatorPropertyBinderSpecEntity, mapping)

        when:
        binder.bindDiscriminatorProperty(rootClass)

        then:
        rootClass.getDiscriminator().getSelectables().iterator().next() instanceof Formula
    }
}

@Entity
class DiscriminatorPropertyBinderSpecEntity {
    Long id
    String name
}
