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
package org.grails.spring

import org.springframework.beans.BeanWrapper
import org.springframework.beans.BeanWrapperImpl
import org.springframework.beans.PropertyValue
import org.springframework.beans.factory.config.AutowireCapableBeanFactory
import org.springframework.beans.factory.config.BeanDefinition
import org.springframework.beans.factory.config.ConstructorArgumentValues
import org.springframework.beans.factory.config.RuntimeBeanReference
import org.springframework.beans.factory.support.AbstractBeanDefinition
import org.springframework.beans.factory.support.GenericBeanDefinition
import org.springframework.context.annotation.Lazy
import org.springframework.util.Assert

import groovy.transform.CompileStatic

/**
 * Default implementation of the BeanConfiguration interface .
 *
 * Credit must go to Solomon Duskis and the
 * article: http://jroller.com/page/Solomon?entry=programmatic_configuration_in_spring
 *
 * @author Graeme
 * @since 0.3
 */
// Whole-class @CompileStatic: every method body here is plain typed Java-equivalent logic (only
// getProperty()/setProperty() override GroovyObjectSupport for the builder DSL). Annotating only
// those two override methods left calls like setParent() from within setProperty() still routed
// through Groovy's indy machinery -- the static compiler, with the enclosing class left dynamic,
// keeps sibling calls going through the metaclass to preserve possible overrides -- which hit a
// Groovy indy/reflective call-site NPE ("MetaClass.initialize() because mc is null") when
// setProperty() itself is invoked cold via reflection (as it is, being a GroovyObject override)
// and then makes that further dynamic self-call. External dynamic callers (BeanBuilder, the
// property-assignment DSL) are unaffected: Groovy always routes `obj.prop = x` through the
// overridden getProperty()/setProperty() below regardless of this class's own compile mode.
@CompileStatic
class DefaultBeanConfiguration extends GroovyObjectSupport implements BeanConfiguration {

    private static final String AUTOWIRE = 'autowire'
    private static final String SINGLETON = 'singleton'
    private static final String CONSTRUCTOR_ARGS = 'constructorArgs'
    private static final String DESTROY_METHOD = 'destroyMethod'
    private static final String FACTORY_BEAN = 'factoryBean'
    private static final String FACTORY_METHOD = 'factoryMethod'
    private static final String INIT_METHOD = 'initMethod'
    private static final String BY_NAME = 'byName'
    private static final String PARENT = 'parent'
    private static final String BY_TYPE = 'byType'
    private static final String BY_CONSTRUCTOR = 'constructor'
    private static final List<String> DYNAMIC_PROPS = Arrays.asList(
        AUTOWIRE,
        CONSTRUCTOR_ARGS,
        DESTROY_METHOD,
        FACTORY_BEAN,
        FACTORY_METHOD,
        INIT_METHOD,
        BY_NAME,
        BY_TYPE,
        BY_CONSTRUCTOR)

    private String parentName

    @Override
    Object getProperty(String property) {
        @SuppressWarnings('unused')
        AbstractBeanDefinition bd = getBeanDefinition()
        if (wrapper.isReadableProperty(property)) {
            return wrapper.getPropertyValue(property)
        }
        if (DYNAMIC_PROPS.contains(property)) {
            return null
        }
        return super.getProperty(property)
    }

