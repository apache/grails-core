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
package org.grails.openapi

import java.lang.reflect.Method
import java.lang.reflect.Parameter

import groovy.transform.CompileStatic

import org.springframework.context.ApplicationContext
import org.springframework.core.GenericTypeResolver

import grails.core.GrailsApplication
import grails.core.GrailsClass
import grails.core.GrailsControllerClass
import grails.rest.RestfulController
import org.grails.core.artefact.ControllerArtefactHandler

/**
 * The controllers of an application, as a description reads them: by the name and namespace a
 * mapping reaches them by, and by what they serve.
 */
@CompileStatic
class ControllerCatalog {

    private static final String RESPONSE_FORMATS = 'responseFormats'
    private static final String ALLOWED_METHODS = 'allowedMethods'
    private static final String HTML_FORMAT = 'html'
    private static final String ID = 'id'

    private final GrailsApplication grailsApplication
    private final Map<String, GrailsControllerClass> byKey = [:]
    private final Map<String, List<GrailsControllerClass>> byName = [:]
    private final Map<Class<?>, Object> instances = [:]
    private final Map<Class<?>, Class<?>> boundResources = [:]

    ControllerCatalog(GrailsApplication grailsApplication) {
        this.grailsApplication = grailsApplication
        if (grailsApplication == null) {
            return
        }
        for (GrailsClass artefact : grailsApplication.getArtefacts(ControllerArtefactHandler.TYPE)) {
            if (artefact instanceof GrailsControllerClass) {
                GrailsControllerClass controller = (GrailsControllerClass) artefact
                byKey[key(controller.namespace, controller.logicalPropertyName)] = controller
                byName.computeIfAbsent(controller.logicalPropertyName) { [] }.add(controller)
            }
        }
    }

    /**
     * @return every controller of the application
     */
    Collection<GrailsControllerClass> getControllers() {
        byKey.values()
    }

    /**
     * The controller a mapping dispatches to. A mapping that names a namespace reaches that
     * namespace's controller; one that names none reaches the controller without a namespace, or
     * the only controller of that name.
     */
    GrailsControllerClass controllerFor(String name, String namespace) {
        GrailsControllerClass exact = byKey[key(namespace, name)]
        if (exact != null || namespace) {
            return exact
        }
        List<GrailsControllerClass> named = byName[name]
        named?.size() == 1 ? named.first() : null
    }

    /**
     * The formats an action responds in, read as {@code respond} reads them: a list declared for
     * every action, or the list a map declares for the action.
     *
     * @return the declared formats, or null where the controller declares none for the action, and
     * it responds in any format the application configures
     */
    List<String> responseFormats(GrailsControllerClass controller, String actionName) {
        Object declared = controller?.getPropertyValue(RESPONSE_FORMATS)
        Object formats = declared instanceof Map ? ((Map) declared).get(actionName) : declared
        formats instanceof List ? ((List) formats).findAll().collect { Object format -> format.toString() } : null
    }

    /**
     * The HTTP methods a controller allows, by action.
     */
    Object allowedMethods(GrailsControllerClass controller) {
        controller?.getPropertyValue(ALLOWED_METHODS)
    }

    /**
     * A controller a mapping reaching controllers by name describes an action of: a
     * RestfulController, or one declaring the formats an action responds in, as a REST controller
     * does, none of them HTML, rather than one rendering views for a browser.
     */
    boolean isRestController(GrailsControllerClass controller) {
        RestfulController.isAssignableFrom(controller.clazz) || controller.actions.any { String action ->
            isRestAction(controller, action)
        }
    }

    /**
     * An action a mapping reaching controllers by name describes: one of a RestfulController, or
     * one its controller declares the formats of, none of them HTML. Where {@code responseFormats} is
     * a map, each action is decided by its own entry.
     */
    boolean isRestAction(GrailsControllerClass controller, String actionName) {
        if (RestfulController.isAssignableFrom(controller.clazz)) {
            return true
        }
        List<String> formats = responseFormats(controller, actionName)
        formats && !formats.contains(HTML_FORMAT)
    }

    /**
     * Whether a controller serves a resource the way a RestfulController does: it is one, or its
     * save and update actions bind the same domain class, as the controllers the rest-api profile
     * generates do.
     */
    boolean isResourceController(GrailsControllerClass controller) {
        controller != null && (RestfulController.isAssignableFrom(controller.clazz) || boundResource(controller) != null)
    }

    /**
     * The type a resource controller serves: the type argument a RestfulController declares, or
     * the resource it was constructed with, or the domain class a generated controller binds.
     */
    Class<?> resourceType(GrailsControllerClass controller) {
        if (!RestfulController.isAssignableFrom(controller.clazz)) {
            return boundResource(controller)
        }
        Class<?> declared = GenericTypeResolver.resolveTypeArgument(controller.clazz, RestfulController)
        if (declared != null && declared != Object) {
            return declared
        }
        Object instance = instance(controller)
        instance instanceof RestfulController ? ((RestfulController) instance).resource : null
    }

    /**
     * Whether a RestfulController was constructed read only, which is decided by its constructor
     * rather than declared on the class, so is read from the controller itself.
     */
    boolean isReadOnly(GrailsControllerClass controller) {
        Object instance = controller != null ? instance(controller) : null
        instance instanceof RestfulController && ((RestfulController) instance).readOnly
    }

    /**
     * Whether an action addresses one resource, and so takes the identifier a mapping may leave
     * optional: the actions of a resource controller that address one, or an action declaring an
     * {@code id} parameter.
     */
    boolean takesId(GrailsControllerClass controller, String actionName) {
        if (isResourceController(controller)) {
            return RestfulControllerActions.takesId(actionName)
        }
        Method action = ActionAnnotations.actionMethod(controller.clazz, actionName)
        action != null && action.parameters.any { Parameter parameter -> parameter.name == ID }
    }

    /**
     * The controller the application context serves requests with, if it holds one.
     */
    Object instance(GrailsControllerClass controller) {
        if (!instances.containsKey(controller.clazz)) {
            instances[controller.clazz] = lookUp(controller)
        }
        instances[controller.clazz]
    }

    private Class<?> boundResource(GrailsControllerClass controller) {
        if (!boundResources.containsKey(controller.clazz)) {
            Class<?> saved = ActionAnnotations.commandObjectType(controller.clazz, 'save')
            Class<?> updated = ActionAnnotations.commandObjectType(controller.clazz, 'update')
            boundResources[controller.clazz] = saved != null && saved == updated && GrailsModelConverter.entityFor(saved) != null
                    ? saved : null
        }
        boundResources[controller.clazz]
    }

    /**
     * Grails registers a controller under its class name, which a subclass controller does not
     * share, where a lookup by type would find both. A controller in a scope other than singleton
     * would be created for the lookup, so it is not looked up.
     */
    private Object lookUp(GrailsControllerClass controller) {
        ApplicationContext context = grailsApplication?.mainContext
        if (context == null) {
            return null
        }
        try {
            String name = context.containsBean(controller.fullName)
                    ? controller.fullName
                    : context.getBeanNamesForType(controller.clazz).find()
            return name != null && context.isSingleton(name) ? context.getBean(name) : null
        }
        catch (RuntimeException ignored) {
            return null
        }
    }

    private static String key(String namespace, String name) {
        "${namespace ?: ''}:${name}".toString()
    }
}
