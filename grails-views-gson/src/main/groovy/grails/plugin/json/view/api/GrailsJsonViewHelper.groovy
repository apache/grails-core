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

package grails.plugin.json.view.api

import grails.plugin.json.builder.JsonOutput
import grails.plugin.json.builder.StreamingJsonBuilder
import grails.views.api.GrailsViewHelper
import groovy.transform.CompileStatic

/**
 * Additional methods specific to JSON views
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
interface GrailsJsonViewHelper extends GrailsViewHelper {

    /**
     * The deep parameter
     */
    String DEEP = "deep"

    /**
     * Renders a template and returns the output
     *
     * @param arguments The named arguments: 'template', 'collection', 'model', 'var' and 'bean'
     * @return The unescaped JSON
     */
    JsonOutput.JsonWritable render(Map arguments)

    /**
     * Renders the given object to JSON, typically a domain class, ignoring lazy and internal properties
     *
     * @param object The object to render
     * @param arguments The supported named arguments: 'includes' or 'excludes' list
     * @param customizer Used to customize the contents
     * @return The unescaped JSON
     */
    JsonOutput.JsonWritable render(Object object, Map arguments, @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure customizer)
    /**
     * Renders the given object to JSON, typically a domain class, ignoring lazy and internal properties
     *
     * @param object The object to render
     * @param arguments The supported named arguments: 'includes' or 'excludes' list
     * @return The unescaped JSON
     */
    JsonOutput.JsonWritable render(Object object, Map arguments)

    /**
     * Renders the given object to JSON, typically a domain class, ignoring lazy and internal properties
     *
     * @param object The object to render
     * @return The unescaped JSON
     */
    JsonOutput.JsonWritable render(Object object)

    /**
     * Renders the given object to JSON, typically a domain class, ignoring lazy and internal properties
     *
     * @param object The object to render
     * @param customizer the customizer
     * @return The unescaped JSON
     */
    JsonOutput.JsonWritable render(Object object, @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure customizer)

    /**
     * Renders the given object inline within the current JSON object instead of creating a new JSON object
     *
     * @param object The object to render
     * @param arguments The arguments
     * @param customizer The customizer
     * @param delegate The delegate to use to render the output
     */
    void inline(Object object, Map arguments, @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure customizer, StreamingJsonBuilder.StreamingJsonDelegate delegate)

    /**
     * Renders the given object inline within the current JSON object instead of creating a new JSON object
     *
     * @param object The object to render
     * @param arguments The arguments
     * @param customizer The customizer
     */
    void inline(Object object, Map arguments, @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure customizer)

    /**
     * Renders the given object inline within the current JSON object instead of creating a new JSON object
     *
     * @param object The object to render
     * @param arguments The arguments
     */
    void inline(Object object, Map arguments)
    /**
     * Renders the given object inline within the current JSON object instead of creating a new JSON object
     *
     * @param object The object to render
     * @param customizer The customizer
     */
    void inline(Object object, @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure customizer )

    /**
     * Renders the given object inline within the current JSON object instead of creating a new JSON object
     *
     * @param object The object to render
     */
    void inline(Object object)
}