    @Override
    void setProperty(String property, Object newValue) {
        if (PARENT.equals(property)) {
            this.setParent(newValue)
            return
        }

        AbstractBeanDefinition bd = getBeanDefinition()
        if (AUTOWIRE.equals(property)) {
            if (BY_NAME.equals(newValue)) {
                bd.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_NAME)
            }
            else if (BY_TYPE.equals(newValue)) {
                bd.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_TYPE)
            }
            else if (Boolean.TRUE.equals(newValue)) {
                bd.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_BY_NAME)
            }
            else if (BY_CONSTRUCTOR.equals(newValue)) {
                bd.setAutowireMode(AutowireCapableBeanFactory.AUTOWIRE_CONSTRUCTOR)
            }
        }
        // constructorArgs
        else if (CONSTRUCTOR_ARGS.equals(property) && newValue instanceof List<?>) {
            ConstructorArgumentValues cav = new ConstructorArgumentValues()
            for (Object e in (List<?>) newValue) {
                cav.addGenericArgumentValue(e)
            }
            bd.setConstructorArgumentValues(cav)
        }
        // destroyMethod
        else if (DESTROY_METHOD.equals(property)) {
            if (newValue != null) {
                bd.setDestroyMethodName(newValue.toString())
            }
        }
        // factoryBean
        else if (FACTORY_BEAN.equals(property)) {
            if (newValue != null) {
                bd.setFactoryBeanName(newValue.toString())
            }
        }
        // factoryMethod
        else if (FACTORY_METHOD.equals(property)) {
            if (newValue != null) {
                bd.setFactoryMethodName(newValue.toString())
            }
        }
        // initMethod
        else if (INIT_METHOD.equals(property)) {
            if (newValue != null) {
                bd.setInitMethodName(newValue.toString())
            }
        }
        // singleton property
        else if (SINGLETON.equals(property)) {
            bd.setScope(Boolean.TRUE.equals(newValue) ? BeanDefinition.SCOPE_SINGLETON : BeanDefinition.SCOPE_PROTOTYPE)
        }
        else if (wrapper.isWritableProperty(property)) {
            wrapper.setPropertyValue(property, newValue)
        }
        // autowire
        else {
            super.setProperty(property, newValue)
        }
    }

    private Class<?> clazz
    private String name
    private boolean singleton = true
    private AbstractBeanDefinition definition
    private Collection<?> constructorArgs = Collections.emptyList()
    private BeanWrapper wrapper

    DefaultBeanConfiguration(String name, Class<?> clazz) {
        this.name = name
        this.clazz = clazz
    }

    DefaultBeanConfiguration(String name, Class<?> clazz, boolean prototype) {
        this(name, clazz, Collections.emptyList())
        singleton = !prototype
    }

    DefaultBeanConfiguration(String name) {
        this(name, null, Collections.emptyList())
    }

    DefaultBeanConfiguration(Class<?> clazz2) {
        clazz = clazz2
    }

    DefaultBeanConfiguration(String name2, Class<?> clazz2, Collection<?> args) {
        name = name2
        clazz = clazz2
        constructorArgs = args
    }

    DefaultBeanConfiguration(String name2, boolean prototype) {
        this(name2, null, Collections.emptyList())
        singleton = !prototype
    }

    DefaultBeanConfiguration(Class<?> clazz2, Collection<?> constructorArguments) {
        clazz = clazz2
        constructorArgs = constructorArguments
    }

    String getName() {
        return name
    }

    boolean isSingleton() {
        return singleton
    }

    AbstractBeanDefinition getBeanDefinition() {
        if (definition == null) {
            definition = createBeanDefinition()
        }
        return definition
    }

    void setBeanDefinition(BeanDefinition definition) {
        this.definition = (AbstractBeanDefinition) definition
    }

    protected AbstractBeanDefinition createBeanDefinition() {
        AbstractBeanDefinition bd = new GenericBeanDefinition()
        if (!constructorArgs.isEmpty()) {
            ConstructorArgumentValues cav = new ConstructorArgumentValues()
            for (Object constructorArg in constructorArgs) {
                cav.addGenericArgumentValue(constructorArg)
            }
            bd.setConstructorArgumentValues(cav)
        }
        if (clazz != null) {
            bd.setLazyInit(clazz.getAnnotation(Lazy) != null)
            bd.setBeanClass(clazz)
        }
        bd.setScope(singleton ? AbstractBeanDefinition.SCOPE_SINGLETON : AbstractBeanDefinition.SCOPE_PROTOTYPE)
        if (parentName != null) {
            bd.setParentName(parentName)
        }
        wrapper = new BeanWrapperImpl(bd)
        return bd
    }

    BeanConfiguration addProperty(String propertyName, Object propertyValue) {
        if (propertyValue instanceof BeanConfiguration) {
            propertyValue = ((BeanConfiguration) propertyValue).getBeanDefinition()
        }
        getBeanDefinition()
            .getPropertyValues()
            .addPropertyValue(propertyName, propertyValue)

        return this
    }

    BeanConfiguration setDestroyMethod(String methodName) {
        getBeanDefinition().setDestroyMethodName(methodName)
        return this
    }

    BeanConfiguration setDependsOn(String[] dependsOn) {
        getBeanDefinition().setDependsOn(dependsOn)
        return this
    }

    BeanConfiguration setFactoryBean(String beanName) {
        getBeanDefinition().setFactoryBeanName(beanName)
        return this
    }

    BeanConfiguration setFactoryMethod(String methodName) {
        getBeanDefinition().setFactoryMethodName(methodName)
        return this
    }

    BeanConfiguration setAutowire(String type) {
        if ('byName'.equals(type)) {
            getBeanDefinition().setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_NAME)
        }
        else if ('byType'.equals(type)) {
            getBeanDefinition().setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE)
        }
        return this
    }

    void setName(String beanName) {
        name = beanName
    }

    Object getPropertyValue(String propName) {
        PropertyValue propertyValue = getBeanDefinition()
            .getPropertyValues()
            .getPropertyValue(propName)
        if (propertyValue == null) {
            return null
        }

        return propertyValue.getValue()
    }

    boolean hasProperty(String propName) {
        return getBeanDefinition().getPropertyValues().contains(propName)
    }

    void setPropertyValue(String property, Object newValue) {
        getBeanDefinition().getPropertyValues().addPropertyValue(property, newValue)
    }

    BeanConfiguration setAbstract(boolean isAbstract) {
        getBeanDefinition().setAbstract(isAbstract)
        return this
    }

    void setParent(Object obj) {
        Assert.notNull(obj, 'Parent bean cannot be set to a null runtime bean reference!')

        if (obj instanceof String) {
            parentName = (String) obj
        }
        else if (obj instanceof RuntimeBeanReference) {
            parentName = ((RuntimeBeanReference) obj).getBeanName()
        }
        else if (obj instanceof BeanConfiguration) {
            parentName = ((BeanConfiguration) obj).getName()
        }
        getBeanDefinition().setParentName(parentName)
        setAbstract(false)
    }

}
