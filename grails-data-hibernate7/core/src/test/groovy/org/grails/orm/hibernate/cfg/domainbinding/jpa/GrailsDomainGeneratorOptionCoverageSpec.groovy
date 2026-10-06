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
package org.grails.orm.hibernate.cfg.domainbinding.jpa

import grails.gorm.annotation.Entity
import jakarta.persistence.AccessType
import jakarta.persistence.CascadeType
import jakarta.persistence.EnumType
import jakarta.persistence.FetchType
import org.hibernate.MappingException
import org.hibernate.annotations.ColumnDefault
import org.hibernate.annotations.ColumnTransformer
import org.hibernate.annotations.Comment
import org.hibernate.mapping.Column

import org.grails.datastore.mapping.config.AuditMetadataType
import org.grails.orm.hibernate.cfg.CacheConfig
import org.grails.orm.hibernate.cfg.ColumnConfig
import org.grails.orm.hibernate.cfg.Mapping
import org.grails.orm.hibernate.cfg.NaturalId
import org.grails.orm.hibernate.cfg.PropertyConfig
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.GrailsHibernatePersistentEntity
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateBasicProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernatePersistentProperty
import org.grails.orm.hibernate.cfg.domainbinding.hibernate.HibernateToManyEntityProperty

/**
 * Guards the rule that {@link GrailsDomainGenerator} never drops a mapping option without a word. Every option of
 * {@code PropertyConfig}, its {@code ColumnConfig}, its cache, join table and index column, and of {@code Mapping} is switched
 * on, on a property of each kind (a simple property, a number, an enum, a to-one association, an embedded object, and four kinds
 * of collection), and the classes the generator writes are compared with the ones it wrote before: the option must change them,
 * or the generator must reject the entity.
 *
 * <p>{@link #MUST_HAVE_EFFECT} lists, for each option, the kinds of property where the domain binder reads it, so the generator must
 * react. {@link #NEVER_HAS_EFFECT} lists the options that change nothing on any kind, with the reason: nothing in the domain binder
 * reads them, they only matter at run time, or the metamodel consumes them before the generator runs. An option that appears in
 * neither list is not meaningful for the kind and may be ignored there. Anything the binder reads and the generator ignores
 * is a silent drop and fails here.</p>
 */
class GrailsDomainGeneratorOptionCoverageSpec extends GrailsDomainGeneratorSupport {

    private static final List<String> KINDS = ['basic', 'number', 'mode', 'target', 'home', 'tags', 'modes', 'targets', 'ordered']

    private static final List<String> SIMPLE = ['basic', 'number', 'mode']

    private static final List<String> COLLECTIONS = ['tags', 'modes', 'targets', 'ordered']

    private static final List<String> VALUE_COLLECTIONS = ['tags', 'modes', 'ordered']

    private static final List<String> ENTITY_ASSOCIATIONS = ['target', 'targets']

    /** Option to the kinds where the binder reads it: the generator must change its output or reject the entity there. */
    private static final Map<String, List<String>> MUST_HAVE_EFFECT = [
            'property.nullable'            : ['basic', 'number', 'mode'],
            'property.lazy'                : ['basic', 'number', 'mode', 'target', 'home', 'tags', 'modes', 'targets'],
            'property.fetchStrategy'       : ['target', 'tags', 'modes', 'targets', 'ordered'],
            'property.cascade'             : ENTITY_ASSOCIATIONS,
            'property.ignoreNotFound'      : ['target'],
            'property.maxSize'             : ['basic'],
            'property.inList'              : ['basic'],
            'property.max'                 : ['number'],
            'property.min'                 : ['number'],
            'property.scale'               : ['number'],
            'property.unique'              : ['basic', 'number', 'target', 'tags', 'modes', 'targets', 'ordered'],
            'property.uniqueGroup'         : ['basic', 'number', 'mode', 'target', 'tags', 'modes', 'targets', 'ordered'],
            'property.insertable'          : KINDS,
            'property.updatable'           : KINDS,
            'property.type'                : ['basic'],
            'property.typeClass'           : ['basic', 'number', 'mode', 'home', 'tags', 'modes', 'targets', 'ordered'],
            'property.derived'             : ['basic', 'number', 'mode', 'target'],
            'property.cache'               : COLLECTIONS,
            'property.batchSize'           : COLLECTIONS,
            'property.sort'                : ['targets'],
            'property.indexColumn'         : ['ordered'],
            'property.joinTable.name'      : COLLECTIONS,
            'property.joinTable.schema'    : COLLECTIONS,
            'property.joinTable.catalog'   : COLLECTIONS,
            'property.joinTable.keys'      : COLLECTIONS,
            'property.joinTable.column'    : COLLECTIONS,
            'column.name'                  : KINDS - ['home'],
            'column.sqlType'               : KINDS - ['home'],
            'column.enumType'              : ['mode', 'modes'],
            'column.index'                 : KINDS - ['home'],
            'column.unique'                : KINDS - ['home'],
            'column.length'                : KINDS - ['home'],
            'column.precision'             : KINDS - ['home'],
            'column.scale'                 : KINDS - ['home'],
            'column.defaultValue'          : KINDS - ['home'],
            'column.comment'               : KINDS - ['home'],
            'column.read'                  : KINDS - ['home'],
            'column.write'                 : KINDS - ['home'],
            'mapping.autoImport'           : ['mapping'],
            'mapping.batchSize'            : ['mapping'],
            'mapping.cache'                : ['mapping'],
            'mapping.comment'              : ['mapping'],
            'mapping.dynamicInsert'        : ['mapping'],
            'mapping.dynamicUpdate'        : ['mapping'],
            'mapping.table.name'           : ['mapping'],
            'mapping.table.schema'         : ['mapping'],
            'mapping.table.catalog'        : ['mapping'],
            'mapping.identity.natural'     : ['mapping'],
            'mapping.userTypes'            : ['mapping'],
    ]

