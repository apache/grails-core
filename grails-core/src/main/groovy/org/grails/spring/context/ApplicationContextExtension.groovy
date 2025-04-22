/*
 * Copyright 2014 original authors
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
package org.grails.spring.context

import groovy.transform.CompileStatic
import org.springframework.context.ApplicationContext



/**
 * A Groovy extension module that adds additional methods to the Spring {@link org.springframework.context.ApplicationContext} interface
 *
 * @author Graeme Rocher
 * @since 3.0
 */
@CompileStatic
class ApplicationContextExtension {

    /**
     * Adds the ability to get beans via the dot operator
     *
     * @param applicationContext The ApplicationContext instance
     * @param name The bean name
     * @return
     */
    public static Object propertyMissing(ApplicationContext applicationContext, String name ) {
        if(applicationContext.containsBean(name)) {
            return applicationContext.getBean(name);
        }
        else {
            return null;
        }
    }

    /**
     * Adds the ability to use the subscript operator to obtain beans
     *
     * @param applicationContext The ApplicationContext instance
     * @param name The bean name
     * @return A bean or null
     */
    public static Object getAt(ApplicationContext applicationContext, String name) {
        return propertyMissing(applicationContext, name);
    }
}
