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
package org.grails.web.taglib

import grails.compiler.traits.CompiledTagCallTransformation
import org.apache.grails.common.compiler.GroovyTransformOrder
import org.codehaus.groovy.control.CompilePhase
import org.codehaus.groovy.transform.GroovyASTTransformation
import org.codehaus.groovy.transform.TransformWithPriority
import spock.lang.Specification

/**
 * Whether a class can call tags is only settled once the traits that let it have been applied, so the
 * rewriting has to run after the transforms that apply them.
 *
 * <p>That used to hold by accident: a transform declaring no priority defaults to zero, which happened
 * to place it last. Declaring the order means a transform added later cannot displace it, and this
 * pins the relationship rather than the number.
 */
class CompiledTagCallTransformationOrderSpec extends Specification {

    void 'the transformation declares its order rather than relying on a default'() {
        expect:
        new CompiledTagCallTransformation() instanceof TransformWithPriority
    }

    void 'it runs after the transforms that inject artefact traits'() {
        given: 'the registry decrements, so a later transform has the lower priority'
        int rewriting = new CompiledTagCallTransformation().priority()

        expect: 'the trait a controller calls tags through has been applied by the time this runs'
        rewriting < GroovyTransformOrder.ARTIFACT_TYPE_ORDER
        rewriting < GroovyTransformOrder.GLOBAL_GRAILS_TRANSFORM_ORDER
    }

    void 'it runs after every other transform the registry orders'() {
        given:
        int rewriting = new CompiledTagCallTransformation().priority()

        expect: 'nothing else can introduce a tag-calling class after the rewriting has run'
        rewriting == GroovyTransformOrder.COMPILED_TAG_CALL_ORDER
        rewriting < GroovyTransformOrder.COMMAND_FACTORIES_ORDER
    }

    void 'it runs in a later phase than the transforms that inject artefact traits'() {
        given: 'a trait can arrive from a local transform, which no priority within a phase orders'
        GroovyASTTransformation declared = CompiledTagCallTransformation.getAnnotation(GroovyASTTransformation)

        expect: 'so the class is read in a phase by which every trait has been applied'
        declared.phase() == CompilePhase.INSTRUCTION_SELECTION
    }
}
