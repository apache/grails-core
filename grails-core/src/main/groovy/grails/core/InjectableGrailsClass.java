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
package grails.core;

/**
 * Represents a Grails class that is to be configured in Spring and as such is injectable.
 *
 * @author Steven Devijver
 * @author Graeme Rocher
 * @since 1.0
 */
public interface InjectableGrailsClass extends GrailsClass {

    /**
     * If autowiring by name is enabled.
     *
     * @return autowiring by name
     */
    boolean byName();

    /**
     * If autowiring by type is enabled.
     *
     * @return autowiring by type
     */
    boolean byType();

    /**
     * If class should be configured for dependency injection.
     *
     * @return available for dependency injection
     */
    boolean getAvailable();
}
