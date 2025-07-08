package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.mapping.RootClass

class ClassBinderSpec extends HibernateGormDatastoreSpec {


    void "Test defaults"() {
        when:

        def collector = getCollector()
        def grailsDomainBinder = getGrailsDomainBinder()

        def simpleName = "Book"
        def persistentName = "foo.Book"
        def persistentEntity = createPersistentEntity(grailsDomainBinder,simpleName, [:], [:])
        def root = new RootClass(grailsDomainBinder.metadataBuildingContext);
        def binder = new ClassBinder()

        binder.bindClass(persistentEntity,root, collector)
        then:
        root.getEntityName() == persistentName
        root.getJpaEntityName() == simpleName
        root.getProxyInterfaceName() == persistentName
        root.getClassName() == persistentName
        root.isLazy()
        !root.useDynamicInsert()
        !root.useDynamicUpdate()
        !root.hasSelectBeforeUpdate()
        collector.getImports()[simpleName] == persistentName
    }

    void "Test autoImport true"() {
        when:

        def collector = getCollector()
        def grailsDomainBinder = getGrailsDomainBinder()

        def simpleName = "Book"
        def persistentName = "foo.Book"
        def persistentEntity = createPersistentEntity(grailsDomainBinder,simpleName, [:], [autoImport: "true"])
        def root = new RootClass(grailsDomainBinder.metadataBuildingContext);
        def binder = new ClassBinder()

        binder.bindClass(persistentEntity,root, collector)
        then:
        root.getEntityName() == persistentName
        root.getJpaEntityName() == simpleName
        root.getProxyInterfaceName() == persistentName
        root.getClassName() == persistentName
        root.isLazy()
        !root.useDynamicInsert()
        !root.useDynamicUpdate()
        !root.hasSelectBeforeUpdate()
        collector.getImports()[simpleName] == persistentName
    }

    void "Test autoImport false"() {
        when:

        def collector = getCollector()
        def grailsDomainBinder = getGrailsDomainBinder()

        def simpleName = "Book"
        def persistentName = "foo.Book"
        def persistentEntity = createPersistentEntity(grailsDomainBinder, simpleName, [:], [autoImport: "false"])
        def root = new RootClass(grailsDomainBinder.metadataBuildingContext);
        def binder = new ClassBinder()

        binder.bindClass(persistentEntity,root, collector)
        then:
        root.getEntityName() == persistentName
        root.getJpaEntityName() == persistentName
        root.getProxyInterfaceName() == persistentName
        root.getClassName() == persistentName
        root.isLazy()
        !root.useDynamicInsert()
        !root.useDynamicUpdate()
        !root.hasSelectBeforeUpdate()
        !collector.getImports()[simpleName]
    }


}
