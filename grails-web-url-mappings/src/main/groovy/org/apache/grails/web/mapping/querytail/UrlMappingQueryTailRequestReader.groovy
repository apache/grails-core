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
package org.apache.grails.web.mapping.querytail

import java.nio.charset.StandardCharsets

import groovy.transform.CompileStatic

import grails.core.GrailsControllerClass
import grails.web.mapping.UrlMappingData
import grails.web.mapping.UrlMappingInfo
import org.apache.grails.datastore.core.querytail.QueryTailRequestReader
import org.apache.grails.datastore.core.querytail.QueryTailRequestTarget
import org.grails.web.mapping.mvc.GrailsControllerUrlMappingInfo
import org.grails.web.mapping.mvc.UrlMappingsHandlerMapping
import org.grails.web.util.GrailsApplicationAttributes

/**
 * Reads the controller and action Grails resolved a request to, for the {@code query-tail} capture, which finds it
 * with {@link java.util.ServiceLoader}. The page of a request is its controller and action, with the namespace of the
 * controller before them.
 *
 * <p>The controller, action and namespace a request names count only when they are those of the controller class
 * Grails resolved the request to: the class of the URL mapping it matched, else the one it keeps on the request. URL
 * mapping sets the names of each mapping it reads, so the names of a mapping that resolved no controller are values of
 * its path, which it reads as names, and the class Grails keeps on the request may have been looked up by such a name.
 * An action counts only when that controller has it, and a namespace only when it is the one the controller is
 * declared in. Grails names the default action only as it calls it, after the {@code before} interceptors have run,
 * so a request to {@code /book} is on the page of the default action of the controller its mapping matched from its
 * first statement.</p>
 *
 * <p>The path of a request is read against the URL mapping Grails matched: a segment the mapping spells out is kept,
 * as is a variable segment that names the controller, action or namespace, and every other variable segment is
 * replaced whole, including a segment a mapping such as {@code /reset/${token}_confirm} glues a value into, as is
 * every segment a greedy token takes and every path parameter after a semicolon, such as a {@code jsessionid} the
 * container rewrote the path with. A path the mapping does not spell, as one kept from before a forward, or a request
 * whose mapping Grails has let go of, is not read: no segment's role is known, and only its shape is recorded.</p>
 *
 * <p>A request is resolved once, and resolved again only once one of the request attributes it was resolved from has
 * changed, as naming the action Grails calls changes them.</p>
 *
 * @since 9.0
 */
@CompileStatic
class UrlMappingQueryTailRequestReader implements QueryTailRequestReader {

    private static final String PLACEHOLDER = '?'

    /** The extension of a last segment whose name is kept, which a client may fill with any value. */
    private static final String EXTENSION = '.' + PLACEHOLDER

    /** The request attribute the target of a request is kept in, with what it was resolved from. */
    private static final String TARGET_ATTRIBUTE = UrlMappingQueryTailRequestReader.name + '.TARGET'

    @Override
    QueryTailRequestTarget read(QueryTailRequestReader.Attributes request) {
        Object controller = request.getAttribute(GrailsApplicationAttributes.CONTROLLER_NAME_ATTRIBUTE)
        if (controller == null) {
            return null
        }
        Object action = request.getAttribute(GrailsApplicationAttributes.ACTION_NAME_ATTRIBUTE)
        Object namespace = request.getAttribute(GrailsApplicationAttributes.CONTROLLER_NAMESPACE_ATTRIBUTE)
        Object mapping = request.getAttribute(UrlMappingsHandlerMapping.MATCHED_REQUEST)
        Object resolvedClass = request.getAttribute(GrailsApplicationAttributes.GRAILS_CONTROLLER_CLASS)
        Object earlier = request.getAttribute(TARGET_ATTRIBUTE)
        if (earlier instanceof ResolvedTarget &&
                ((ResolvedTarget) earlier).resolvedFrom(controller, action, namespace, mapping, resolvedClass)) {
            return ((ResolvedTarget) earlier).target
        }
        MappedTarget target = resolve(controller, action, namespace, mapping, resolvedClass)
        request.setAttribute(TARGET_ATTRIBUTE, new ResolvedTarget(controller, action, namespace, mapping, resolvedClass, target))
        target
    }

