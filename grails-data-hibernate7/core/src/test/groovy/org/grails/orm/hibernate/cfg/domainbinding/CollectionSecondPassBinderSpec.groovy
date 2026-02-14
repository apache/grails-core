package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.annotation.Entity
import grails.gorm.specs.HibernateGormDatastoreSpec
import org.grails.orm.hibernate.cfg.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyProperty
import org.grails.orm.hibernate.cfg.domainbinding.util.BackticksRemover
import org.grails.orm.hibernate.cfg.domainbinding.util.ColumnNameForPropertyAndPathFetcher
import org.grails.orm.hibernate.cfg.domainbinding.collectionType.CollectionHolder
import org.grails.orm.hibernate.cfg.domainbinding.util.DefaultColumnNameFetcher

import org.hibernate.mapping.RootClass
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
import org.grails.orm.hibernate.cfg.GrailsDomainBinder

import org.grails.orm.hibernate.cfg.domainbinding.secondpass.CollectionSecondPassBinder

import org.grails.orm.hibernate.cfg.domainbinding.binder.OneToOneBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.ComponentPropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.BasicValueIdCreator
import org.grails.orm.hibernate.cfg.domainbinding.binder.GrailsPropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.IdentityBinder
import org.hibernate.boot.spi.InFlightMetadataCollector
import org.grails.orm.hibernate.cfg.domainbinding.binder.VersionBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleIdBinder
import org.grails.orm.hibernate.cfg.domainbinding.binder.PropertyBinder
import org.grails.orm.hibernate.cfg.domainbinding.util.PropertyFromValueCreator
import org.hibernate.mapping.BasicValue

class CollectionSecondPassBinderSpec extends HibernateGormDatastoreSpec {

    protected Map getBinders(GrailsDomainBinder binder) {
        MetadataBuildingContext metadataBuildingContext = binder.getMetadataBuildingContext()
        PersistentEntityNamingStrategy namingStrategy = binder.getNamingStrategy()
        JdbcEnvironment jdbcEnvironment = binder.getJdbcEnvironment()
        BackticksRemover backticksRemover = new BackticksRemover()
        DefaultColumnNameFetcher defaultColumnNameFetcher = new DefaultColumnNameFetcher(namingStrategy, backticksRemover)
        ColumnNameForPropertyAndPathFetcher columnNameForPropertyAndPathFetcher = new ColumnNameForPropertyAndPathFetcher(namingStrategy, defaultColumnNameFetcher, backticksRemover)
        CollectionHolder collectionHolder = new CollectionHolder(metadataBuildingContext)
        SimpleValueBinder simpleValueBinder = new SimpleValueBinder(namingStrategy, jdbcEnvironment)
        EnumTypeBinder enumTypeBinderToUse = new EnumTypeBinder()
        SimpleValueColumnFetcher simpleValueColumnFetcher = new SimpleValueColumnFetcher()
        CompositeIdentifierToManyToOneBinder compositeIdentifierToManyToOneBinder = new CompositeIdentifierToManyToOneBinder(
                new org.grails.orm.hibernate.cfg.domainbinding.util.ForeignKeyColumnCountCalculator(),
                new TableNameFetcher(namingStrategy),
                namingStrategy,
                defaultColumnNameFetcher,
                backticksRemover,
                simpleValueBinder
        )
        OneToOneBinder oneToOneBinder = new OneToOneBinder(namingStrategy, simpleValueBinder)
        ManyToOneBinder manyToOneBinder = new ManyToOneBinder(namingStrategy, simpleValueBinder, new ManyToOneValuesBinder(), compositeIdentifierToManyToOneBinder, simpleValueColumnFetcher)

        CollectionBinder collectionBinder = new CollectionBinder(
                metadataBuildingContext,
                binder,
                namingStrategy,
                jdbcEnvironment,
                simpleValueBinder,
                enumTypeBinderToUse,
                manyToOneBinder,
                compositeIdentifierToManyToOneBinder,
                simpleValueColumnFetcher
        )
        ComponentPropertyBinder componentPropertyBinder = new ComponentPropertyBinder(
                metadataBuildingContext,
                namingStrategy,
                jdbcEnvironment,
                binder.getMappingCacheHolder(),
                collectionHolder,
                enumTypeBinderToUse,
                collectionBinder,
                new PropertyFromValueCreator(),
                null,
                simpleValueBinder,
                oneToOneBinder,
                manyToOneBinder,
                columnNameForPropertyAndPathFetcher
        )
        GrailsPropertyBinder propertyBinder = new GrailsPropertyBinder(
                metadataBuildingContext,
                namingStrategy,
                collectionHolder,
                enumTypeBinderToUse,
                componentPropertyBinder,
                collectionBinder,
                simpleValueBinder,
                columnNameForPropertyAndPathFetcher,
                oneToOneBinder,
                manyToOneBinder,
                new PropertyFromValueCreator()
        )
        CompositeIdBinder compositeIdBinder = new CompositeIdBinder(metadataBuildingContext, componentPropertyBinder)
        PropertyBinder propertyBinderHelper = new PropertyBinder()
        SimpleIdBinder simpleIdBinder = new SimpleIdBinder(metadataBuildingContext, namingStrategy, jdbcEnvironment, new BasicValueIdCreator(jdbcEnvironment), simpleValueBinder, propertyBinderHelper)
        IdentityBinder identityBinder = new IdentityBinder(simpleIdBinder, compositeIdBinder)
        VersionBinder versionBinder = new VersionBinder(metadataBuildingContext, simpleValueBinder, propertyBinderHelper, BasicValue::new)

        return [
            propertyBinder: propertyBinder,
            collectionBinder: collectionBinder,
            identityBinder: identityBinder,
            versionBinder: versionBinder,
            defaultColumnNameFetcher: defaultColumnNameFetcher,
            columnNameForPropertyAndPathFetcher: columnNameForPropertyAndPathFetcher
        ]
    }

