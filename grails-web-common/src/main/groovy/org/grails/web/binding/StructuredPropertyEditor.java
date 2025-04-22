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
package org.grails.web.binding;

import java.util.List;
import java.util.Map;

/**
 * A PropertyEditor that is able to edit structured properties (properties made up of multiple field values).
 * The #assemble method takes the required type and a map of field values and create an instance of the required type.
 *
 * @since 1.0.4
 * @author Graeme Rocher
 */
@SuppressWarnings("rawtypes")
public interface StructuredPropertyEditor {

    /**
     * @return The required fields
     */
    List getRequiredFields();

    /**
     * @return The optional fields
     */
    List getOptionalFields();

    /**
     * Assemble and bind a property value from the specified fieldValues and the given type
     * @param type The type
     * @param fieldValues The field values
     * @return A bound property
     * @throws IllegalArgumentException Thrown in one of the field values is illegal
     */
    Object assemble(Class type, Map fieldValues) throws IllegalArgumentException;
}
