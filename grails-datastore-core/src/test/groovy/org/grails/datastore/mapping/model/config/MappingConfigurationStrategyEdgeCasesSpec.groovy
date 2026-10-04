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
package org.grails.datastore.mapping.model.config

import jakarta.persistence.Embedded
import jakarta.persistence.EmbeddedId
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.ManyToOne
import jakarta.persistence.OneToMany
import jakarta.persistence.Transient
import spock.lang.Specification

import org.grails.datastore.mapping.keyvalue.mapping.config.KeyValueMappingContext
import org.grails.datastore.mapping.model.ClassMapping
import org.grails.datastore.mapping.model.IdentityMapping
import org.grails.datastore.mapping.model.IllegalMappingException
import org.grails.datastore.mapping.model.MappingConfigurationStrategy
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.ValueGenerator
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.model.types.ManyToMany
import org.grails.datastore.mapping.model.types.OneToOne
import org.grails.datastore.mapping.model.types.Simple

class MappingConfigurationStrategyEdgeCasesSpec extends Specification {

    KeyValueMappingContext context = new KeyValueMappingContext('edge')

    void "an explicit mappedBy that names no property is an illegal mapping"() {
        when:
        context.addPersistentEntity(EcBadMappedBy)

        then:
        IllegalMappingException e = thrown()
        e.message == 'Non-existent mapping property [nowhere] specified for property [items] in class [' + EcBadMappedBy.name + ']'
    }

    void "an external entity with an invalid mappedBy silently drops the association"() {
        when:
        PersistentEntity entity = context.addExternalPersistentEntity(EcBadMappedBy)
        entity.initialize()

        then:
        entity.getPropertyByName('items') == null
    }

    void "mappedBy none forces a unidirectional one to many"() {
        when:
        PersistentEntity entity = context.addPersistentEntity(EcNoneMappedBy)
        org.grails.datastore.mapping.model.types.OneToMany items = entity.getPropertyByName('items')

        then:
        !items.bidirectional
        items.referencedPropertyName == null
        items.associatedEntity.javaClass == EcItem
    }

    void "hasMany on both sides yields a many to many with an inverse property"() {
        when:
        PersistentEntity left = context.addPersistentEntity(EcLeft)
        ManyToMany rights = left.getPropertyByName('rights')
        ManyToMany lefts = context.getPersistentEntity(EcRight.name).getPropertyByName('lefts')

        then:
        rights.inversePropertyName == 'lefts'
        rights.referencedPropertyName == 'lefts'
        lefts.referencedPropertyName == 'rights'
        rights.bidirectional
        lefts.bidirectional
        rights.owningSide
        !lefts.owningSide
    }

    void "embedded collections of simple values and of non entities are supported"() {
        when:
        PersistentEntity entity = context.addPersistentEntity(EcEmbedding)

        then:
        entity.getPropertyByName('tags') instanceof Basic
        ((Basic) entity.getPropertyByName('tags')).componentType == String
        entity.getPropertyByName('addresses') instanceof EmbeddedCollection
        ((EmbeddedCollection) entity.getPropertyByName('addresses')).associatedEntity.javaClass == EcAddress
        entity.getPropertyByName('home') instanceof org.grails.datastore.mapping.model.types.Embedded
        entity.getPropertyByName('untyped') instanceof Basic
        entity.getPropertyByName('plain') instanceof Basic
        entity.getPropertyByName('other') instanceof OneToOne
        ((OneToOne) entity.getPropertyByName('other')).foreignKeyInChild
        entity.getPropertyByName('ignored') == null
        entity.getPropertyByName('alsoIgnored') == null
    }

    void "a strategy that cannot expand the mapping context leaves unknown associations without an entity"() {
        given:
        MappingConfigurationStrategy strategy = new GormMappingConfigurationStrategy(context.mappingFactory)
        strategy.canExpandMappingContext = false
        PersistentEntity owner = context.addPersistentEntity(EcNoneMappedBy)

        when:
        List<PersistentProperty> properties = strategy.getPersistentProperties(EcNoneMappedBy, context, owner.mapping)
        List<PersistentProperty> empty = strategy.getPersistentProperties(EcItem, new KeyValueMappingContext('other'), null)

        then:
        properties*.name == ['items', 'version']
        empty.empty
        strategy.getPersistentProperties(EcNoneMappedBy, context).find { it.name == 'items' } != null
        strategy.getOwningEntities(EcItem, context) == [EcNoneMappedBy] as Set
    }