    /** Options that change nothing anywhere, and why that is no silent drop. */
    private static final Map<String, String> NEVER_HAS_EFFECT = [
            'property.accessType'          : 'run time: how the entity instance is read and written, which a generated metadata class never does',
            'property.auditMetadataType'   : 'no binder class reads it',
            'property.cascadeValidate'     : 'validation only: no binder class reads it',
            'property.cascades'            : 'no binder class reads it: the cascade is the cascade string',
            'property.enumType'            : 'the property-level field is dead: the enum style is read from the column config',
            'property.generator'           : 'only the identifier reads it, through the identity mapping',
            'property.index'               : 'the boolean is dead: only the index of the column config is read',
            'property.minSize'             : 'validation only: the binder reads maxSize',
            'property.name'                : 'no binder class reads it: the property name is the one of the class',
            'property.order'               : 'only meaningful together with sort, which is checked with property.sort',
            'property.orphanRemoval'       : 'no binder class reads it: orphan removal is the all-delete-orphan cascade',
            'property.reference'           : 'no binder class reads it',
            'property.storedAs'            : 'no binder class reads it',
            'property.targetName'          : 'no binder class reads it',
            'property.typeParams'          : 'only meaningful together with a type, which is checked with property.type',
            'property.formula'             : 'the metamodel turns a formula into the derived flag before the generator runs',
            'mapping.autowire'             : 'run time: Spring autowiring of the instance',
            'mapping.stateless'            : 'run time: the session kind',
            'mapping.autoTimestamp'        : 'run time: the timestamp listener',
            'mapping.datasources'          : 'run time: which datastore holds the entity',
            'mapping.defaultSort'          : 'run time: the default order of queries',
            'mapping.sort.name'            : 'run time: the default order of queries',
    ]

    void setupSpec() {
        manager.registerDomainClasses(GenCovTarget, GenCovOwner, GenCovEnumColumn)
    }

    void "every option the binder reads changes what the generator writes, or the generator rejects the entity"() {
        given:
        Map<String, Map<String, String>> verdicts = verdicts()

        expect: "an option the binder reads on a kind of property is never dropped"
        MUST_HAVE_EFFECT.collectMany { String option, List<String> kinds ->
            kinds.findAll { String kind -> verdicts[option][kind] == 'ignored' }.collect { String kind -> "${option} on ${kind}".toString() }
        } == []
    }

    void "no mutation breaks the generator with something other than a rejection"() {
        given:
        Map<String, Map<String, String>> verdicts = verdicts()

        expect:
        verdicts.collectMany { String option, Map<String, String> byKind ->
            byKind.findAll { String kind, String verdict -> verdict.startsWith('error') }.collect { String kind, String verdict ->
                "${option} on ${kind}: ${verdict}".toString()
            }
        } == []
    }

