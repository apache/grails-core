/*
 * Copyright 2011 the original author or authors.
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
package org.grails.core.support.internal.tools;

import groovy.lang.MetaClass;
import groovy.lang.MetaClassRegistryChangeEvent;
import groovy.lang.MetaClassRegistryChangeEventListener;

/**
 * Simple class that reports when meta class changes and where (in what stack frame) those changes took place
 *
 * @author Graeme Rocher
 * @since 2.0
 */
public class MetaClassChangeReporter implements MetaClassRegistryChangeEventListener{
    /**
     * Called when the a constant MetaClass is updated. If the new MetaClass is null, then the MetaClass
     * is removed. Be careful, while this method is executed other updates may happen. If you want this
     * method thread safe, you have to take care of that by yourself.
     *
     * @param cmcu - the change event
     */
    public void updateConstantMetaClass(MetaClassRegistryChangeEvent cmcu) {
        Class<?> classToUpdate = cmcu.getClassToUpdate();
        MetaClass newMetaClass = cmcu.getNewMetaClass();

        System.out.println("Class ["+classToUpdate+"] updated MetaClass to ["+newMetaClass+"]");
        Thread.dumpStack();
    }
}
