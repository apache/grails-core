/*
 * Copyright 2004-2005 Graeme Rocher
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
package grails.util;

import groovy.util.ObjectGraphBuilder;

import java.lang.reflect.Field;
import java.util.Collection;

import org.codehaus.groovy.runtime.InvokerHelper;

/**
 * <p>Allows the construction of object graphs of domain classes. Example:
 *
 * <pre><code>
 *      def builder = new DomainBuilder()

        def company = builder.company(name: 'ACME') {
            employee(name: 'Duke', employeeId: 1) {
                address(street: '123 Groovy Rd')
            }
            employee(name: 'George', employeeId: 2)
        }
 * </code></pre>
 * @author Scott Vlaminck
 */
public class DomainBuilder extends ObjectGraphBuilder {

    public DomainBuilder() {
        setChildPropertySetter(new DefaultGrailsChildPropertySetter());
        setClassLoader(Thread.currentThread().getContextClassLoader());
    }

    public static class DefaultGrailsChildPropertySetter implements ChildPropertySetter {
        public void setChild(Object parent, Object child, String parentName, String propertyName) {
            if (isCollection(parent, child, parentName, propertyName)) {
                String propName = propertyName.substring(0,1).toUpperCase() + propertyName.substring(1);
                String methodName = "addTo" + propName;
                InvokerHelper.invokeMethod(parent, methodName, child);
            }
            else {
                InvokerHelper.setProperty(parent, propertyName, child);
            }
        }

        private boolean isCollection(Object parent, Object child, String parentName, String propertyName) {
            try {
                Field field = parent.getClass().getDeclaredField(propertyName);
                return Collection.class.isAssignableFrom(field.getType());
            }
            catch (NoSuchFieldException ignored) { /* ignored */}

            return false;
        }
    }
}
