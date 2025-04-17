/*
 * Copyright 2012 Rob Fletcher
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package grails.plugin.formfields

import grails.core.GrailsApplication
import grails.core.support.GrailsApplicationAware
import grails.core.support.proxy.ProxyHandler
import grails.gorm.validation.ConstrainedProperty
import grails.gorm.validation.DefaultConstrainedProperty
import grails.validation.Validateable
import groovy.transform.CompileStatic
import groovy.transform.PackageScope
import org.grails.datastore.gorm.validation.constraints.eval.ConstraintsEvaluator
import org.grails.datastore.gorm.validation.constraints.registry.DefaultConstraintRegistry
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.scaffolding.model.property.Constrained
import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.model.property.DomainPropertyFactory
import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl
import org.springframework.beans.PropertyAccessorFactory
import org.springframework.context.support.StaticMessageSource

import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.regex.Matcher
import java.util.regex.Pattern

@CompileStatic
class BeanPropertyAccessorFactory implements GrailsApplicationAware {

    GrailsApplication grailsApplication
    ConstraintsEvaluator constraintsEvaluator
    ProxyHandler proxyHandler
    DomainPropertyFactory fieldsDomainPropertyFactory
    MappingContext grailsDomainClassMappingContext

    BeanPropertyAccessor accessorFor(bean, String propertyPath) {
        if (bean == null) {
            new PropertyPathAccessor(propertyPath)
        } else {
            resolvePropertyFromPath(unwrapIfProxy(bean), propertyPath)
        }
    }

    private PersistentEntity resolveDomainClass(Class beanClass) {
        grailsDomainClassMappingContext.getPersistentEntity(beanClass.name)
    }

    private BeanPropertyAccessor resolvePropertyFromPath(bean, String pathFromRoot) {
        BeanWrapper beanWrapper = PropertyAccessorFactory.forBeanPropertyAccess(bean)
        List<String> pathElements = pathFromRoot.tokenize(".")

        Map<String, Object> params = [rootBean: bean, rootBeanType: bean.getClass(), pathFromRoot: pathFromRoot, grailsApplication: grailsApplication]

        DomainProperty domainProperty = resolvePropertyFromPathComponents(beanWrapper, pathElements, params)

        if (domainProperty != null) {
            new DelegatingBeanPropertyAccessorImpl(bean, params.value, params.propertyType as Class, pathFromRoot, domainProperty, addPathFromRoot)
        } else {
            new BeanPropertyAccessorImpl(params)
        }

    }

    private boolean getAddPathFromRoot() {
		grailsApplication.config.getProperty('grails.plugin.fields.i18n.addPathFromRoot', Boolean)
	}
	
	private DomainProperty resolvePropertyFromPathComponents(BeanWrapper beanWrapper, List<String> pathElements, Map params) {
        String propertyName = pathElements.remove(0)
        PersistentEntity beanClass = resolveDomainClass(beanWrapper.wrappedClass)
        Class propertyType = resolvePropertyType(beanWrapper, beanClass, propertyName)
        Object value = beanWrapper.getPropertyValue(propertyName)
        if (pathElements.empty) {
            params.value = value
            params.propertyType = propertyType

            PersistentProperty persistentProperty
            String nameWithoutIndex = stripIndex(propertyName)
            if (beanClass != null) {
                persistentProperty = beanClass.getPropertyByName(nameWithoutIndex)
                if (!persistentProperty && beanClass.isIdentityName(nameWithoutIndex)) {
                    persistentProperty = beanClass.identity
                }
            }

            if (persistentProperty != null) {
                return fieldsDomainPropertyFactory.build(persistentProperty)
            } else {
                params.entity = beanClass
                params.beanType = beanWrapper.wrappedClass
                params.propertyType = propertyType
                params.propertyName = nameWithoutIndex
                params.domainProperty = null
                params.constraints = resolveConstraints(beanWrapper, params.propertyName as String)
                return null
            }
        } else {
            return resolvePropertyFromPathComponents(beanWrapperFor(propertyType, value), pathElements, params)
        }
    }

    private Constrained resolveConstraints(BeanWrapper beanWrapper, String propertyName) {
        Class<?> type = beanWrapper.wrappedClass
        boolean defaultNullable = Validateable.class.isAssignableFrom(type) ? type.metaClass.invokeStaticMethod(type, 'defaultNullable') : false
        ConstrainedProperty constraint = constraintsEvaluator.evaluate(type, defaultNullable)[propertyName]

        new Constrained(constraint ?: createDefaultConstraint(beanWrapper, propertyName))
    }

    private static ConstrainedProperty createDefaultConstraint(BeanWrapper beanWrapper, String propertyName) {
        new DefaultConstrainedProperty(beanWrapper.wrappedClass, propertyName, beanWrapper.getPropertyType(propertyName), new DefaultConstraintRegistry(new StaticMessageSource())).tap {
            nullable = true
        }
    }

    private static Class resolvePropertyType(BeanWrapper beanWrapper, PersistentEntity beanClass, String propertyName) {
        return resolveDomainPropertyType(beanClass, propertyName) ?: resolveNonDomainPropertyType(beanWrapper, propertyName)
    }

    private static Class resolveDomainPropertyType(PersistentEntity beanClass, String propertyName) {
        if(beanClass) {
            String propertyNameWithoutIndex = stripIndex(propertyName)
            PersistentProperty persistentProperty = beanClass.getPropertyByName(propertyNameWithoutIndex)
            if (!persistentProperty && beanClass.isIdentityName(propertyNameWithoutIndex)) {
                persistentProperty = beanClass.identity
            }
            if (!persistentProperty) {
                return null
            }
            boolean isIndexed = propertyName =~ INDEXED_PROPERTY_PATTERN
            if (isIndexed) {
                if (persistentProperty instanceof Basic) {
                    return (persistentProperty as Basic).componentType
                } else if (persistentProperty instanceof Association) {
                    return (persistentProperty as Association).associatedEntity.javaClass
                }
            } else {
                return persistentProperty.type
            }
        }
        return null
    }

    private static Class<?> resolveNonDomainPropertyType(BeanWrapper beanWrapper, String propertyName) {
        Class<?> type = beanWrapper.getPropertyType(propertyName)
		if (type == null) {
			String match = getPropertyMatch(propertyName)
			if (match) {
                Type genericType = beanWrapper.getPropertyDescriptor(match).readMethod.genericReturnType 
				if (genericType instanceof ParameterizedType) {
                    ParameterizedType parameterizedType = genericType as ParameterizedType
					switch (parameterizedType.rawType) {
						case Collection:
							return parameterizedType.actualTypeArguments[0] as Class
						case Map:
							return parameterizedType.actualTypeArguments[1] as Class
					}
				} else {
					return Object
				}
			}
		}
		return type
	}

    private BeanWrapper beanWrapperFor(Class type, value) {
        value ? PropertyAccessorFactory.forBeanPropertyAccess(unwrapIfProxy(value)) : new BeanWrapperImpl(type)
    }

    private Object unwrapIfProxy(value) {
        return proxyHandler.unwrapIfProxy(value)
    }

    private static final Pattern INDEXED_PROPERTY_PATTERN = ~/^(\w+)\[(.+)]$/

    private static String getPropertyMatch(String propertyName) {
        Matcher matcher = propertyName =~ INDEXED_PROPERTY_PATTERN
        matcher.matches() ? (matcher[0] as String[])[1] : null
    }

    @PackageScope
    static String stripIndex(String propertyName) {
        def matcher = propertyName =~ INDEXED_PROPERTY_PATTERN
        matcher.matches() ? (matcher[0] as String[])[1] : propertyName
    }
    
    
}