    void "the options listed as having no effect have none, on any kind of property"() {
        given:
        Map<String, Map<String, String>> verdicts = verdicts()

        expect:
        NEVER_HAS_EFFECT.keySet().collectMany { String option ->
            verdicts[option].findAll { String kind, String verdict -> verdict != 'ignored' }.collect { String kind, String verdict ->
                "${option} on ${kind}: ${verdict}".toString()
            }
        } == []
    }

    void "every option that was tried is accounted for"() {
        given:
        Set<String> tried = verdicts().keySet()

        expect:
        (tried - MUST_HAVE_EFFECT.keySet() - NEVER_HAS_EFFECT.keySet()) == [] as Set
        (MUST_HAVE_EFFECT.keySet() + NEVER_HAS_EFFECT.keySet() - tried) == [] as Set
    }

    void "an enum column states the comment, default and read and write expressions that the binder forgets"() {
        when:
        Class<?> generated = generateGroup(GenCovEnumColumn).values().first()
        Column read = annotationMetadata([generated]).getEntityBinding(generated.name).getProperty('mode').columns[0] as Column

        then:
        generated.getDeclaredField('mode').getAnnotation(Comment).value() == 'the mode'
        generated.getDeclaredField('mode').getAnnotation(ColumnDefault).value() == "'A'"
        generated.getDeclaredField('mode').getAnnotation(ColumnTransformer).read() == 'lower(mode)'
        generated.getDeclaredField('mode').getAnnotation(ColumnTransformer).write() == 'upper(?)'
        read.comment == 'the mode'
        read.defaultValue == "'A'"
        read.customRead == 'lower(mode)'
        read.customWrite == 'upper(?)'
    }

    void "an insertable or updatable flag on a property with no column is rejected by name"() {
        when:
        generateGroup(GenCovOwner, GenCovTarget)
        entity(GenCovOwner).getHibernatePropertyByName(property).hibernateMappedForm.insertable = false
        generateGroup(GenCovOwner, GenCovTarget)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('GenCovOwner')
        e.message.contains(property)
        e.message.contains('insertable: false or updatable: false')

        cleanup:
        entity(GenCovOwner).getHibernatePropertyByName(property).hibernateMappedForm.insertable = true

        where:
        property << ['home', 'tags', 'targets']
    }

    void "a user type mapped on an embedded property is rejected by name, because the binder binds it as a simple value"() {
        given:
        PropertyConfig config = entity(GenCovOwner).getHibernatePropertyByName('home').hibernateMappedForm
        config.type = GenUpperType

        when:
        generateGroup(GenCovOwner, GenCovTarget)

        then:
        UnsupportedOperationException e = thrown()
        e.message.contains('GenCovOwner')
        e.message.contains('home')
        e.message.contains('GenUpperType')

        cleanup:
        config.type = null
    }

    private Map<String, Map<String, String>> verdicts

    private Map<String, Map<String, String>> verdicts() {
        if (verdicts == null) {
            verdicts = computeVerdicts()
        }
        return verdicts
    }

    private Map<String, Map<String, String>> computeVerdicts() {
        Map<String, Map<String, String>> result = [:].withDefault { [:] }
        String base = signature()
        for (String kind : KINDS) {
            PropertyConfig config = entity(GenCovOwner).getHibernatePropertyByName(kind).hibernateMappedForm
            propertyMutations().each { String option, List<Closure> variants ->
                result["property.${option}".toString()][kind] = verdict(base, config, variants)
            }
            columnMutations().each { String option, List<Closure> variants ->
                boolean added = config.columns.isEmpty()
                if (added) {
                    config.columns << new ColumnConfig()
                }
                result["column.${option}".toString()][kind] = verdict(base, config.columns[0], variants)
                if (added) {
                    config.columns.clear()
                }
            }
        }
        Mapping mapping = entity(GenCovOwner).hibernateMappedForm
        mappingMutations().each { String option, List<Closure> variants ->
            result["mapping.${option}".toString()]['mapping'] = verdict(base, mapping, variants)
        }
        return result
    }

    private String verdict(String base, Object target, List<Closure> variants) {
        Set<String> found = []
        for (Closure variant : variants) {
            Closure restore = variant.call(target)
            try {
                found << (signature() == base ? 'ignored' : 'effect')
            } catch (UnsupportedOperationException | MappingException ignored) {
                // the generator's own rejection, or the binder's own validation that the generator runs: loud either way
                found << 'effect'
            } catch (Exception e) {
                found << "error ${e.getClass().simpleName}: ${e.message}".toString()
            } finally {
                restore.call()
            }
        }
        return found.find { String verdict -> verdict.startsWith('error') } ?: (found.contains('effect') ? 'effect' : 'ignored')
    }

