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

import org.codehaus.groovy.runtime.metaclass.MethodSelectionException

/**
 * Not public API. Used by GormEnhancer
 */
@SuppressWarnings("rawtypes")
@CompileStatic
abstract class MethodInvokingClosure extends Closure {
    protected String methodName
    protected apiDelegate
    protected Class[] parameterTypes
    protected MetaMethod metaMethod
    
    MethodInvokingClosure(apiDelegate, String methodName, Class[] parameterTypes) {
        super(apiDelegate, apiDelegate)
        this.apiDelegate = apiDelegate
        this.methodName = methodName
        this.parameterTypes = parameterTypes
    }

    @Override
    abstract Object call(Object[] args);

    Object doCall(Object[] args) {
        call(args)
    }

    @Override
    Class[] getParameterTypes() { parameterTypes }

    @Override
    int getMaximumNumberOfParameters() {
        parameterTypes.length
    }
    
    /**
     * Utility method for choosing matching metamethod, handles MethodSelectionException 
     * 
     * 
     * @param theMetaClass
     * @param methodName
     * @param parameterTypes
     * @param staticScope
     * @return
     */
    protected static MetaMethod pickMetaMethod(final MetaClass theMetaClass, final String methodName, final Class[] parameterTypes, boolean staticScope) {
        try {
            return theMetaClass.pickMethod(methodName, parameterTypes)
        } catch (MethodSelectionException mse) {
            // the metamethod already exists with multiple signatures, pick the most specific
            return theMetaClass.methods.find { MetaMethod existingMethod ->
                existingMethod.name == methodName && existingMethod.isStatic()==staticScope && ((!parameterTypes && !existingMethod.parameterTypes) || parameterTypes==existingMethod.parameterTypes)
            }
        }
    }
}