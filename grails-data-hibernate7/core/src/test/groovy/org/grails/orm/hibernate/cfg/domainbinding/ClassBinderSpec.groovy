/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package org.grails.orm.hibernate.cfg.domainbinding

import grails.gorm.specs.HibernateGormDatastoreSpec
import org.hibernate.mapping.RootClass

import org.grails.orm.hibernate.cfg.domainbinding.binder.ClassBinder

class ClassBinderSpec extends HibernateGormDatastoreSpec {


    void "Test defaults"() {
        when:

        def collector = getCollector()
        def grailsDomainBinder = getGrailsDomainBinder()

        def simpleName = "Book"
        def persistentName = "foo.Book"
        def persistentEntity = createPersistentEntity(grailsDomainBinder,simpleName, [:], [:])
        def root = new RootClass(grailsDomainBinder.metadataBuildingContext);
        def binder = new ClassBinder(collector)

        binder.bindClass(persistentEntity as org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity, root)
        then:
        root.getEntityName() == persistentName
        root.getJpaEntityName() == simpleName
        root.getProxyInterfaceName() == persistentName
        root.getClassName() == persistentName
        root.isLazy()
        !root.useDynamicInsert()
        !root.useDynamicUpdate()
        !root.hasSelectBeforeUpdate()
        root.getBatchSize() == 0
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
        def binder = new ClassBinder(collector)

        binder.bindClass(persistentEntity as org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity, root)
        then:
        root.getEntityName() == persistentName
        root.getJpaEntityName() == simpleName
        root.getProxyInterfaceName() == persistentName
        root.getClassName() == persistentName
        root.isLazy()
        !root.useDynamicInsert()
        !root.useDynamicUpdate()
        !root.hasSelectBeforeUpdate()
        root.getBatchSize() == 0
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
        def binder = new ClassBinder(collector)

        binder.bindClass(persistentEntity as org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity, root)
        then:
        root.getEntityName() == persistentName
        root.getJpaEntityName() == persistentName
        root.getProxyInterfaceName() == persistentName
        root.getClassName() == persistentName
        root.isLazy()
        !root.useDynamicInsert()
        !root.useDynamicUpdate()
        !root.hasSelectBeforeUpdate()
        root.getBatchSize() == 0
        !collector.getImports()[simpleName]
    }

    void "Test dynamic update and insert true"() {
        when:

        def collector = getCollector()
        def grailsDomainBinder = getGrailsDomainBinder()

        def simpleName = "Book"
        def persistentName = "foo.Book"
        def persistentEntity = createPersistentEntity(grailsDomainBinder,simpleName, [:], [dynamicUpdate: "true", dynamicInsert: "true", batchSize: "10"])
        def root = new RootClass(grailsDomainBinder.metadataBuildingContext);
        def binder = new ClassBinder(collector)

        binder.bindClass(persistentEntity as org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentEntity, root)
        then:
        root.getEntityName() == persistentName
        root.getJpaEntityName() == simpleName
        root.getProxyInterfaceName() == persistentName
        root.getClassName() == persistentName
        root.isLazy()
        root.useDynamicInsert()
        root.useDynamicUpdate()
        !root.hasSelectBeforeUpdate()
        root.getBatchSize() == 10
        collector.getImports()[simpleName] == persistentName
    }


}
