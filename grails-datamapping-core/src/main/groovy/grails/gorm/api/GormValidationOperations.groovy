/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */
package grails.gorm.api

import groovy.transform.CompileStatic
import org.springframework.validation.Errors

/**
 * Validation methods of the GORM API.
 *
 * @author Graeme Rocher
 * @param <D> the entity/domain class
 */
@CompileStatic
interface GormValidationOperations<D> {

    /**
     * Validates an instance
     *
     * @param instance The instance to validate
     * @return True if the instance is valid
     */
    boolean validate(D instance)

    /**
     * Validates an instance for the given arguments
     *
     * @param instance The instance to validate
     * @param arguments The arguments to use
     * @return True if the instance is valid
     */
    boolean validate(D instance, Map arguments)

    /**
     * Validates an instance
     *
     * @param instance The instance to validate
     * @param fields The list of fields to validate
     * @return True if the instance is valid
     */
    boolean validate(D instance, List fields)

    /**
     * Obtains the errors for an instance
     * @param instance The instance to obtain errors for
     * @return The {@link Errors} instance
     */
    Errors getErrors(D instance)

    /**
     * Sets the errors for an instance
     * @param instance The instance
     * @param errors The errors
     */
    void setErrors(D instance, Errors errors)

    /**
     * Clears any errors that exist on an instance
     * @param instance The instance
     */
    void clearErrors(D instance)

    /**
     * Tests whether an instance has any errors
     * @param instance The instance
     * @return True if errors exist
     */
    boolean hasErrors(D instance)
}
