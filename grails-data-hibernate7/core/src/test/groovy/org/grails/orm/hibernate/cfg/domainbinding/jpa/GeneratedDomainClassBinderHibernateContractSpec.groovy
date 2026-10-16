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

import java.lang.reflect.Constructor
import java.lang.reflect.Method
import java.lang.reflect.Modifier

import org.hibernate.Version
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import spock.lang.Specification

/**
 * A structural guard on the Hibernate classes the generated-class binding uses that are public but sit in {@code internal}
 * packages or are otherwise not part of Hibernate's supported API. Hibernate may change or remove them in any release; this
 * spec turns that into one failure that says what to revisit, instead of a {@code NoSuchMethodError} at application start.
 *
 * <p>This is deliberately the one place that inspects non-public-API signatures: the signatures are the contract. The
 * behaviour they provide is exercised by the generated-mode specs ({@code GeneratedDomainClassesContributorSpec},
 * {@code GeneratedModeEnversSpec} and the rest of the suite).</p>
 */
class GeneratedDomainClassBinderHibernateContractSpec extends Specification {

    private static final Logger LOG = LoggerFactory.getLogger(GeneratedDomainClassBinderHibernateContractSpec)

    /** The Hibernate version the signatures below were verified against. */
    private static final String VERIFIED_AGAINST = '7.4.10'

    private static final String BIND_NOW = 'GeneratedDomainClassBinder.bindNow: it binds the generated classes itself, as ' +
            'AdditionalMappingContributions.complete() does, so that an AdditionalMappingContributor that runs after the binder ' +
            '(Envers) sees the bound entities; revisit it, and how the binder falls back to contributions.contributeEntity'
    private static final String LOADER_SERVICE = 'HibernateMappingContextConfiguration.newClassLoaderService: it subclasses ' +
            'the class loader service to put the GORM binder and the GeneratedDomainClassBinder into the Java services Hibernate ' +
            'loads; revisit how those two are contributed'
    private static final String ALIGN = 'GeneratedDomainClassBinder.align and the methods it calls: after the mappings are ' +
            'bound they re-point the bound entities at the real domain classes and restore what the domain binder would have ' +
            'bound; revisit how the same state is set on the mapping model'

    def setupSpec() {
        String actual = Version.versionString
        LOG.info('GeneratedDomainClassBinder Hibernate contract verified against {}, running against {}', VERIFIED_AGAINST, actual)
        if (!actual.startsWith(VERIFIED_AGAINST)) {
            LOG.warn('Hibernate is {} but the generated-class binding contract was verified against {}: if a feature of ' +
                    'this spec fails, that is the reason', actual, VERIFIED_AGAINST)
        }
    }

    def "the Hibernate version is reported for information only and never fails the contract"() {
        expect: 'a patch upgrade passes as long as the signatures checked by the other features still exist'
        Version.versionString != null
    }

    def "the internal Hibernate method #owner.#member still has the signature the generated-class binding calls"() {
        expect:
        requireMethod(owner, member, parameters, isStatic, revisit)

        where:
        owner                                                                                  | member                      | parameters                                                                                                                                                                                                                                                         | isStatic | revisit
        'org.hibernate.boot.model.source.internal.annotations.AnnotationMetadataSourceProcessorImpl' | 'processAdditionalMappings' | ['java.util.List', 'java.util.List', 'java.util.List', 'org.hibernate.boot.internal.MetadataBuildingContextRootImpl', 'org.hibernate.boot.spi.MetadataBuildingOptions'] | true     | BIND_NOW
        'org.hibernate.boot.internal.InFlightMetadataCollectorImpl'                            | 'processSecondPasses'       | ['org.hibernate.boot.spi.MetadataBuildingContext']                                                                                                                                                                                                                 | false    | BIND_NOW
        'org.hibernate.boot.registry.classloading.internal.ClassLoaderServiceImpl'             | 'loadJavaServices'          | ['java.lang.Class']                                                                                                                                                                                                                                                | false    | LOADER_SERVICE
    }

    def "the classes the generated-class binding tests with instanceof and subclasses are still related as it assumes"() {
        expect: 'bindNow only takes its direct path when the context is the root implementation and the collector the in-flight one'
        isAssignable('org.hibernate.boot.spi.MetadataBuildingContext', 'org.hibernate.boot.internal.MetadataBuildingContextRootImpl', BIND_NOW)
        isAssignable('org.hibernate.boot.spi.InFlightMetadataCollector', 'org.hibernate.boot.internal.InFlightMetadataCollectorImpl', BIND_NOW)
        isAssignable('org.hibernate.boot.spi.MetadataBuildingOptions', typeOf('org.hibernate.boot.spi.MetadataBuildingContext', 'getBuildingOptions'), BIND_NOW)
        isAssignable('org.hibernate.boot.spi.InFlightMetadataCollector', typeOf('org.hibernate.boot.spi.MetadataBuildingContext', 'getMetadataCollector'), BIND_NOW)
    }

