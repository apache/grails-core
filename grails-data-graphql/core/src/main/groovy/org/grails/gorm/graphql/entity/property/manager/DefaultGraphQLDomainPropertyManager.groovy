package org.grails.gorm.graphql.entity.property.manager

import groovy.transform.CompileStatic
import org.grails.datastore.mapping.config.Property
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.gorm.graphql.GraphQLEntityHelper
import org.grails.gorm.graphql.entity.dsl.GraphQLMapping
import org.grails.gorm.graphql.entity.dsl.GraphQLPropertyMapping
import org.grails.gorm.graphql.entity.property.GraphQLDomainProperty
import org.grails.gorm.graphql.entity.property.impl.CustomGraphQLProperty
import org.grails.gorm.graphql.entity.property.impl.PersistentGraphQLProperty

import java.lang.reflect.Method

/**
 * A class to retrieve {@link PersistentProperty} instances in combination
 * with a {@link GraphQLMapping} to produce a list of {@link GraphQLDomainProperty}
 * instances used in creation of the GraphQL schema.
 *
 * @author James Kleeh
 * @since 1.0.0
 */
@CompileStatic
class DefaultGraphQLDomainPropertyManager implements GraphQLDomainPropertyManager {

    //To support older versions of GORM
    private static Method derivedMethod
    static {
        try {
            derivedMethod = Property.getMethod('isDerived', (Class<?>[]) null)
        } catch (NoSuchMethodException | SecurityException e) { }
    }

    @Override
    Builder builder() {
        new Builder()
    }

    private static class Builder implements GraphQLDomainPropertyManager.Builder {
        Set<String> excludedProperties = [] as Set
        boolean identifiers = true
        boolean compositeIdentifiers = true
        Closure customCondition = null
        boolean overrideNullable = false

        @Override
        Builder excludeIdentifiers(boolean exceptComposite = false) {
            this.identifiers = false
            this.compositeIdentifiers = exceptComposite
            this
        }

        @Override
        Builder excludeVersion() {
            excludedProperties.add('version')
            this
        }

        @Override
        Builder excludeTimestamps() {
            excludedProperties.addAll(['dateCreated', 'lastUpdated'])
            this
        }

        @Override
        Builder exclude(String... props) {
            excludedProperties.addAll(props)
            this
        }

        @Override
        Builder condition(Closure closure) {
            this.customCondition = closure
            this
        }

        @Override
        Builder alwaysNullable() {
            this.overrideNullable = true
            this
        }

        @Override
        List<GraphQLDomainProperty> getProperties(PersistentEntity entity) {
            getProperties(entity, GraphQLEntityHelper.getMapping(entity))
        }

        private GraphQLPropertyMapping getPropertyMapping(PersistentProperty property, GraphQLMapping mapping, boolean id = false) {
            GraphQLPropertyMapping propertyMapping
            if (mapping.propertyMappings.containsKey(property.name)) {
                propertyMapping = mapping.propertyMappings.get(property.name)
            }
            else {
                propertyMapping = new GraphQLPropertyMapping()
            }

            if (overrideNullable) {
                propertyMapping.nullable(true)
            }
            else if (id && propertyMapping.nullable == null) {
                propertyMapping.nullable(false)
            }

            if (derivedMethod != null) {
                Property prop = property.mapping.mappedForm
                if (derivedMethod.invoke(prop, (Object[]) null)) {
                    propertyMapping.input(false)
                }
            }
            propertyMapping
        }

        @Override
        List<GraphQLDomainProperty> getProperties(PersistentEntity entity, GraphQLMapping mapping) {
            List<GraphQLDomainProperty> properties = []
            MappingContext mappingContext = entity.mappingContext
            if (mapping == null) {
                mapping = new GraphQLMapping()
            }

            if (identifiers) {
                if (entity.identity != null) {
                    properties.add(new PersistentGraphQLProperty(mappingContext, entity.identity, getPropertyMapping(entity.identity, mapping)))
                }
            }

            if (compositeIdentifiers) {
                if (entity.compositeIdentity != null) {
                    for (PersistentProperty prop: entity.compositeIdentity) {
                        properties.add(
                            new PersistentGraphQLProperty(mappingContext, prop, getPropertyMapping(prop, mapping))
                        )
                    }
                }
            }

            for (PersistentProperty prop: entity.persistentProperties) {
                if (mapping.excluded.contains(prop.name)) {
                    continue
                }
                if (excludedProperties.contains(prop.name)) {
                    continue
                }
                if (customCondition != null && !customCondition.call(prop)) {
                    continue
                }
                if (prop.name == 'version' && !entity.versioned) {
                    continue
                }

                properties.add(new PersistentGraphQLProperty(mappingContext, prop, getPropertyMapping(prop, mapping)))
            }

            for (CustomGraphQLProperty property: mapping.additional) {
                CustomGraphQLProperty prop
                if (overrideNullable && !property.nullable) {
                    prop = (CustomGraphQLProperty)property.clone().nullable(true)
                }
                else {
                    prop = property
                }
                prop.mappingContext = mappingContext
                properties.add(prop)
            }

            properties.sort(true)
            properties
        }
    }

}
