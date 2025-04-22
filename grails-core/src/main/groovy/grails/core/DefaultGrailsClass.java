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
package grails.core;

import org.grails.core.AbstractGrailsClass;

/**
 * A default implementation for Grails classes that need to be registered and managed by a GrailsApplication,
 * but don't need any special handling.
 *
 * @author Graeme Rocher
 * @since 0.5
 */
public class DefaultGrailsClass extends AbstractGrailsClass {

    /**
     * Contructor to be used by all child classes to create a
     * new instance and get the name right.
     *
     * @param clazz        the Grails class
     * @param trailingName the trailing part of the name for this class type
     */
    public DefaultGrailsClass(Class<?> clazz, String trailingName) {
        super(clazz, trailingName);
    }

    public DefaultGrailsClass(Class<?> clazz) {
        super(clazz, "");
    }
}