    def "the class loader service implementation can still be subclassed and constructed with a class loader"() {
        when:
        Class<?> type = load('org.hibernate.boot.registry.classloading.internal.ClassLoaderServiceImpl', LOADER_SERVICE)
        Constructor<?> constructor = findConstructor(type, [ClassLoader])

        then: 'the binder-mode and generated-mode services are anonymous subclasses created with this constructor'
        !Modifier.isFinal(type.modifiers)
        Modifier.isPublic(type.modifiers)
        constructor != null
        Modifier.isPublic(constructor.modifiers)
    }

    def "the mapping model mutators used to align the bound entities with the real classes still exist"() {
        expect:
        requireMethod(owner, member, parameters, false, ALIGN)

        where:
        owner                                    | member                       | parameters
        'org.hibernate.mapping.PersistentClass'  | 'setClassName'               | ['java.lang.String']
        'org.hibernate.mapping.PersistentClass'  | 'setProxyInterfaceName'      | ['java.lang.String']
        'org.hibernate.mapping.PersistentClass'  | 'getDeclaredProperties'      | []
        'org.hibernate.mapping.PersistentClass'  | 'getRecursiveProperty'       | ['java.lang.String']
        'org.hibernate.mapping.RootClass'        | 'setForceDiscriminator'      | ['boolean']
        'org.hibernate.mapping.Property'         | 'setPropertyAccessorName'    | ['java.lang.String']
        'org.hibernate.mapping.Property'         | 'setLazy'                    | ['boolean']
        'org.hibernate.mapping.Component'        | 'setComponentClassName'      | ['java.lang.String']
        'org.hibernate.mapping.Component'        | 'getProperties'              | []
        'org.hibernate.mapping.Collection'       | 'setExtraLazy'               | ['boolean']
        'org.hibernate.mapping.Collection'       | 'createAllKeys'              | []
        'org.hibernate.mapping.Table'            | 'setPrimaryKey'              | ['org.hibernate.mapping.PrimaryKey']
        'org.hibernate.mapping.UniqueKey'        | 'setNameExplicit'            | ['boolean']
        'org.hibernate.mapping.UniqueKey'        | 'setExplicit'                | ['boolean']
        'org.hibernate.mapping.SimpleValue'      | 'setCustomIdGeneratorCreator' | ['org.hibernate.mapping.GeneratorCreator']
        'org.hibernate.boot.Metadata'            | 'getEntityBindings'          | []
        'org.hibernate.boot.Metadata'            | 'getFilterDefinitions'       | []
        'org.hibernate.boot.spi.MetadataImplementor' | 'getTypeConfiguration'   | []
        'org.hibernate.type.descriptor.java.spi.JavaTypeRegistry' | 'resolveEntityTypeDescriptor' | ['java.lang.Class']
    }

    private static boolean requireMethod(String owner, String member, List<String> parameters, boolean isStatic, String revisit) {
        Class<?> type = load(owner, revisit)
        List<Class<?>> parameterTypes = parameters.collect { String name -> load(name, revisit) }
        Method method
        try {
            method = type.getMethod(member, parameterTypes as Class<?>[])
        } catch (NoSuchMethodException ignored) {
            throw new AssertionError(failure("${owner}.${member}(${parameters.join(', ')}) no longer exists", revisit))
        }
        if (Modifier.isStatic(method.modifiers) != isStatic) {
            throw new AssertionError(failure(
                    "${owner}.${member} is ${isStatic ? 'no longer' : 'now'} static, the binder calls it as " +
                            "${isStatic ? 'a static method' : 'an instance method'}", revisit))
        }
        return true
    }

    private static boolean isAssignable(String supertype, String subtype, String revisit) {
        return isAssignable(supertype, load(subtype, revisit), revisit)
    }

    private static boolean isAssignable(String supertype, Class<?> subtype, String revisit) {
        if (!load(supertype, revisit).isAssignableFrom(subtype)) {
            throw new AssertionError(failure("${subtype.name} is no longer a ${supertype}", revisit))
        }
        return true
    }

    private static Class<?> typeOf(String owner, String getter) {
        return load(owner, 'GeneratedDomainClassBinder').getMethod(getter).returnType
    }

    private static Constructor<?> findConstructor(Class<?> type, List<Class<?>> parameterTypes) {
        try {
            return type.getConstructor(parameterTypes as Class<?>[])
        } catch (NoSuchMethodException ignored) {
            throw new AssertionError(failure("${type.name} has no public constructor taking ${parameterTypes*.simpleName}", LOADER_SERVICE))
        }
    }

    private static Class<?> load(String name, String revisit) {
        if (name == 'boolean') {
            return boolean
        }
        try {
            return Class.forName(name, false, GeneratedDomainClassBinderHibernateContractSpec.classLoader)
        } catch (ClassNotFoundException ignored) {
            throw new AssertionError(failure("class ${name} no longer exists", revisit))
        }
    }

    private static String failure(String what, String revisit) {
        return "Hibernate ${Version.versionString} changed the API the generated-class binding relies on (the contract was " +
                "written against Hibernate ${VERIFIED_AGAINST}): ${what}. Revisit ${revisit}."
    }
}
