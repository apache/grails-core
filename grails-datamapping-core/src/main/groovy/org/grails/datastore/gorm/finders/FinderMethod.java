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
package org.grails.datastore.gorm.finders;

import groovy.lang.Closure;

/**
 * Implementation of dynamic finders.
 */
@SuppressWarnings("rawtypes")
public interface FinderMethod {

    /**
     * @param pattern A regular expression
     */
    void setPattern(String pattern);

    /**
     * Invokes the method
     * @param clazz The class
     * @param methodName The method name
     * @param arguments The arguments
     * @return The return value
     */
    Object invoke(Class clazz, String methodName, Object[] arguments);

    /**
     * Invokes the method
     * @param clazz The class
     * @param methodName The method name
     * @param additionalCriteria additional criteria closure
     * @param arguments The arguments
     * @return The return value
     */
    Object invoke(Class clazz, String methodName, Closure additionalCriteria, Object[] arguments);

    /**
     * Whether the given method name matches this finder
     * @param methodName The method name
     * @return true if it does
     */
    boolean isMethodMatch(String methodName);
}
