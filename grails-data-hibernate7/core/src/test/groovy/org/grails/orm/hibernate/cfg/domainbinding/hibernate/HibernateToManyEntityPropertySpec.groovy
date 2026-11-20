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
package org.grails.orm.hibernate.cfg.domainbinding.hibernate

import spock.lang.Specification
import org.hibernate.MappingException
import org.hibernate.mapping.PersistentClass
import org.hibernate.mapping.RootClass
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PropertyMapping
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.reflect.EntityReflector
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.Mapping

class HibernateToManyEntityPropertySpec extends Specification {

    def "getAssociatedClass returns the Hibernate PersistentClass"() {
        given:
        def associatedEntity = Mock(HibernatePersistentEntity)
        def buildingContext = Mock(org.hibernate.boot.spi.MetadataBuildingContext)
        def pc = new RootClass(buildingContext)
        associatedEntity.getPersistentClass() >> pc
        
        def prop = new TestHibernateToManyEntityProperty(associatedEntity: associatedEntity)

        expect:
        prop.getAssociatedClass() == pc
    }

    def "getAssociatedClass throws MappingException if PersistentClass is null"() {
        given:
        def associatedEntity = Mock(HibernatePersistentEntity)
        associatedEntity.getPersistentClass() >> null
        
        def prop = new TestHibernateToManyEntityProperty(associatedEntity: associatedEntity, name: "myAssoc")

        when:
        prop.getAssociatedClass()

        then:
        def e = thrown(MappingException)
        e.message == "Association [myAssoc] has no associated class"
    }

    static class TestHibernateToManyEntityProperty implements HibernateToManyEntityProperty {
        HibernatePersistentEntity associatedEntity
        String name

        @Override HibernatePersistentEntity getHibernateAssociatedEntity() { associatedEntity }
        @Override String getName() { name }

        // Stub other required methods
        @Override Class getComponentType() { null }
        @Override PropertyConfig getMappedForm() { null }
        @Override Class getType() { List }
        @Override PersistentEntity getOwner() { null }
        @Override String getCapitilizedName() { name?.capitalize() }
        @Override PropertyMapping<PropertyConfig> getMapping() { null }
        @Override boolean isNullable() { true }
        @Override boolean isInherited() { false }
        @Override EntityReflector.PropertyReader getReader() { null }
        @Override EntityReflector.PropertyWriter getWriter() { null }
        @Override boolean supportsJoinColumnMapping() { true }
        @Override PersistentProperty<?> getInverseSide() { null }
        @Override PersistentEntity getAssociatedEntity() { associatedEntity }
        @Override boolean isBidirectional() { false }
        @Override boolean isOwningSide() { false }
        @Override boolean isCircular() { false }
        @Override boolean isBidirectionalToManyMap() { false }
        @Override boolean isBasic() { false }
        @Override boolean isOneToMany() { true }
        @Override boolean isManyToMany() { false }
        @Override void setHibernateCollection(org.hibernate.mapping.Collection collection) {}
        @Override org.hibernate.mapping.Collection getHibernateCollection() { null }
    }
}
