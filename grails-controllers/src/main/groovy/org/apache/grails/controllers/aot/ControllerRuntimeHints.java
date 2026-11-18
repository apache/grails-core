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
package org.apache.grails.controllers.aot;

import java.security.Principal;

import org.jspecify.annotations.Nullable;

import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

import grails.artefact.Controller;
import grails.artefact.controller.support.AllowedMethodsHelper;
import grails.artefact.controller.support.RequestForwarder;
import grails.artefact.controller.support.ResponseRedirector;
import grails.artefact.controller.support.ResponseRenderer;

/**
 * Registers the controller API a request is dispatched through.
 *
 * <p>A controller action reaches these through Groovy's dynamic dispatch, which reads a type's
 * declared methods to choose an overload. An ahead-of-time image keeps only the members something
 * asks for, so without these hints the methods are absent and dispatch fails at the point of use --
 * on the request that first takes that path, rather than at start-up.</p>
 *
 * <p>Recording this here rather than leaving it to a tracing agent matters because the agent only
 * ever sees the paths a developer happened to exercise: the method check below is reached only by
 * POST, PUT and DELETE, so a walk of an application's pages never records it and the failure
 * appears the first time someone submits a form.</p>
 *
 * @since 8.0
 */
public class ControllerRuntimeHints implements RuntimeHintsRegistrar {

    /** What a hinted type is registered for: the members Groovy reads to choose an overload. */
    private static final MemberCategory[] DISPATCHED_MEMBERS = {
        MemberCategory.INVOKE_DECLARED_METHODS,
        MemberCategory.INVOKE_PUBLIC_METHODS,
        MemberCategory.INVOKE_DECLARED_CONSTRUCTORS
    };

    /**
     * Types Groovy dispatches on that this module declares itself, so they are named as types. A
     * rename is then a compile error here rather than a hint that quietly stops matching -- which
     * would show up as a failed dispatch on the first request that took that path.
     */
    private static final Class<?>[] DISPATCHED_TYPES = {
        Controller.class,
        AllowedMethodsHelper.class,
        RequestForwarder.class,
        ResponseRedirector.class,
        ResponseRenderer.class,
        // a view asking who is logged in reaches the request's principal, and Groovy makes that
        // call on the interface rather than the implementation the container supplies
        Principal.class
    };

    /**
     * Types contributed by a module this one does not depend on. Named as strings and registered
     * only when present, so this stays correct for an application that does not use every plugin.
     */
    private static final String[] OPTIONAL_DISPATCHED_TYPES = {
        "grails.artefact.controller.RestResponder"
    };

    @Override
    public void registerHints(RuntimeHints hints, @Nullable ClassLoader classLoader) {
        for (Class<?> type : DISPATCHED_TYPES) {
            hints.reflection().registerType(type, DISPATCHED_MEMBERS);
        }
        for (String type : OPTIONAL_DISPATCHED_TYPES) {
            hints.reflection().registerTypeIfPresent(classLoader, type, DISPATCHED_MEMBERS);
        }
    }
}