    protected void bindRoot(GrailsDomainBinder binder, GrailsHibernatePersistentEntity entity, InFlightMetadataCollector mappings, String sessionFactoryBeanName) {
        def binders = getBinders(binder)
        binder.bindRoot(entity, mappings, sessionFactoryBeanName, binders.defaultColumnNameFetcher, binders.columnNameForPropertyAndPathFetcher, binders.identityBinder as IdentityBinder, binders.versionBinder as VersionBinder, binders.propertyBinder as GrailsPropertyBinder)
    }

    void setupSpec() {
        manager.addAllDomainClasses([
                Author,
                Book
        ])
    }

    void "Test bind collection second pass"() {
        given:
        def collector = getCollector()
        def binder = getGrailsDomainBinder()
        def binders = getBinders(binder)
        def collectionBinder = binders.collectionBinder
        def namingStrategy = binder.getNamingStrategy()
        def jdbcEnvironment = binder.getJdbcEnvironment()
        def collectionSecondPassBinder = new CollectionSecondPassBinder(
                binder.getMetadataBuildingContext(),
                namingStrategy,
                jdbcEnvironment,
                new org.grails.orm.hibernate.cfg.domainbinding.binder.SimpleValueBinder(namingStrategy, jdbcEnvironment),
                new org.grails.orm.hibernate.cfg.domainbinding.binder.EnumTypeBinder(),
                new org.grails.orm.hibernate.cfg.domainbinding.binder.ManyToOneBinder(namingStrategy, jdbcEnvironment),
                new org.grails.orm.hibernate.cfg.domainbinding.binder.CompositeIdentifierToManyToOneBinder(namingStrategy, jdbcEnvironment),
                new org.grails.orm.hibernate.cfg.domainbinding.util.SimpleValueColumnFetcher()
        )

        def authorEntity = getPersistentEntity(Author) as GrailsHibernatePersistentEntity
        def bookEntity = getPersistentEntity(Book) as GrailsHibernatePersistentEntity

        // Register referenced entity in Hibernate
        bindRoot(binder, bookEntity, collector, "sessionFactory")

        // Manually create RootClass for the main entity
        def rootClass = new RootClass(binder.getMetadataBuildingContext())
        rootClass.setEntityName(authorEntity.name)
        rootClass.setJpaEntityName(authorEntity.name)
        rootClass.setTable(collector.addTable(null, null, "AUTHOR", null, false, binder.getMetadataBuildingContext()))
        
        // Add a primary key to avoid NPE
        def pk = new org.hibernate.mapping.PrimaryKey(rootClass.table)
        def idCol = new org.hibernate.mapping.Column("id")
        rootClass.table.addColumn(idCol)
        pk.addColumn(idCol)
        rootClass.table.setPrimaryKey(pk)
        collector.addEntityBinding(rootClass)

        def booksProp = authorEntity.getPropertyByName("books") as HibernateToManyProperty
        def set = new org.hibernate.mapping.Set(binder.getMetadataBuildingContext(), rootClass)
        set.setRole(authorEntity.name + ".books")
        
        // Initial first pass binding
        collectionBinder.bindCollection(booksProp, set, rootClass, collector, "", "sessionFactory")

        // Prepare persistentClasses map
        Map persistentClasses = [
            (authorEntity.name): rootClass,
            (bookEntity.name): collector.getEntityBinding(bookEntity.name)
        ]

        when:
        collectionSecondPassBinder.bindCollectionSecondPass(booksProp, collector, persistentClasses, set, "sessionFactory")
        collector.processSecondPasses(binder.getMetadataBuildingContext())

        then:
        set.key != null
        set.element != null
        set.collectionTable != null
    }
}

@Entity
class Author {
    Long id
    Set<Book> books
    static hasMany = [books: Book]
}

@Entity
class Book {
    Long id
    String title
}
