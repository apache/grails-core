/* Copyright (C) 2013 SpringSource
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
package org.grails.datastore.gorm.internal

import groovy.transform.CompileStatic


/**
 * Not public API. Used by GormEnhancer
 */
@SuppressWarnings("rawtypes")
@CompileStatic
class InstanceMethodInvokingClosure extends MethodInvokingClosure {
    InstanceMethodInvokingClosure(apiDelegate, Class<?> persistentClass, String methodName, Class[] parameterTypes) {
        super(apiDelegate, methodName, parameterTypes)
        List<Class> params = parameterTypes.toList()
        params.add(persistentClass)

        Class[] metaMethodParams = params.toArray() as Class[]
        super.metaMethod = pickMetaMethod(apiDelegate.getMetaClass(), methodName, metaMethodParams, false)
    }

    @Override
    Object call(Object[] args) {
        def delegateArg = Collections.singletonList(delegate).toArray()
        Object[] arguments
        if(args) {
            def argList = []
            argList.add(delegate)
            argList.addAll(Arrays.asList(args))
            arguments = argList.toArray()
        }
        else {
            arguments = delegateArg
        }
        metaMethod.invoke(apiDelegate, arguments)
    }
}