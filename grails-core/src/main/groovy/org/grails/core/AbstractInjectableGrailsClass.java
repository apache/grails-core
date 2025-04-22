/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.core;

import grails.core.InjectableGrailsClass;

/**
 * Configures Grails classes to be autowirable by name, always.
 *
 * @author Graeme Rocher
 * @author Steven Devijver
 *
 * @since 0.1
 */
public abstract class AbstractInjectableGrailsClass extends AbstractGrailsClass implements InjectableGrailsClass {

    public AbstractInjectableGrailsClass(Class<?> clazz, String trailingName) {
        super(clazz, trailingName);
    }

    public boolean byName() {
        return true;
    }

    public boolean byType() {
        return false;
    }

    public boolean getAvailable() {
        return true;
    }
}