    private static Closure set(String name, Object value) {
        return { Object target ->
            Object old = target.getProperty(name)
            target.setProperty(name, value)
            return { target.setProperty(name, old) }
        }
    }

    private static Closure change(Closure apply, Closure undo) {
        return { Object target ->
            apply.call(target)
            return { undo.call(target) }
        }
    }

    private static Map<String, List<Closure>> propertyMutations() {
        return [
                'nullable'         : [set('nullable', false), set('nullable', true)],
                'lazy'             : [set('lazy', Boolean.TRUE), set('lazy', Boolean.FALSE)],
                'fetchStrategy'    : [set('fetchStrategy', FetchType.EAGER), set('fetchStrategy', FetchType.LAZY)],
                'cascade'          : [set('cascade', 'all'), set('cascade', 'none'), set('cascade', 'all-delete-orphan')],
                'ignoreNotFound'   : [set('ignoreNotFound', true)],
                'maxSize'          : [set('maxSize', 7)],
                'minSize'          : [set('minSize', 3)],
                'inList'           : [set('inList', ['10', '200'])],
                'max'              : [set('max', new BigInteger('1' + '0' * 49))],
                'min'              : [set('min', new BigInteger('-1' + '0' * 49))],
                'scale'            : [set('scale', 3)],
                'unique'           : [set('unique', true)],
                'uniqueGroup'      : [change({ PropertyConfig c -> c.setUnique(['basic']) },
                        { PropertyConfig c -> c.uniquenessGroup.clear(); c.setUnique(false) })],
                'insertable'       : [set('insertable', false)],
                'updatable'        : [set('updatable', false)],
                'type'             : [set('type', 'text')],
                'typeClass'        : [set('type', GenUpperType)],
                'typeParams'       : [set('typeParams', new Properties())],
                'derived'          : [change({ PropertyConfig c -> c.formula = 'x'; c.derived = true },
                        { PropertyConfig c -> c.formula = null; c.derived = false })],
                'formula'          : [set('formula', 'x')],
                'cache'            : [change({ PropertyConfig c -> c.cache = new CacheConfig(enabled: true) }, { PropertyConfig c -> c.cache = null })],
                'batchSize'        : [set('batchSize', 5)],
                'sort'             : [set('sort', 'label')],
                'order'            : [set('order', 'desc')],
                'indexColumn'      : [change({ PropertyConfig c -> c.indexColumn = new PropertyConfig(columns: [new ColumnConfig(name: 'ic')]) },
                        { PropertyConfig c -> c.indexColumn = null })],
                'joinTable.name'   : [change({ PropertyConfig c -> c.joinTable.name = 'jt' }, { PropertyConfig c -> c.joinTable.name = null })],
                'joinTable.schema' : [change({ PropertyConfig c -> c.joinTable.schema = 'sc' }, { PropertyConfig c -> c.joinTable.schema = null })],
                'joinTable.catalog': [change({ PropertyConfig c -> c.joinTable.catalog = 'ca' }, { PropertyConfig c -> c.joinTable.catalog = null })],
                'joinTable.keys'   : [change({ PropertyConfig c -> c.joinTable.keys = [new ColumnConfig(name: 'k')] }, { PropertyConfig c -> c.joinTable.keys = [] })],
                'joinTable.column' : [change({ PropertyConfig c -> c.joinTable.column = new ColumnConfig(name: 'e') }, { PropertyConfig c -> c.joinTable.column = null })],
                'accessType'       : [set('accessType', AccessType.FIELD)],
                'auditMetadataType': [set('auditMetadataType', AuditMetadataType.values()[0])],
                'cascadeValidate'  : [set('cascadeValidate', 'x')],
                'cascades'         : [set('cascades', [CascadeType.ALL])],
                'enumType'         : [change({ PropertyConfig c -> c.setEnumType('ordinal') }, { PropertyConfig c -> c.setEnumType((EnumType) null) })],
                'generator'        : [set('generator', 'x')],
                'index'            : [set('index', true)],
                'name'             : [set('name', 'x')],
                'orphanRemoval'    : [set('orphanRemoval', true)],
                'reference'        : [set('reference', true)],
                'storedAs'         : [set('storedAs', String)],
                'targetName'       : [set('targetName', 'x')],
        ]
    }

