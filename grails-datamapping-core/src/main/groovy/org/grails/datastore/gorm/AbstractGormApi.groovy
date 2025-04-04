/* Copyright (C) 2010 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm

import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import groovy.transform.TypeChecked
import groovy.transform.TypeCheckingMode
import org.grails.datastore.mapping.model.MappingContext

import java.lang.reflect.Method
import java.lang.reflect.Modifier

import org.grails.datastore.gorm.utils.ReflectionUtils
import org.grails.datastore.mapping.core.Datastore
import org.grails.datastore.mapping.model.PersistentEntity

/**
 * Abstract GORM API provider.
 *
 * @author Graeme Rocher
 * @param <D> the entity/domain class
 * @since 1.0
 */
@CompileStatic
abstract class AbstractGormApi<D> extends AbstractDatastoreApi {

    static final List<String> EXCLUDES = [
        'setProperty',
        'getProperty',
        'getMetaClass',
        'setMetaClass',
        'invokeMethod',
        'getMethods',
        'getExtendedMethods',
        'wait',
        'equals',
        'toString',
        'hashCode',
        'getClass',
        'notify',
        'notifyAll',
        'setTransactionManager'
    ]

    protected Class<D> persistentClass
    protected PersistentEntity persistentEntity
    private List<Method> methods
    private List<Method> extendedMethods

    AbstractGormApi(Class<D> persistentClass, Datastore datastore) {
        super(datastore)
        this.persistentClass = persistentClass
        this.persistentEntity = datastore.getMappingContext().getPersistentEntity(persistentClass.name)
    }

    AbstractGormApi(Class<D> persistentClass, MappingContext mappingContext) {
        super(null)
        this.persistentClass = persistentClass
        this.persistentEntity = mappingContext.getPersistentEntity(persistentClass.name)
    }

    @CompileDynamic
    protected initializeMethods(clazz) {
        while (clazz != Object) {
            final methodsToAdd = clazz.declaredMethods.findAll { Method m ->
                def mods = m.getModifiers()
                !m.isSynthetic() && !Modifier.isStatic(mods) && Modifier.isPublic(mods) &&
                        !AbstractGormApi.EXCLUDES.contains(m.name)
            }
            methods.addAll methodsToAdd
            if (clazz != GormStaticApi.class && clazz != GormInstanceApi && clazz != GormValidationApi && clazz != AbstractGormApi) {
                def extendedMethodsToAdd = methodsToAdd.findAll { Method m -> !ReflectionUtils.isMethodOverriddenFromParent(m)}
                extendedMethods.addAll extendedMethodsToAdd
            }
            clazz = clazz.getSuperclass()
        }
        return clazz
    }

    List<Method> getMethods() {
        if(methods == null) {
            initializeMethods(getClass())
        }
        return methods
    }

    List<Method> getExtendedMethods() {
        if(extendedMethods == null) {
            initializeMethods(getClass())
        }
        return extendedMethods
    }
}