    /**
     * Resolves the target of a request from the names Grails set on it, which count only when they are those of the
     * controller class Grails resolved the request to.
     *
     * @param controller the controller name
     * @param action the action name, which Grails sets for the default action only as it calls it
     * @param namespace the controller namespace
     * @param mapping the URL mapping Grails matched the request with, if it has not let go of it, as it does when the
     * request completes and around a forward
     * @param resolvedClass the controller class Grails keeps on the request
     * @return the target, or null when the names are not those of the controller class
     */
    private static MappedTarget resolve(Object controller, Object action, Object namespace, Object mapping, Object resolvedClass) {
        GrailsControllerClass mapped = mapping instanceof GrailsControllerUrlMappingInfo ?
                ((GrailsControllerUrlMappingInfo) mapping).controllerClass : null
        boolean matched = names(mapped, controller)
        GrailsControllerClass controllerClass = matched ? mapped :
                (resolvedClass instanceof GrailsControllerClass ? (GrailsControllerClass) resolvedClass : null)
        if (!matched && !names(controllerClass, controller)) {
            return null
        }
        if (present(namespace) && !spelledAlike(controllerClass.namespace, namespace)) {
            return null
        }
        Object resolvedAction = action
        if (present(action)) {
            if (!hasAction(controllerClass, action)) {
                return null
            }
        }
        else if (matched) {
            // without it the statements of a request to /book would land on book and on book/index
            resolvedAction = controllerClass.defaultAction ?: action
        }
        new MappedTarget(controller, resolvedAction, namespace, mapping instanceof UrlMappingInfo ? (UrlMappingInfo) mapping : null)
    }

    /** @return whether a controller class is the one a request names, by its logical name */
    private static boolean names(GrailsControllerClass controllerClass, Object controller) {
        controllerClass != null && spelledAlike(controllerClass.logicalPropertyName, controller)
    }

    /** @return whether an action a request names is one of a controller class, or its default action */
    private static boolean hasAction(GrailsControllerClass controllerClass, Object action) {
        if (spelledAlike(controllerClass.defaultAction, action)) {
            return true
        }
        Set<String> actions = controllerClass.actions
        actions != null && actions.any { String name -> spelledAlike(name, action) }
    }

    private static boolean present(Object name) {
        name != null && !name.toString().isEmpty()
    }

    /**
     * @return whether two names are one: a request carries a controller, action or namespace by its name as the URL
     * converter spells it, {@code bookStore} or {@code book-store}
     */
    private static boolean spelledAlike(Object name, Object requested) {
        name != null && requested != null &&
                name.toString().replace('-', '').equalsIgnoreCase(requested.toString().replace('-', ''))
    }

    /**
     * The controller, action and namespace Grails resolved a request to, and the URL mapping it matched, which the
     * path of the request is read against.
     */
    @CompileStatic
    private static final class MappedTarget implements QueryTailRequestTarget {

        final Object controller
        final Object action
        final Object namespace
        final UrlMappingInfo mapping

        MappedTarget(Object controller, Object action, Object namespace, UrlMappingInfo mapping) {
            this.controller = controller
            this.action = action
            this.namespace = namespace
            this.mapping = mapping
        }

        @Override
        String getPage() {
            StringBuilder page = new StringBuilder()
            if (namespace != null) {
                page.append(namespace).append(':')
            }
            page.append(controller)
            if (action != null) {
                page.append('/').append(action)
            }
            page.toString()
        }

