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

import groovy.transform.CompileStatic

import io.swagger.v3.core.converter.AnnotatedType
import io.swagger.v3.core.converter.ModelConverters
import io.swagger.v3.core.converter.ResolvedSchema
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.media.ObjectSchema
import io.swagger.v3.oas.models.media.Schema

/**
 * The schemas a document holds in its components: resolved from classes through swagger-core,
 * derived from them, such as a patch, and named so that no two share a name.
 */
@CompileStatic
class ComponentSchemas {

    static final String REFERENCE_PREFIX = '#/components/schemas/'

    private static final String PATCH_SUFFIX = 'Patch'

    private final Components components
    private final boolean openapi31
    private final SchemaNames names = new SchemaNames()
    private final Set<String> added = [] as Set
    private final Map<String, String> patches = [:]

    /**
     * The names the document already has for a schema springdoc resolved from a class, which the
     * schema Grails describes the class by replaces.
     */
    private final Set<String> resolvedElsewhere = [] as Set

    ComponentSchemas(Components components, boolean openapi31) {
        this.components = components
        this.openapi31 = openapi31
    }

    /**
     * The names the classes described are given while the document is described.
     */
    SchemaNames getNames() {
        names
    }

    /**
     * Keeps a name for a schema that is not resolved from a class, so no class takes it.
     *
     * @return the name to describe the schema under while the document is described
     */
    String reserve(String name) {
        names.reserve(name)
    }

    /**
     * Keeps a name the document already has, for the class springdoc resolved its schema from where
     * that is known, so the class is described under the name, as Grails renders it, rather than
     * apart from it.
     */
    void reserve(String name, Class<?> resolvedFrom) {
        names.reserve(name, resolvedFrom)
        if (resolvedFrom != null) {
            resolvedElsewhere << name
        }
    }

    /**
     * Resolves a type into the components through swagger-core and refers to it.
     *
     * @return the reference, or {@code null} where the type cannot be described
     */
    Schema<?> reference(Class<?> type) {
        if (type == null || type == Object) {
            return null
        }
        ResolvedSchema resolved = null
        DocumentParts.describe("type [${type.name}]".toString()) {
            resolved = ModelConverters.getInstance(openapi31)
                    .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(true))
        }
        if (resolved?.schema == null) {
            return null
        }
        resolved.referencedSchemas?.each { String name, Schema schema ->
            if (!components.schemas?.containsKey(name)) {
                components.addSchemas(name, schema)
                added << name
            }
            else if (resolvedElsewhere.remove(name)) {
                // Only the class springdoc resolved it from is described under the name, so the
                // schema describes that class, which springdoc's endpoints refer to as well.
                components.addSchemas(name, schema)
            }
        }
        resolved.schema.$ref ? new Schema<>().$ref(resolved.schema.$ref) : resolved.schema
    }

    /**
     * Counts the schemas some work adds to the components as this document's own, as swagger-core
     * adds a class an annotation names as it reads the annotation, so such a class is named apart
     * as one resolved here is.
     */
    void addedBy(Closure<?> work) {
        Set<String> before = new HashSet<String>(components.schemas?.keySet() ?: Collections.<String> emptySet())
        work.call()
        components.schemas?.keySet()?.each { String name ->
            if (!(name in before)) {
                added << name
            }
        }
    }

    /**
     * The schema of a patch of what a reference refers to: its properties with nothing required,
     * since a patch binds only what it is sent, sent in XML as the same element. A schema requiring
     * nothing is its own patch.
     */
    Schema<?> patchReference(Schema<?> reference) {
        String name = nameOf(reference)
        Schema<?> full = name ? components.schemas?.get(name) : null
        if (full == null || !full.required) {
            return reference
        }
        String patchName = patches.computeIfAbsent(name) { String base -> names.reserve(base + PATCH_SUFFIX) }
        if (!components.schemas.containsKey(patchName)) {
            Schema<?> patch = new ObjectSchema()
            patch.setProperties(new LinkedHashMap<String, Schema>(full.properties ?: [:]))
            patch.setDescription(full.description)
            patch.setXml(full.xml)
            components.addSchemas(patchName, patch)
            added << patchName
        }
        referenceTo(patchName)
    }

    /**
     * The schema of a type described inline, to read its properties from rather than to add to the
     * document, so it claims no name in it.
     */
    Schema<?> inline(Class<?> type) {
        ResolvedSchema resolved = null
        DocumentParts.describe("type [${type.name}]".toString()) {
            GrailsModelConverter.withSchemaNames(null) {
                resolved = ModelConverters.getInstance(openapi31)
                        .resolveAsResolvedSchema(new AnnotatedType(type).resolveAsRef(false))
            }
        }
        resolved?.schema
    }

    /**
     * The names to move the schemas this document added to, now that every class described is
     * known: a class holding a name another class also claims moves to its qualified name, and a
     * patch schema follows the schema it is a patch of. A schema the document already had keeps
     * its name.
     */
    Map<String, String> renames() {
        Map<String, String> renames = names.renames().findAll { String from, String to -> from in added }
        patches.each { String base, String patch ->
            String moved = renames[base]
            if (moved != null && patch in added) {
                renames[patch] = names.move(base + PATCH_SUFFIX, moved + PATCH_SUFFIX)
            }
        }
        renames
    }

    /**
     * @return the name of the component a reference refers to, or {@code null}
     */
    static String nameOf(Schema<?> reference) {
        String ref = reference?.$ref
        ref?.startsWith(REFERENCE_PREFIX) ? ref.substring(REFERENCE_PREFIX.length()) : null
    }

    static Schema<?> referenceTo(String name) {
        new Schema<>().$ref(REFERENCE_PREFIX + name)
    }
}
