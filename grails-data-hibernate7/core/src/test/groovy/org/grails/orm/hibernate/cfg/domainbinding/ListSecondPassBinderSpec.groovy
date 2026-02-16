package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentProperty
import org.hibernate.mapping.OneToMany
import org.hibernate.mapping.RootClass
import org.hibernate.mapping.Set
import org.hibernate.boot.spi.MetadataBuildingContext
import org.grails.orm.hibernate.cfg.PersistentEntityNamingStrategy
import org.hibernate.engine.jdbc.env.spi.JdbcEnvironment
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneValuesBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.TableNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.binder.CollectionBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.OneToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ClassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentUpdater
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.IdentityBinder
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.grails.orm.hibernate.cfg.domainbinding.binder.VersionBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.hibernate.mapping.BasicValue
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator
import org.grails.orm.hibernate.cfg.GrailsDomainBinder

import org.grails.orm.hibernate.cfg.domainbinding.binder.ClassPropertiesBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.MultiTenantFilterBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.JoinedSubClassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.UnionSubclassBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SingleTableSubclassBinder

class ListSecondPassBinderSpec extends HibernateGormDatastoreSpec {

    protected Map getBinders(GrailsDomainBinder binder) {
        MetadataBuildingContext metadataBuildingContext = binder.getMetadataBuildingContext()
        PersistentEntityNamingStrategy namingStrategy = binder.getNamingStrategy()
        JdbcEnvironment jdbcEnvironment = binder.getJdbcEnvironment()
        BackticksRemover backticksRemover = new BackticksRemover()
        DefaultColumnNameFetcher defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy, backticksRemover)
        ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, backticksRemover)
        CollectionHolder collectionHolder = new CollectionHolder(metadataBuildingContext)
        SimpleValueBinder simpleValueBinder = new SimpleValueBinder(namingStrategy, jdbcEnvironment)
        EnumTypeBinder enumTypeBinderToUse = new EnumTypeBinder(metadataBuildingContext, columnNameForPropertyAndPathFetcher)
        SimpleValueColumnFetcher simpleValueColumnFetcher = new SimpleValueColumnFetcher()
        CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder = new CompositeIdentifierToManyToOneBinder(
                new org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator(),
                new TableNameFetcher(namingStrategy),
                namingStrategy,
                defaultColumnNameFetcher,
                backticksRemover,
                simpleValueBinder
        )
        OneToOneBinder oneToOneBinder = new OneToOneBinder(metadataBuildingContext, namingStrategy, simpleValueBinder)
        ManyToOneBinder manyToOneBinder = new ManyToOneBinder(metadataBuildingContext, namingStrategy, simpleValueBinder, new ManyToOneValuesBinder(), compositeIdentifierToManyToOneBinder, simpleValueColumnFetcher)

        CollectionBinder collectionBinder = new CollectionBinder(
                metadataBuildingContext,
                namingStrategy,
                jdbcEnvironment,
                simpleValueBinder,
                enumTypeBinderToUse,
                manyToOneBinder,
                compositeIdentifierToManyToOneBinder,
                simpleValueColumnFetcher,
                columnNameForPropertyAndPathFetcher
        )
        PropertyFromValueCreator propertyFromValueCreator = new PropertyFromValueCreator()
        ComponentUpdater componentUpdater = new ComponentUpdater(propertyFromValueCreator)
        ComponentBinder componentBinder = new ComponentBinder(
                metadataBuildingContext,
                binder.getMappingCacheHolder(),
                collectionHolder,
                enumTypeBinderToUse,
                collectionBinder,
                simpleValueBinder,
                oneToOneBinder,
                manyToOneBinder,
                columnNameForPropertyAndPathFetcher,
                componentUpdater
        )

        GrailsPropertyBinder propertyBinder = new GrailsPropertyBinder(
                metadataBuildingContext,
                namingStrategy,
                collectionHolder,
                enumTypeBinderToUse,
                componentBinder,
                collectionBinder,
                simpleValueBinder,
                columnNameForPropertyAndPathFetcher,
                oneToOneBinder,
                manyToOneBinder,
                propertyFromValueCreator
        )
        CompositeIdBinder compositeIdBinder = new CompositeIdBinder(metadataBuildingContext, componentBinder, componentUpdater)
        PropertyBinder propertyBinderHelper = new PropertyBinder()
        SimpleIdBinder simpleIdBinder = new SimpleIdBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment, new BasicValueIdCreator(jdbcEnvironment), simpleValueBinder, propertyBinderHelper)
        IdentityBinder identityBinder = new IdentityBinder(simpleIdBinder, compositeIdBinder)
        VersionBinder versionBinder = new VersionBinder(metadataBuildingContext, simpleValueBinder, propertyBinderHelper, BasicValue::new)

        ClassBinder classBinder = new ClassBinder()
        ClassPropertiesBinder classPropertiesBinder = new ClassPropertiesBinder(propertyBinder, propertyFromValueCreator)
        MultiTenantFilterBinder multiTenantFilterBinder = new MultiTenantFilterBinder()
        JoinedSubClassBinder joinedSubClassBinder = new JoinedSubClassBinder(metadataBuildingContext, namingStrategy, new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueColumnBinder(), columnNameForPropertyAndPathFetcher, classBinder)
        UnionSubclassBinder unionSubclassBinder = new UnionSubclassBinder(metadataBuildingContext, namingStrategy, classBinder)
        SingleTableSubclassBinder singleTableSubclassBinder = new SingleTableSubclassBinder(classBinder)

        return [
            propertyBinder: propertyBinder,
            collectionBinder: collectionBinder,
            identityBinder: identityBinder,
            versionBinder: versionBinder,
            defaultColumnNameFetcher: defaultColumnNameFetcher,
            columnNameForPropertyAndPathFetcher: columnNameForPropertyAndPathFetcher,
            classBinder: classBinder,
            classPropertiesBinder: classPropertiesBinder,
            multiTenantFilterBinder: multiTenantFilterBinder,
            joinedSubClassBinder: joinedSubClassBinder,
            unionSubclassBinder: unionSubclassBinder,
            singleTableSubclassBinder: singleTableSubclassBinder
        ]
    }

    protected void bindRoot(GrailsDomainBinder binder, GrailsHibernatePersistentEntity entity, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        def binders = getBinders(binder)
        binder.bindRoot(entity, mappings, sessionFactoryBeanName, 
            binders.defaultColumnNameFetcher, 
            binders.identityBinder as IdentityBinder, 
            binders.versionBinder as VersionBinder, 
            binders.classBinder as ClassBinder, 
            binders.classPropertiesBinder as ClassPropertiesBinder, 
            binders.multiTenantFilterBinder as MultiTenantFilterBinder, 
            binders.joinedSubClassBinder as JoinedSubClassBinder, 
            binders.unionSubclassBinder as UnionSubclassBinder, 
            binders.singleTableSubclassBinder as SingleTableSubclassBinder)
    }

    void setupSpec() {
        manager.addAllDomainClasses([
            org.apache.grails.data.testing.tck.domains.Pet,
            org.apache.grails.data.testing.tck.domains.Person,
            org.apache.grails.data.testing.tck.domains.PetType
        ])
    }

    void "Test bind collection"() {
        given:
        def binder = getGrailsDomainBinder()
        def collectionBinder = getBinders(binder).collectionBinder
        def collector = getCollector()

        def personEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Person) as GrailsHibernatePersistentEntity
        def petEntity = getPersistentEntity(org.apache.grails.data.testing.tck.domains.Pet) as GrailsHibernatePersistentEntity

        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(personEntity.name)
        rootClass.setTable(collector.addTable(null, null, "PERSON", null, false, binder.getMetadataBuildingContext()))

        def petsProp = personEntity.getPropertyByName("pets") as GrailsHibernatePersistentProperty
        def collection = new Set(binder.getMetadataBuildingContext(), rootClass)

        when:
        collectionBinder.bindCollection(petsProp, collection, rootClass, collector, "")

        then:
        collection.role == "${personEntity.name}.pets".toString()
        collection.element instanceof OneToMany
        (collection.element as OneToMany).referencedEntityName == petEntity.name
    }
}
