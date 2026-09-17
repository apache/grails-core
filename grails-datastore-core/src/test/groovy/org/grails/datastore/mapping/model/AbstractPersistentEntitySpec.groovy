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
package org.grails.datastore.mapping.model

import grails.gorm.annotation.Entity
import spock.lang.Shared
import spock.lang.Specification

import org.grails.datastore.mapping.core.EntityCreationException
import org.grails.datastore.mapping.core.connections.ConnectionSourceSettings
import org.grails.datastore.mapping.core.exceptions.ConfigurationException
import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.config.GormProperties
import org.grails.datastore.mapping.multitenancy.MultiTenancySettings

class AbstractPersistentEntitySpec extends Specification {

    @Shared
    MappingContext mappingContext = new KeyValueMappingContext('test')

    @Shared
    PersistentEntity parent = mappingContext.addPersistentEntity(APEParent)

    @Shared
    PersistentEntity child = mappingContext.addPersistentEntity(APEChild)

    @Shared
    PersistentEntity kid = mappingContext.addPersistentEntity(APEKid)

    void "naming and identity of the entity derive from the java class"() {
        expect:
        parent.name == APEParent.name
        parent.decapitalizedName == 'APEParent'
        parent.discriminator == 'APEParent'
        parent.javaClass == APEParent
        parent.toString() == APEParent.name
        parent.hashCode() == APEParent.hashCode()
        parent.isInstance(new APEParent())
        parent.isInstance(new APEChild())
        !parent.isInstance('nope')
        parent.mappingStrategy == GormProperties.DEFAULT_MAPPING_STRATEGY
        !parent.isAbstract()
        mappingContext.addPersistentEntity(APEAbstract).isAbstract()
    }

    void "entities are equal when they map the same class"() {
        given:
        PersistentEntity other = new TestMappingContext().addPersistentEntity(APEParent)

        expect:
        parent == other
        parent != child
        !parent.equals(null)
        !parent.equals(APEParent)
    }

    void "newInstance uses the default constructor"() {
        expect:
        parent.newInstance() instanceof APEParent

        when:
        mappingContext.addPersistentEntity(APENoDefaultCtor).newInstance()

        then:
        EntityCreationException e = thrown()
        e.message.startsWith('Unable to create entity of type [' + APENoDefaultCtor.name + ']')
    }

    void "bean properties and identity names are checked by name and type"() {
        expect:
        parent.hasProperty('name', String)
        !parent.hasProperty('name', Integer)
        !parent.hasProperty('missing', String)
        parent.isIdentityName('id')
        !parent.isIdentityName('name')
    }

    void "the inheritance hierarchy is exposed through parent and root"() {
        expect:
        parent.root
        parent.parentEntity == null
        parent.rootEntity.is(parent)
        !child.root
        child.parentEntity.is(parent)
        child.rootEntity.is(parent)
        child.initialized
        child.getPropertyByName('name') != null
        child.getPropertyByName('extra') != null
    }

    void "ownership is derived from belongsTo and can be extended"() {
        expect:
        kid.isOwningEntity(parent)
        !parent.isOwningEntity(kid)
        !parent.isOwningEntity(null)

        when:
        boolean added = parent.addOwner(APEKid)

        then:
        added
        parent.isOwningEntity(kid)
    }

    void "properties, associations and embedded components are collected on initialisation"() {
        expect:
        parent.persistentProperties*.name.containsAll(['name', 'kids', 'home'])
        !parent.persistentPropertyNames.contains('kids')
        parent.persistentPropertyNames.containsAll(['name', 'home'])
        parent.associations*.name.sort() == ['home', 'kids']
        parent.embedded*.name == ['home']
        parent.getPropertyByName('missing') == null
        parent.identity.name == 'id'
        parent.compositeIdentity == null
        parent.reflector != null
        parent.mappingContext.is(mappingContext)
        parent.tenantId == null
        !parent.multiTenant
    }

    void "a mapped target name resolves the property as well"() {
        given:
        PersistentEntity mapped = mappingContext.addPersistentEntity(APEMapped)

        expect:
        mapped.getPropertyByName('title').mapping.mappedForm.targetName == 'ttl'
        mapped.getPropertyByName('ttl').is(mapped.getPropertyByName('title'))
    }

    void "the external flag is settable"() {
        given:
        PersistentEntity external = mappingContext.addExternalPersistentEntity(APEExternal)

        expect:
        external.external

        when:
        external.external = false

        then:
        !external.external
    }

    void "versioning depends on the version property and its type"() {
        expect:
        parent.versioned
        parent.version.name == 'version'
        !mappingContext.addPersistentEntity(APEUnversioned).versioned
        !mappingContext.addPersistentEntity(APEStringVersion).versioned
        kid.initialized
        kid.version.name == 'version'
        kid.versioned
    }

    void "the class mapping is built against this entity"() {
        when:
        ClassMapping mapping = parent.mapping

        then:
        mapping.entity.is(parent)
        mapping.identifier != null
        mapping.mappedForm != null
        parent.mappedForm != null
    }

    void "a multi tenant entity in discriminator mode requires a tenant id"() {
        given:
        ConnectionSourceSettings settings = new ConnectionSourceSettings()
        settings.multiTenancy.mode = MultiTenancySettings.MultiTenancyMode.DISCRIMINATOR
        MappingContext tenantContext = new KeyValueMappingContext('tenants', settings)

        when:
        PersistentEntity tenanted = tenantContext.addPersistentEntity(APETenanted)

        then:
        tenanted.multiTenant
        tenanted.tenantId.name == 'tenantId'

        when:
        tenantContext.addPersistentEntity(APETenantless)

        then:
        ConfigurationException e = thrown()
        e.message == 'Class [' + APETenantless.name + '] is multi tenant but does not specify a tenant identifier property'
    }
}

@Entity
class APEParent {
    Long id
    Long version
    String name
    Set kids
    APEHome home
    static hasMany = [kids: APEKid]
    static embedded = ['home']
}

@Entity
class APEChild extends APEParent {
    String extra
}

@Entity
class APEKid {
    Long id
    String name
    APEParent parent
    static belongsTo = [parent: APEParent]
}

class APEHome {
    String street
}

@Entity
abstract class APEAbstract {
    Long id
    String name
}

@Entity
class APENoDefaultCtor {
    Long id
    String name

    APENoDefaultCtor(String name) {
        this.name = name
    }
}

@Entity
class APEMapped {
    Long id
    String title
    static mapping = {
        title key: 'ttl'
    }
}

@Entity
class APEExternal {
    Long id
    String name
}

@Entity
class APEUnversioned {
    Long id
    Long version
    String name
    static mapping = {
        version false
    }
}

@Entity
class APEStringVersion {
    Long id
    String version
    String name
}

interface MultiTenant {
}

@Entity
class APETenanted implements MultiTenant {
    Long id
    String tenantId
    String name
}

@Entity
class APETenantless implements MultiTenant {
    Long id
    String name
}
