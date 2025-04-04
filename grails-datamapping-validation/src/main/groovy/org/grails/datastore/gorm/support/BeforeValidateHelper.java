/* Copyright (C) 2011 SpringSource
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
package org.grails.datastore.gorm.support;

import groovy.lang.MetaClass;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BeforeValidateHelper implements Serializable {
    public static final String BEFORE_VALIDATE = "beforeValidate";
    private transient Map<Class<?>, BeforeValidateEventTriggerCaller> eventTriggerCallerCache = new ConcurrentHashMap<Class<?>, BeforeValidateEventTriggerCaller>();
    
    public static final class BeforeValidateEventTriggerCaller {
        EventTriggerCaller eventTriggerCaller;
        EventTriggerCaller eventTriggerCallerNoArgs;
        
        public BeforeValidateEventTriggerCaller(Class<?> domainClass, MetaClass metaClass) {
            eventTriggerCaller = build(domainClass, metaClass, new Class<?>[]{List.class});
            eventTriggerCallerNoArgs = build(domainClass, metaClass, new Class<?>[]{});
        }
        
        protected EventTriggerCaller build(Class<?> domainClass, MetaClass metaClass, Class<?>[] argumentTypes) {
            return EventTriggerCaller.buildCaller(BEFORE_VALIDATE, domainClass, metaClass, argumentTypes);
        }
        
        public void call(final Object target, final List<?> validatedFieldsList) {
            if(validatedFieldsList != null && eventTriggerCaller != null) {
                eventTriggerCaller.call(target, new Object[]{validatedFieldsList});
            } else if (eventTriggerCallerNoArgs != null) {
                eventTriggerCallerNoArgs.call(target);
            }
        }
    }
    
    public void invokeBeforeValidate(final Object target, final List<?> validatedFieldsList) {
        Class<?> domainClass = target.getClass();
        BeforeValidateEventTriggerCaller eventTriggerCaller = eventTriggerCallerCache.get(domainClass);
        if(eventTriggerCaller==null) {
            eventTriggerCaller = new BeforeValidateEventTriggerCaller(domainClass, null);
            eventTriggerCallerCache.put(domainClass, eventTriggerCaller);
        }
        eventTriggerCaller.call(target, validatedFieldsList);
    }

    // Ensure that the cache is re-initalized empty when deserialized
    private void readObject(ObjectInputStream in) throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        eventTriggerCallerCache = new ConcurrentHashMap<Class<?>, BeforeValidateEventTriggerCaller>();
    }
}