    void "identifiers are excluded unless requested and identity lookups validate the mapping"() {
        given:
        MappingConfigurationStrategy strategy = context.mappingSyntaxStrategy
        PersistentEntity entity = context.addPersistentEntity(EcEmbedding)

        expect:
        !strategy.getPersistentProperties(entity, context, entity.mapping).any { it.name == 'id' }
        strategy.getPersistentProperties(entity, context, entity.mapping, true).any { it.name == 'id' }
        strategy.getIdentity(EcEmbedding, context).name == 'id'
        strategy.getIdentityMapping(entity.mapping).identifierName == ['id'] as String[]
        strategy.getDefaultIdentityMapping(entity.mapping).identifierName == ['id'] as String[]
        GormMappingConfigurationStrategy.isAbstract(context.addPersistentEntity(EcAbstract))
        GormMappingConfigurationStrategy.MAPPED_BY_NONE == 'none'

        when:
        MappingContext compositeContext = Stub(MappingContext) {
            getPersistentEntity(EcCompositeMissing.name) >> Stub(PersistentEntity) {
                getJavaClass() >> EcCompositeMissing
                getMapping() >> Stub(ClassMapping) {
                    getIdentifier() >> Stub(IdentityMapping) { getIdentifierName() >> (['code', 'missing'] as String[]) }
                }
                getPropertyByName(_) >> null
            }
        }
        strategy.getCompositeIdentity(EcCompositeMissing, compositeContext)

        then:
        IllegalMappingException e = thrown()
        e.message == 'Invalid composite id mapping. Could not resolve property [missing] for entity [' + EcCompositeMissing.name + ']'

        when:
        context.addPersistentEntity(EcAbstractBadId)

        then:
        e = thrown()
        e.message == 'Mapped identifier [nope] for class [' + EcAbstractBadId.name + '] is not a valid property'
    }

    void "jpa entities honour transient, generated value, embedded id and embedded annotations"() {
        when:
        PersistentEntity entity = context.addPersistentEntity(EcJpaOrder)
        IdentityMapping identity = context.mappingSyntaxStrategy.getIdentityMapping(entity.mapping)

        then:
        entity.identity.name == 'orderKey'
        identity.identifierName == ['orderKey'] as String[]
        identity.identifierName.is(identity.identifierName)
        identity.generator == ValueGenerator.AUTO
        identity.classMapping.is(entity.mapping)
        identity.mappedForm.is(entity.identity.mapping.mappedForm)
        entity.getPropertyByName('scratch') == null
        entity.getPropertyByName('sequence') instanceof Simple
        entity.getPropertyByName('sequence').mapping.mappedForm.derived
        !entity.getPropertyByName('note').mapping.mappedForm.derived
        entity.getPropertyByName('shipping') instanceof org.grails.datastore.mapping.model.types.Embedded
        entity.getPropertyByName('history') instanceof EmbeddedCollection
        entity.getPropertyByName('codes') instanceof Basic
        entity.getPropertyByName('lines') instanceof org.grails.datastore.mapping.model.types.OneToMany
        entity.getPropertyByName('lines').referencedPropertyName == 'order'
        ((org.grails.datastore.mapping.model.types.ManyToOne) context.getPersistentEntity(EcJpaLine.name).getPropertyByName('order')).referencedPropertyName == 'lines'
        !((org.grails.datastore.mapping.model.types.ManyToOne) context.getPersistentEntity(EcJpaLine.name).getPropertyByName('order')).foreignKeyInChild
    }

    void "jpa entities without an id annotation default to the id property"() {
        when:
        PersistentEntity entity = context.addPersistentEntity(EcJpaPlain)
        ClassMapping mapping = entity.mapping

        then:
        context.mappingSyntaxStrategy.getIdentityMapping(mapping).identifierName == ['id'] as String[]
        entity.identity.name == 'id'
    }

}

@grails.gorm.annotation.Entity
class EcItem {

    Long id
    String name

    static belongsTo = [owner: EcNoneMappedBy]

}

@grails.gorm.annotation.Entity
class EcBadMappedBy {

    Long id
    Set<EcItem> items
    static hasMany = [items: EcItem]
    static mappedBy = [items: 'nowhere']

}

@grails.gorm.annotation.Entity
class EcNoneMappedBy {

    Long id
    Set<EcItem> items
    static hasMany = [items: EcItem]
    static mappedBy = [items: 'none']

}

@grails.gorm.annotation.Entity
class EcLeft {

    Long id
    Set<EcRight> rights
    static hasMany = [rights: EcRight]

}

@grails.gorm.annotation.Entity
class EcRight {

    Long id
    Set<EcLeft> lefts
    static hasMany = [lefts: EcLeft]
    static belongsTo = EcLeft

}

class EcAddress {

    String street

}

@grails.gorm.annotation.Entity
class EcOther {

    Long id
    String name

}

@grails.gorm.annotation.Entity
class EcEmbedding {

    Long id
    List<String> tags
    List<EcAddress> addresses
    EcAddress home
    List untyped
    List<EcAddress> plain
    EcOther other
    String ignored
    transient String alsoIgnored

    static embedded = ['tags', 'addresses', 'home']
    static hasOne = [other: EcOther]
    static transients = ['ignored']

}

@grails.gorm.annotation.Entity
abstract class EcAbstract {

    Long id

}

class EcCompositeMissing {

    String code

}

@grails.gorm.annotation.Entity
abstract class EcAbstractBadId {

    Long id

    static mapping = {
        id name: 'nope'
    }

}

class EcKey implements Serializable {

    String region
    Long number

}

@jakarta.persistence.Entity
class EcJpaOrder {

    @EmbeddedId
    EcKey orderKey
    @Transient
    String scratch
    @GeneratedValue
    Long sequence
    String note
    @Embedded
    EcAddress shipping
    @Embedded
    List<EcAddress> history
    List<String> codes
    @OneToMany(mappedBy = 'order')
    Set<EcJpaLine> lines

}

@jakarta.persistence.Entity
class EcJpaLine {

    @Id
    Long id
    @ManyToOne
    EcJpaOrder order

}

@jakarta.persistence.Entity
class EcJpaPlain {

    Long id
    String name

}
