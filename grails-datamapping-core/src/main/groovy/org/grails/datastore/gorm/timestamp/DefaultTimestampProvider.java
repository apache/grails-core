/*******************************************************************************
 * Copyright 2014 original authors
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
 *******************************************************************************/
package org.grails.datastore.gorm.timestamp;

import java.util.Date;

import org.codehaus.groovy.runtime.DefaultGroovyMethods;
import org.springframework.util.ClassUtils;

/**
 * Default implementation of TimestampProvider
 * 
 * supports creating timestamps for any class that supports a constructor that accepts a Long or long value.
 * 
 * "currentTimeMillis" can be overrided in subclasses (useful for testing purposes)
 *
 */
public class DefaultTimestampProvider implements TimestampProvider {

    protected long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @Override
    public boolean supportsCreating(Class<?> dateTimeClass) {
        return true;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T createTimestamp(final Class<T> dateTimeClass) {
        long timestampMillis = currentTimeMillis();
        if(dateTimeClass == String.class) {
            return (T)String.valueOf(timestampMillis);
        } else {
            Class<?> actualDateTimeClass;
            if(dateTimeClass==Object.class) {
                actualDateTimeClass = Date.class;
            } else {
                actualDateTimeClass = ClassUtils.resolvePrimitiveIfNecessary(dateTimeClass);
            }
            try {
                return (T)DefaultGroovyMethods.newInstance(actualDateTimeClass, new Object[] { timestampMillis });
            } catch (Exception e) {
                return (T)DefaultGroovyMethods.invokeMethod(actualDateTimeClass, "now", null);
            }
        }
    }
}