        /**
         * Reads a path against the tokens of the URL mapping that matched it: a token the mapping spells out is kept
         * where the path spells it out too, a variable segment that names the controller, action or namespace is
         * kept, and every other variable is replaced, as is every segment a greedy token takes. The path parameters
         * of a segment are replaced each, and so is the extension of a last segment that is kept by what comes before
         * it, which the client chose, as a format is.
         */
        @Override
        String describePath(String path) {
            String[] tokens = tokens()
            if (tokens == null || !path.startsWith('/')) {
                return null
            }
            Set<String> names = names()
            List<String> segments = new ArrayList<>(Arrays.asList(path.substring(1).split('/', -1)))
            boolean trailingSlash = !segments.isEmpty() && segments.last().isEmpty()
            if (trailingSlash) {
                segments.removeLast()
            }
            List<String> spelled = tokens.findAll { String token -> !token.isEmpty() }
            int greedy = spelled.findIndexOf { String token -> token.contains('**') }
            List<String> out = []
            int at = 0
            for (int i = 0; i < spelled.size(); i++) {
                String token = spelled[i]
                boolean optional = token.endsWith('?')
                String bare = optional ? token.substring(0, token.length() - 1) : token
                if (i == greedy) {
                    int taken = segments.size() - (spelled.size() - 1)
                    if (taken < 0) {
                        return null
                    }
                    for (int k = 0; k < taken; k++) {
                        out.add(PLACEHOLDER + parametersOf(segments[at++]))
                    }
                    continue
                }
                if (at >= segments.size()) {
                    if (optional) {
                        continue
                    }
                    return null
                }
                String segment = segments[at]
                int semicolon = segment.indexOf(';')
                String bareSegment = semicolon < 0 ? segment : segment.substring(0, semicolon)
                String decoded = decode(bareSegment)
                boolean last = at == segments.size() - 1
                if (!bare.contains('(') && !bare.contains('*')) {
                    if (decoded == bare) {
                        out.add(bareSegment + parametersOf(segment))
                    }
                    else if (last && withoutExtension(decoded) == bare) {
                        out.add(bare + EXTENSION + parametersOf(segment))
                    }
                    else {
                        return null
                    }
                }
                else if (bare == '(*)' && names.contains(decoded)) {
                    out.add(bareSegment + parametersOf(segment))
                }
                else if (bare == '(*)' && last && names.contains(withoutExtension(decoded))) {
                    out.add(withoutExtension(decoded) + EXTENSION + parametersOf(segment))
                }
                else {
                    out.add(PLACEHOLDER + parametersOf(segment))
                }
                at++
            }
            if (at != segments.size()) {
                return null
            }
            '/' + out.join('/') + (trailingSlash && !out.isEmpty() ? '/' : '')
        }

        /**
         * @return the tokens of the URL mapping, which spell out its literal segments and stand in for its variables,
         * or null when there is no mapping to read
         */
        private String[] tokens() {
            UrlMappingData data = mapping?.urlData
            data?.tokens
        }

        /** @return the names of the controller, action and namespace, which a path may spell out */
        private Set<String> names() {
            Set<String> names = new HashSet<>()
            for (Object name in [controller, action, namespace]) {
                if (name != null && !name.toString().isEmpty()) {
                    names.add(name.toString())
                }
            }
            names
        }

        private static String withoutExtension(String segment) {
            int dot = segment.lastIndexOf('.')
            dot > 0 ? segment.substring(0, dot) : segment
        }

        /** @return the placeholder for each path parameter of a segment, which start with its first semicolon */
        private static String parametersOf(String segment) {
            int semicolon = segment.indexOf(';')
            if (semicolon < 0) {
                return ''
            }
            StringBuilder out = new StringBuilder()
            for (int i = semicolon; i >= 0; i = segment.indexOf(';', i + 1)) {
                out.append(';').append(PLACEHOLDER)
            }
            out.toString()
        }

        /**
         * @return the segment with its percent-encoding decoded, or as it is when it has none or is not valid
         * percent-encoding; a plus sign is kept, since it is literal in a path
         */
        private static String decode(String segment) {
            if (segment.indexOf('%') < 0) {
                return segment
            }
            try {
                return URLDecoder.decode(segment.replace('+', '%2B'), StandardCharsets.UTF_8)
            }
            catch (IllegalArgumentException ignored) {
                return segment
            }
        }
    }

    /**
     * The target a request was resolved to, or none, and the request attributes it was resolved from, so that a later
     * statement of the request reuses it until Grails changes one of them, as naming the action it calls does.
     */
    @CompileStatic
    private static final class ResolvedTarget {

        final Object controller
        final Object action
        final Object namespace
        final Object mapping
        final Object controllerClass
        final MappedTarget target

        ResolvedTarget(Object controller, Object action, Object namespace, Object mapping, Object controllerClass, MappedTarget target) {
            this.controller = controller
            this.action = action
            this.namespace = namespace
            this.mapping = mapping
            this.controllerClass = controllerClass
            this.target = target
        }

        boolean resolvedFrom(Object controller, Object action, Object namespace, Object mapping, Object controllerClass) {
            this.controller == controller && this.action == action && this.namespace == namespace &&
                    this.mapping.is(mapping) && this.controllerClass.is(controllerClass)
        }
    }
}
