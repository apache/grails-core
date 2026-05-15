/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ASF licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  'License'); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    https://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  'AS IS' BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package grails.gorm.validation

/**
 * Enum representing the display behavior for a constrained property in scaffolded views.
 *
 * <p>This enum controls where a property is displayed in generated scaffolding views:</p>
 * <ul>
 *   <li>{@link #ALL} - Display everywhere, including overriding default blacklists (e.g., dateCreated, lastUpdated)</li>
 *   <li>{@link #NONE} - Never display in any view</li>
 *   <li>{@link #INPUT_ONLY} - Display only in input forms (create/edit views)</li>
 *   <li>{@link #OUTPUT_ONLY} - Display only in output views (show/index views)</li>
 * </ul>
 *
 * <p>Example usage in domain class constraints:</p>
 * <pre>
 * import static grails.gorm.validation.DisplayType.*
 *
 * class Book {

 *     String title
 *     Date dateCreated
 *     String internalNotes
 *
 *     static constraints = {
 *         dateCreated display: ALL      // Override blacklist, show everywhere
 *         internalNotes display: NONE   // Never show
 *     }
 * }
 * </pre>
 *
 * <p>For backwards compatibility, boolean values are also supported:</p>
 * <ul>
 *   <li>{@code display: true} is equivalent to the default behavior (not setting display)</li>
 *   <li>{@code display: false} is equivalent to {@link #NONE}</li>
 * </ul>
 *
 * @author Scott Murphy Heiberg
 * @since 7.1
 */
enum DisplayType {

    /**
     * Display the property in all views (input and output).
     * This also overrides the default blacklist for properties like dateCreated and lastUpdated.
     */
    ALL,

    /**
     * Never display the property in any view.
     */
    NONE,

    /**
     * Display the property only in input views (create and edit forms).
     */
    INPUT_ONLY,

    /**
     * Display the property only in output views (show and index/list views).
     */
    OUTPUT_ONLY

}
