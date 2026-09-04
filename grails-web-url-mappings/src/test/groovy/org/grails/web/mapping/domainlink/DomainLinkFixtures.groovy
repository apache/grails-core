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
package org.grails.web.mapping.domainlink

import grails.artefact.Artefact

class Person {
    Long id
    String name
}

class Widget {
    Long id
    String name
}

class Gadget {
    Long id
    String name
}

class Note {
    Long id
    String body
}

/**
 * Stands in for a generic REST controller base class. Deliberately not a Grails artefact and not
 * named after any framework type: the domain class is resolved from the generic type argument, so
 * any generic base class must work.
 */
class ResourceControllerBase<T> {
}

/**
 * A non-generic intermediate that binds the type argument, so a controller extending it declares no
 * generics of its own. Exercises resolution through more than one level of the hierarchy.
 */
abstract class WidgetControllerBase extends ResourceControllerBase<Widget> {
}

/**
 * The only controller for {@code Person}, and named for the plural resource rather than the domain
 * class, which is the case a {@code resource} link cannot resolve by name alone.
 */
@Artefact('Controller')
class PeopleController extends ResourceControllerBase<Person> {
    def index() {}
    def show() {}
}

/**
 * The only controller for {@code Widget}, reaching its domain class through {@link WidgetControllerBase}.
 */
@Artefact('Controller')
class WidgetsController extends WidgetControllerBase {
    def index() {}
    def show() {}
}

/**
 * One of two controllers declaring {@code Gadget}, making the domain class ambiguous.
 */
@Artefact('Controller')
class GadgetsController extends ResourceControllerBase<Gadget> {
    def index() {}
    def show() {}
}

/**
 * The second controller declaring {@code Gadget}.
 */
@Artefact('Controller')
class AdminGadgetsController extends ResourceControllerBase<Gadget> {
    def index() {}
    def show() {}
}

/**
 * A controller that declares no domain class, so resolution falls back to the domain class name.
 */
@Artefact('Controller')
class NoteController {
    def index() {}
    def show() {}
}

class Chapter {
    Long id
    String title
}

/**
 * A plain controller named after {@code Chapter}, alongside {@link ChapterApiController} which declares
 * the domain class. The naming convention has to win, or an application that already relies on it has
 * its links silently retargeted.
 */
@Artefact('Controller')
class ChapterController {
    def index() {}
    def show() {}
}

@Artefact('Controller')
class ChapterApiController extends ResourceControllerBase<Chapter> {
    def index() {}
    def show() {}
}

class Tag {
    Long id
    String label
}

/**
 * Stands in for a generic REST trait. Groovy traits compile to an interface, so a domain class declared
 * this way is reachable only by walking interfaces rather than superclasses.
 */
interface ResourceHolder<T> {
}

@Artefact('Controller')
class TagsController implements ResourceHolder<Tag> {
    def index() {}
    def show() {}
}

class Chronicle {
    Long id
    String name
}

/**
 * A base parameterised on both a parent and a child resource, so its own type arguments are ambiguous
 * and the domain class has to come from further up the hierarchy.
 */
abstract class NestedResourceControllerBase<P, T> extends ResourceControllerBase<T> {
}

@Artefact('Controller')
class ChroniclesController extends NestedResourceControllerBase<Person, Chronicle> {
    def index() {}
    def show() {}
}