    private static Map<String, List<Closure>> columnMutations() {
        return [
                'name'        : [set('name', 'x_col')],
                'sqlType'     : [set('sqlType', 'varchar(10)')],
                'enumType'    : [set('enumType', 'ordinal')],
                'index'       : [set('index', 'x_idx')],
                'unique'      : [set('unique', true)],
                'length'      : [set('length', 7)],
                'precision'   : [set('precision', 9)],
                'scale'       : [set('scale', 2)],
                'defaultValue': [set('defaultValue', '1')],
                'comment'     : [set('comment', 'c')],
                'read'        : [set('read', 'r')],
                'write'       : [set('write', 'w')],
        ]
    }

    private static Map<String, List<Closure>> mappingMutations() {
        return [
                'autoImport'      : [set('autoImport', false)],
                'batchSize'       : [set('batchSize', 4)],
                'cache'           : [change({ Mapping m -> m.cache = new CacheConfig(enabled: true) }, { Mapping m -> m.cache = null })],
                'comment'         : [set('comment', 'c')],
                'dynamicInsert'   : [set('dynamicInsert', true)],
                'dynamicUpdate'   : [set('dynamicUpdate', true)],
                'table.name'      : [change({ Mapping m -> m.table.name = 't' }, { Mapping m -> m.table.name = null })],
                'table.schema'    : [change({ Mapping m -> m.table.schema = 's' }, { Mapping m -> m.table.schema = null })],
                'table.catalog'   : [change({ Mapping m -> m.table.catalog = 'c' }, { Mapping m -> m.table.catalog = null })],
                'identity.natural': [change({ Mapping m -> m.identity.natural = new NaturalId(propertyNames: ['basic']) },
                        { Mapping m -> m.identity.natural = null })],
                'userTypes'       : [change({ Mapping m -> m.userTypes[String] = 'text' }, { Mapping m -> m.userTypes.clear() })],
                'autowire'        : [set('autowire', true)],
                'stateless'       : [set('stateless', true)],
                'autoTimestamp'   : [set('autoTimestamp', false)],
                'datasources'     : [set('datasources', ['other'])],
                'defaultSort'     : [set('defaultSort', 'x')],
                'sort.name'       : [change({ Mapping m -> m.sort.name = 'x' }, { Mapping m -> m.sort.name = null })],
        ]
    }

    /**
     * What the generator decides for the group: the generated classes (annotations and fields) and the facets that no annotation
     * carries but the binding of the generated classes applies to the bound collections, such as an extra-lazy collection.
     */
    private String signature() {
        Map<GrailsHibernatePersistentEntity, Class<?>> classes = generateGroup(GenCovOwner, GenCovTarget)
        GrailsDomainGenerator generator = newGenerator()
        return classes.collect { GrailsHibernatePersistentEntity entity, Class<?> generated ->
            generated.declaredAnnotations.collect { it.toString() }.join('|') + '#' +
                    generated.declaredFields.sort { it.name }.collect { java.lang.reflect.Field field ->
                        "${field.name}:${field.genericType}:${field.declaredAnnotations.collect { it.toString() }.join(',')}".toString()
                    }.join(';') + '#' + extraLazyCollections(generator, entity)
        }.join('\n')
    }

    private static String extraLazyCollections(GrailsDomainGenerator generator, GrailsHibernatePersistentEntity entity) {
        return entity.hibernatePersistentProperties.findAll { HibernatePersistentProperty property ->
            if (property instanceof HibernateBasicProperty && generator.supports(property)) {
                return generator.collectionFacets((HibernateBasicProperty) property).extraLazy()
            }
            return property instanceof HibernateToManyEntityProperty && generator.supports(property) &&
                    generator.toManyFacets((HibernateToManyEntityProperty) property).extraLazy()
        }*.name.sort().join(',')
    }
}

enum GenCovMode {
    A, B
}

@Entity
class GenCovTarget {

    String label
}

@Entity
class GenCovOwner {

    String basic
    Integer number
    GenCovMode mode
    GenCovTarget target
    GenCovHome home
    Set<String> tags
    Set<GenCovMode> modes
    Set<GenCovTarget> targets
    List<String> ordered

    static embedded = ['home']
    static hasMany = [tags: String, modes: GenCovMode, targets: GenCovTarget, ordered: String]
}

@Entity
class GenCovEnumColumn {

    GenCovMode mode

    static mapping = {
        mode comment: 'the mode', defaultValue: "'A'", read: 'lower(mode)', write: 'upper(?)'
    }
}

class GenCovHome {

    String street
}
