/* Copyright (C) 2013 SpringSource
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.grails.datastore.gorm.internal

import org.grails.datastore.gorm.GormEnhancer

/**
 * Not public API. Used by GormEnhancer
 */
@SuppressWarnings('rawtypes')
class InstanceMethodInvokingClosure extends MethodInvokingClosure {

    InstanceMethodInvokingClosure(apiDelegate, Class<?> persistentClass, String methodName) {
        super(apiDelegate, persistentClass, methodName)
    }

    @Override
    Object doCall(Object[] args) {
        def activeDelegate = GormEnhancer.findInstanceApi(targetClass)
        Object[] arguments
        if (args) {
            def argList = []
            argList.add(delegate)
            argList.addAll(Arrays.asList(args))
            arguments = argList.toArray()
        }
        else {
            arguments = Collections.singletonList(delegate).toArray()
        }

        def parameterTypes = arguments.collect { it?.getClass() ?: Object } as Class[]
        def metaMethod = pickMetaMethod(activeDelegate.getMetaClass(), methodName, parameterTypes, false)
        if (metaMethod == null) {
            throw new MissingMethodException(methodName, targetClass, args)
        }
        metaMethod.invoke(activeDelegate, arguments)
    }
}
