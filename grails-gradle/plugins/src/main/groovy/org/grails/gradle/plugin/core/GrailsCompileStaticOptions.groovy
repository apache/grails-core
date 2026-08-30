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
package org.grails.gradle.plugin.core

import javax.inject.Inject

import groovy.transform.CompileStatic

import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * Lazy opt-ins for compiling Grails artefacts with {@code @GrailsCompileStatic} automatically,
 * configured through the nested {@code grails { compileStatic { } }} block:
 *
 * <pre>
 * grails {
 *     compileStatic {
 *         controllers = true
 *         services = true
 *         tagLibs = true
 *     }
 * }
 * </pre>
 *
 * <p>Every flag is a lazy {@link Property} that defaults to {@code false} and is read when the Groovy
 * compile task runs (not at configuration time), so the values reflect the user's {@code grails { }}
 * block regardless of configuration ordering. The {@link #getAll() all} flag is a shortcut that enables
 * controllers, services and tag libraries together.</p>
 *
 * <p>A class that declares its own {@code @CompileDynamic} (or {@code @CompileStatic} /
 * {@code @GrailsCompileStatic} / {@code @TypeChecked} / {@code @GrailsTypeChecked}) annotation always
 * keeps that setting; these build-wide opt-ins never override an explicit per-class choice.</p>
 *
 * @since 8.0
 */
@CompileStatic
class GrailsCompileStaticOptions implements Serializable {

    private static final long serialVersionUID = 0L

    /**
     * What every artefact type falls back to where it says nothing of its own. A shortcut for enabling
     * {@link #getControllers() controllers}, {@link #getServices() services} and {@link #getTagLibs()
     * tagLibs} together. Disabled by default.
     *
     * <p>An artefact type that states a value keeps it, so a single type can be held back from the
     * shortcut:</p>
     *
     * <pre>
     * grails {
     *     compileStatic {
     *         all = true
     *         services = false   // every artefact type but services
     *     }
     * }
     * </pre>
     */
    final Property<Boolean> all

    /**
     * Whether every controller under {@code grails-app/controllers} should be compiled with
     * {@code @GrailsCompileStatic}. Follows {@link #getAll() all} where it is not set.
     */
    final Property<Boolean> controllers

    /**
     * Whether every service under {@code grails-app/services} should be compiled with
     * {@code @GrailsCompileStatic}. Follows {@link #getAll() all} where it is not set.
     */
    final Property<Boolean> services

    /**
     * Whether every tag library under {@code grails-app/taglib} should be compiled with
     * {@code @GrailsCompileStatic}. Follows {@link #getAll() all} where it is not set.
     */
    final Property<Boolean> tagLibs

    /**
     * Whether every GSP page under {@code grails-app/views} should be compiled statically, by stating
     * the {@code grails.views.gsp.compileStatic} configuration setting for both the build and the
     * running application. Disabled by default.
     *
     * <p>Deliberately not covered by {@link #getAll() all}. The artefact opt-ins fail on code that is
     * doubtful anyway; this one fails on a page that reads a model variable it has not declared, which
     * describes most pages in an application that has never declared one. Enabling it is a migration,
     * not a switch, so it is never turned on as a side effect of asking for everything.</p>
     */
    final Property<Boolean> gsp

    /**
     * Whether every page is held to the names it declares, rather than only the pages that declare a
     * model. Disabled by default.
     *
     * <p>A page that declares a model is held to it either way: declaring one states what the page is
     * rendered with, so a name outside it is a mistake. This asks for the same of a page that declares
     * nothing, which otherwise reads what it is rendered with the way a dynamically compiled page
     * does. Meaningless unless {@link #getGsp() gsp} is enabled.</p>
     */
    final Property<Boolean> strictGsp

    /**
     * Whether a tag call written without its namespace may be compiled into a direct invocation.
     *
     * <p>Off by default. A namespaced call names the tag library it means; a bare name is a tag only
     * when nothing nearer answers to it, and what answers to it is not fully visible when compiling -
     * a method Groovy gives every object, a delegate an enclosing closure is handed, an overload the
     * tag library also declares. Turn this on to compile those calls too, in a project whose tag
     * names are known not to collide.
     */
    final Property<Boolean> unqualifiedTagCalls

    /**
     * Whether a tag no compiled tag library declares should fail compilation. Disabled by default,
     * where such a tag is left to resolve at runtime with nothing reported.
     *
     * <p>Checked only where the source says a call is a tag: one naming its namespace, as
     * {@code g.message(code: 'x')} does, and one written as markup, as {@code <g:message/>} is. A call
     * written without a namespace is not checked, because such a name may equally be a method
     * contributed by any of the dynamic mechanisms an application has, and in a page it may be part of
     * the model the page was rendered with.
     *
     * <p>Knowing that a namespace holds some compiled tag libraries is not the same as knowing it
     * holds all of them: a plugin built before tag library descriptors existed contributes tags
     * without one, and a tag library registered while an application runs contributes more. Enable
     * this once every tag library an application uses is described, and declare the namespaces that
     * are genuinely filled in at runtime through {@link #getDynamicTagNamespaces() dynamicTagNamespaces}:
     *
     * <pre>
     * grails {
     *     compileStatic {
     *         strictTags = true
     *         dynamicTagNamespaces = ['legacy']
     *     }
     * }
     * </pre>
     *
     * @since 8.0
     */
    final Property<Boolean> strictTags

    /**
     * Namespaces whose tag libraries are registered while the application runs rather than described
     * when it is compiled. Tags in them are never reported as unknown, however complete the tag
     * library index is, and calls to them keep being dispatched dynamically.
     *
     * @since 8.0
     */
    final SetProperty<String> dynamicTagNamespaces

    @Inject
    GrailsCompileStaticOptions(ObjectFactory objects) {
        // Only `all` carries a convention. The artefact types are left unset so that setting one to
        // false is distinguishable from never setting it, which is what lets an explicit value be kept
        // where `all` would otherwise supply it.
        this.all = objects.property(Boolean).convention(false)
        this.controllers = objects.property(Boolean)
        this.services = objects.property(Boolean)
        this.tagLibs = objects.property(Boolean)
        this.strictTags = objects.property(Boolean).convention(false)
        this.unqualifiedTagCalls = objects.property(Boolean).convention(false)
        this.dynamicTagNamespaces = objects.setProperty(String).convention(Collections.<String> emptySet())
        // Not part of `all`, so these carry a convention of their own rather than falling back to it.
        this.gsp = objects.property(Boolean).convention(false)
        this.strictGsp = objects.property(Boolean).convention(false)
    }
}
