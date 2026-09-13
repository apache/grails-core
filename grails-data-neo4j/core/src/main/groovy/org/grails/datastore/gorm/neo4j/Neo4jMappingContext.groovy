/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package org.grails.datastore.gorm.neo4j

import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.springframework.core.convert.ConversionService

import grails.neo4j.Relationship
import org.grails.datastore.gorm.neo4j.connections.Neo4jConnectionSourceSettings
import org.grails.datastore.gorm.neo4j.identity.SnowflakeIdGenerator
import org.grails.datastore.gorm.neo4j.proxy.HashcodeEqualsAwareProxyFactory
import org.grails.datastore.gorm.neo4j.proxy.Neo4jProxyFactory
import org.grails.datastore.mapping.model.AbstractMappingContext
import org.grails.datastore.mapping.model.MappingConfigurationStrategy
import org.grails.datastore.mapping.model.MappingFactory
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.config.GormMappingConfigurationStrategy
import org.grails.datastore.mapping.proxy.ProxyFactory

/**
 * A {@link org.grails.datastore.mapping.model.MappingContext} implementation for Neo4j
 *
 * @author Stefan Armbruster (stefan@armbruster-it.de)
 * @author Graeme Rocher
 *
 * @since 1.0
 */
@CompileStatic
class Neo4jMappingContext extends AbstractMappingContext {

    private static final Class LONG_TYPE = long
    private static final Class DOUBLE_TYPE = double
    private static final Class STRING_TYPE = String
    public static final Set<Class> BASIC_TYPES = Collections.unmodifiableSet(new HashSet<Class>([
        STRING_TYPE,
        Long,
        Float,
        Integer,
        Double,
        Short,
        Boolean,
        Byte,
        // primitives
        byte,
        int,
        LONG_TYPE,
        float,
        DOUBLE_TYPE,
        short,
        boolean,
        // primitive arrays
        byte[],
        int[],
        long[],
        float[],
        double[],
        short[],
        boolean[],
        String[]
    ] as List<Class>))
    protected Map<Iterable<String>, GraphPersistentEntity> entitiesByLabel = new LinkedHashMap<>()
    // default id generator strategy is native
    protected IdGenerator idGenerator = null
    protected SnowflakeIdGenerator snowflakeIdGenerator = null
    @PackageScope GraphGormMappingFactory mappingFactory = new GraphGormMappingFactory()
    @PackageScope MappingConfigurationStrategy mappingSyntaxStrategy = new GormMappingConfigurationStrategy(mappingFactory)

    Neo4jMappingContext() {
        super()
    }

    @Deprecated
    Neo4jMappingContext(Closure defaultMapping) {
        super()
        setProxyFactory(new HashcodeEqualsAwareProxyFactory())
        mappingFactory.setDefaultMapping(defaultMapping)
    }

    @Deprecated
    Neo4jMappingContext(Closure defaultMapping, Class... classes) {
        super()
        mappingFactory.setDefaultMapping(defaultMapping)
        addPersistentEntities(classes)
    }

    Neo4jMappingContext(Neo4jConnectionSourceSettings settings, Class... classes) {
        super()
        mappingFactory.setDefaultMapping(settings.getDefault().getMapping())
        mappingFactory.setDefaultConstraints(settings.getDefault().getConstraints())
        initialize(settings)
        addPersistentEntities(classes)
    }

    IdGenerator getIdGenerator() {
        return idGenerator
    }

    SnowflakeIdGenerator getSnowflakeIdGenerator() {
        if (snowflakeIdGenerator == null) {
            snowflakeIdGenerator = new SnowflakeIdGenerator()
        }
        return snowflakeIdGenerator
    }

    @Override
    MappingConfigurationStrategy getMappingSyntaxStrategy() {
        return mappingSyntaxStrategy
    }

    @Override
    MappingFactory getMappingFactory() {
        return mappingFactory
    }

    @Override
    protected PersistentEntity createPersistentEntity(Class javaClass) {
        return createPersistentEntity(javaClass, false)
    }

    @Override
    protected PersistentEntity createPersistentEntity(Class javaClass, boolean external) {
        final GraphPersistentEntity entity = Relationship.isAssignableFrom(javaClass) ?
            new RelationshipPersistentEntity(javaClass, this, external) :
            new GraphPersistentEntity(javaClass, this, external)
        final Collection<String> labels = entity.getLabels()
        entitiesByLabel.put(labels, entity)
        return entity
    }

    /**
     * Finds an entity for the statically mapped set of labels
     *
     * @param labels The labels
     * @return The entity or null if it is not found
     */
    GraphPersistentEntity findPersistentEntityForLabels(Iterable<String> labels) {
        final GraphPersistentEntity entity = entitiesByLabel.get(labels)
        return entity
    }

    /**
     * Obtain the native type to use for the given value
     *
     * @param value The value
     * @return The value converted to a native Neo4j type
     */
    Object convertToNative(Object value) {
        if (value != null) {
            final Class<?> type = value.getClass()
            if (type.equals(byte[])) {
                // workaround for https://github.com/neo4j/neo4j-java-driver/issues/182, remove when fixed
                byte[] bytes = (byte[]) value
                int[] intArray = new int[bytes.length]
                for (int i = 0; i < bytes.length; i++) {
                    byte b = bytes[i]
                    intArray[i] = b
                }
                return intArray
            } else if (BASIC_TYPES.contains(type)) {
                return value
            } else if (value instanceof CharSequence) {
                return value.toString()
            } else if (value instanceof Collection) {
                return value
            } else if (value instanceof BigDecimal) {
                return ((BigDecimal) value).doubleValue()
            } else {
                final ConversionService conversionService = getConversionService()
                if (value instanceof byte[]) {
                    return conversionService.convert(value, String)
                } else {
                    if (conversionService.canConvert(type, LONG_TYPE)) {
                        return conversionService.convert(value, LONG_TYPE)
                    } else if (conversionService.canConvert(type, DOUBLE_TYPE)) {
                        return conversionService.convert(value, DOUBLE_TYPE)
                    } else if (conversionService.canConvert(type, STRING_TYPE)) {
                        return conversionService.convert(value, STRING_TYPE)
                    } else {
                        return value.toString()
                    }
                }
            }
        } else {
            return value
        }
    }

    @Override
    ProxyFactory getProxyFactory() {
        if (!(this.@proxyFactory instanceof Neo4jProxyFactory)) {
            this.@proxyFactory = new Neo4jProxyFactory()
        }
        return this.@proxyFactory
    }

}
