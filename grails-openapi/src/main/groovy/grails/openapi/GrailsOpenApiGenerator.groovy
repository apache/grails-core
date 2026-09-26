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
package grails.openapi

import java.lang.reflect.Method

import groovy.transform.CompileStatic

import io.swagger.v3.core.util.Json
import io.swagger.v3.core.util.Json31
import io.swagger.v3.core.util.Yaml
import io.swagger.v3.core.util.Yaml31
import io.swagger.v3.oas.annotations.tags.Tag as TagAnnotation
import io.swagger.v3.oas.models.Components
import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.Operation
import io.swagger.v3.oas.models.PathItem
import io.swagger.v3.oas.models.Paths
import io.swagger.v3.oas.models.SpecVersion
import io.swagger.v3.oas.models.info.Info
import io.swagger.v3.oas.models.media.Schema
import io.swagger.v3.oas.models.parameters.RequestBody
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.core.io.DefaultResourceLoader
import org.springframework.core.io.ResourceLoader

import grails.core.GrailsApplication
import grails.core.GrailsControllerClass
import grails.rest.RestfulController
import grails.web.http.HttpHeaders
import grails.web.mapping.UrlMapping
import grails.web.mapping.UrlMappingsHolder
import org.grails.datastore.mapping.model.MappingContext
import org.grails.openapi.ActionAnnotations
import org.grails.openapi.ActionHooks
import org.grails.openapi.BaseDocument
import org.grails.openapi.ComponentSchemas
import org.grails.openapi.ControllerCatalog
import org.grails.openapi.DocumentCompletion
import org.grails.openapi.DocumentParts
import org.grails.openapi.ErrorsViews
import org.grails.openapi.GrailsModelConverter
import org.grails.openapi.MappingVersions
import org.grails.openapi.MediaTypes
import org.grails.openapi.OperationParameters
import org.grails.openapi.OperationResponses
import org.grails.openapi.RestfulControllerActions
import org.grails.openapi.SchemaReferences
import org.grails.openapi.UrlMappingPaths
import org.grails.openapi.ValidationErrorsContent
import org.grails.web.mapping.ResponseCodeMappingData

/**
 * Generates an OpenAPI description of a Grails application from its URL mappings, its
 * controllers and its GORM mapping context.
 *
 * <p>The generator depends on nothing but the application, so the same description is produced
 * at build time by the {@code generate-open-api} command and at runtime by springdoc, which this
 * module contributes the description to when springdoc is on the classpath.</p>
 *
 * @since 8.0
 */
@CompileStatic
class GrailsOpenApiGenerator {

    /**
     * The schema describing the validation errors Grails renders when a request cannot be bound.
     */
    static final String VALIDATION_ERRORS_SCHEMA = 'ValidationErrors'

    private static final Logger LOG = LoggerFactory.getLogger(GrailsOpenApiGenerator)

    /**
     * The name the validation errors are described under where the document already describes
     * something else as {@link #VALIDATION_ERRORS_SCHEMA}: that of the class Grails renders them from.
     */
    private static final String QUALIFIED_VALIDATION_ERRORS_SCHEMA = 'grails.validation.ValidationErrors'

    private static final String CONTROLLER_TOKEN = 'controller'
    private static final String ACTION_TOKEN = 'action'
    private static final String NAMESPACE_TOKEN = 'namespace'
    private static final String ID_TOKEN = 'id'
    private static final String DEFAULT_TITLE = 'Grails application'
    private static final String DEFAULT_VERSION = '1.0'
    private static final String SPRINGDOC_TITLE = 'OpenAPI definition'
    private static final String SPRINGDOC_VERSION = 'v0'

    private static final List<String> BODY_METHODS = ['POST', 'PUT', 'PATCH'].asImmutable()

    private final GrailsApplication grailsApplication
    private final UrlMappingsHolder urlMappingsHolder
    private final Collection<MappingContext> mappingContexts
    private final OpenApiSettings settings
    private final ResourceLoader resourceLoader

    /**
     * @param grailsApplication the application whose controllers are described
     * @param urlMappingsHolder the URL mappings whose paths are described
     * @param mappingContexts the GORM mapping contexts whose entities are described, which may be
     * empty in an application without GORM
     * @param settings what to describe
     */
    GrailsOpenApiGenerator(GrailsApplication grailsApplication, UrlMappingsHolder urlMappingsHolder,
                           Collection<MappingContext> mappingContexts, OpenApiSettings settings) {
        this.grailsApplication = grailsApplication
        this.urlMappingsHolder = urlMappingsHolder
        this.mappingContexts = mappingContexts ?: Collections.<MappingContext> emptyList()
        this.settings = settings ?: new OpenApiSettings()
        this.resourceLoader = grailsApplication?.mainContext ?: new DefaultResourceLoader(GrailsOpenApiGenerator.classLoader)
    }

    /**
     * @return the settings the generator describes the application with
     */
    OpenApiSettings getSettings() {
        settings
    }

    /**
     * @return the default document
     */
    OpenAPI generate() {
        generate(settings.defaultSelection)
    }

    /**
     * @param group the name of a group configured under {@code grails.openapi.groups}
     * @return the document of that group
     * @throws IllegalArgumentException if no such group is configured
     */
    OpenAPI generate(String group) {
        OpenApiSelection selection = settings.group(group)
        if (selection == null) {
            throw new IllegalArgumentException("No OpenAPI group named [${group}] is configured".toString())
        }
        generate(selection)
    }

    /**
     * @return a complete document of the operations the selection selects
     */
    OpenAPI generate(OpenApiSelection selection) {
        boolean openapi31 = settings.specVersion == SpecVersion.V31
        OpenAPI openApi = new OpenAPI(settings.specVersion).openapi(openapi31 ? '3.1.0' : '3.0.1')
        openApi.setInfo(applicationInfo(selection))
        contribute(openApi, selection)
        if (openApi.info?.version == null) {
            openApi.info.setVersion(applicationInfo(selection).version)
        }
        openApi
    }

    /**
     * The document titled with the group's display name or {@code info.app.name}, and versioned
     * with {@code info.app.version}.
     */
    private Info applicationInfo(OpenApiSelection selection) {
        String name = grailsApplication?.config?.getProperty('info.app.name', String)
        String version = grailsApplication?.config?.getProperty('info.app.version', String)
        new Info()
                .title(selection?.displayName ?: name ?: DEFAULT_TITLE)
                .version(version ?: DEFAULT_VERSION)
    }

    /**
     * Adds the described operations, and the base document when one is configured, to a document
     * something else has started, such as springdoc.
     *
     * @param openApi the document to add to
     * @param selection what the document selects, or {@code null} for everything
     */
    void contribute(OpenAPI openApi, OpenApiSelection selection) {
        if (!settings.enabled) {
            return
        }
        GrailsModelConverter.register()
        GrailsModelConverter.withMappingContexts(mappingContexts, settings.includeVersion) {
            new Contribution(openApi, selection ?: new OpenApiSelection()).contribute()
        }
    }

    /**
     * Writes a document in the format named: {@code json}, or YAML otherwise.
     */
    static String serialize(OpenAPI openApi, String format) {
        boolean openapi31 = openApi.specVersion == SpecVersion.V31
        boolean json = format?.equalsIgnoreCase('json')
        if (openapi31) {
            return json ? Json31.pretty(openApi) : Yaml31.pretty(openApi)
        }
        json ? Json.pretty(openApi) : Yaml.pretty(openApi)
    }

    /**
     * Adding the operations the URL mappings reach to one document.
     */
    private class Contribution {

        private final OpenAPI openApi
        private final OpenApiSelection selection
        private final ActionHooks hooks
        private final boolean openapi31
        private final Components components
        private final Paths paths
        private final ControllerCatalog controllers
        private final ComponentSchemas schemas
        private final MediaTypes mediaTypes
        private final OperationParameters parameters
        private final MappingVersions versions
        private OperationResponses responses

        Contribution(OpenAPI openApi, OpenApiSelection selection) {
            this.openApi = openApi
            this.selection = selection
            // What springdoc decides by the action itself, where the selection is springdoc's.
            this.hooks = selection instanceof ActionHooks ? (ActionHooks) selection : null
            this.openapi31 = openApi.specVersion == SpecVersion.V31
            this.components = openApi.components ?: new Components()
            this.paths = openApi.paths ?: new Paths()
            this.controllers = new ControllerCatalog(grailsApplication)
            this.schemas = new ComponentSchemas(components, openapi31)
            this.mediaTypes = new MediaTypes(grailsApplication?.mainContext, controllers)
            this.parameters = new OperationParameters(mappingContexts, schemas)
            this.versions = new MappingVersions(urlMappingsHolder)
        }

        void contribute() {
            Map<String, Class<?>> resolvedBySpringdoc = GrailsModelConverter.takeResolvedNames()
            OpenAPI base = BaseDocument.read(settings.baseDocument, resourceLoader, openapi31)
            if (base != null) {
                BaseDocument.merge(base, openApi, paths, components) { String path -> selection.selectsPath(path) }
            }
            describeApplication()

            // A name the document already has, from the base document or springdoc, or that it
            // derives rather than resolves, is not taken by a class of the same name. One springdoc
            // resolved from a class for its own endpoints is that class's: a Grails endpoint using
            // the class is described by it, as Grails renders the class.
            Set<String> declared = base?.components?.schemas?.keySet() ?: Collections.<String> emptySet()
            components.schemas?.keySet()?.each { String name ->
                schemas.reserve(name, name in declared ? null : resolvedBySpringdoc[name])
            }
            // Validation errors the base document declares describe them in place of those derived.
            // A schema springdoc already has under the name describes something else, so the errors
            // are described under their qualified name.
            boolean errorsDeclared = VALIDATION_ERRORS_SCHEMA in declared
            String errorsSchema = schemas.reserve(errorsDeclared || !components.schemas?.containsKey(VALIDATION_ERRORS_SCHEMA)
                    ? VALIDATION_ERRORS_SCHEMA : QUALIFIED_VALIDATION_ERRORS_SCHEMA)
            responses = new OperationResponses(schemas, new ValidationErrorsContent(components,
                    ErrorsViews.of(grailsApplication?.mainContext), errorsSchema, errorsDeclared))
            GrailsModelConverter.withSchemaNames(schemas.names) {
                for (UrlMapping mapping : urlMappingsHolder.urlMappings) {
                    DocumentParts.describe("URL mapping [${mapping.urlData?.urlPattern}]".toString()) {
                        addMappedOperations(mapping)
                    }
                }
                addExpandedMappings()
            }
            DocumentCompletion.disambiguateOperationIds(paths)

            openApi.setPaths(paths)
            openApi.setComponents(components)
            SchemaReferences.rename(openApi, schemas.renames())
            DocumentCompletion.registerTags(openApi, paths, controllers.controllers)
            DocumentCompletion.dropUnresolvedReferences(paths, components)
            if (!components.schemas && !components.securitySchemes && !components.responses
                    && !components.parameters && !components.examples && !components.requestBodies
                    && !components.headers && !components.links && !components.callbacks) {
                openApi.setComponents(null)
            }
        }

        /**
         * springdoc starts a document with a placeholder title and version, which are replaced by
         * the application's, as a document generated at build time has them. A title or version
         * given any other way is kept.
         */
        private void describeApplication() {
            Info info = openApi.info
            if (info != null && info.title == SPRINGDOC_TITLE && info.version == SPRINGDOC_VERSION) {
                openApi.setInfo(applicationInfo(selection))
            }
        }

        /**
         * Describes the operations of a mapping that names its controller.
         */
        private void addMappedOperations(UrlMapping mapping) {
            String controllerName = asStaticName(mapping.controllerName)
            if (!controllerName || isResponseCode(mapping)) {
                return
            }
            GrailsControllerClass controller = controllers.controllerFor(controllerName, asStaticName(mapping.namespace))
            if (controller == null && grailsApplication != null) {
                // A controller the application does not have answers nothing but 404.
                LOG.debug('Skipping the URL mapping [{}]: the application has no controller [{}]',
                        mapping.urlData?.urlPattern, controllerName)
                return
            }
            Object declaredAction = mapping.actionName
            if (declaredAction instanceof Map) {
                // The action is chosen by the method of the request, so there is an operation for each.
                ((Map<Object, Object>) declaredAction).each { Object method, Object action ->
                    addMappedOperation(mapping, controller, controllerName, asStaticName(action), method?.toString())
                }
                return
            }
            if (declaredAction != null && !(declaredAction instanceof CharSequence)) {
                LOG.warn('Skipping the URL mapping [{}]: its action is decided as each request is made',
                        mapping.urlData?.urlPattern)
                return
            }
            String actionName = asStaticName(declaredAction)
            if (actionName == null && controller != null && UrlMappingPaths.variableNames(mapping).contains(ACTION_TOKEN)) {
                // The action is taken from the path, so every action the controller declares is reached.
                for (String action : controller.actions) {
                    DocumentParts.describe("action [${controllerName}.${action}]".toString()) {
                        addExpandedOperation(mapping, controller, action, true)
                    }
                }
                if (UrlMappingPaths.isOptional(mapping, ACTION_TOKEN)) {
                    DocumentParts.describe("action [${controllerName}.${controller.defaultAction}]".toString()) {
                        addDefaultActionOperation(mapping, controller, controllerName)
                    }
                }
                return
            }
            // A mapping that names only the controller dispatches to its default action.
            addMappedOperation(mapping, controller, controllerName, actionName ?: controller?.defaultAction, mapping.httpMethod)
        }

        /**
         * A mapping whose action is optional reaches the controller's default action where the path
         * leaves the action out, and every variable after it, which Grails would take for the action.
         */
        private void addDefaultActionOperation(UrlMapping mapping, GrailsControllerClass controller, String controllerName,
                                               Map<String, String> substitutions = [:], Set<String> omitted = [] as Set) {
            String actionName = controller.defaultAction
            if (!actionName || !controller.actions.contains(actionName) || !isDescribed(controller, controller.clazz, actionName)) {
                return
            }
            List<String> names = UrlMappingPaths.variableNames(mapping)
            Set<String> left = new HashSet<String>(omitted)
            left.addAll(names.subList(names.indexOf(ACTION_TOKEN), names.size()))
            List<String> described = UrlMappingPaths.paths(mapping, substitutions, left).take(1)
            for (PathItem.HttpMethod method : httpMethods(mapping.httpMethod, controller, actionName)) {
                for (String path : described) {
                    addOperation(mapping, path, method, controller, controller.clazz, controllerName, actionName,
                            operationId(controller, controllerName, actionName, method))
                }
            }
        }

        private void addMappedOperation(UrlMapping mapping, GrailsControllerClass controller, String controllerName,
                                        String actionName, String mappedMethod) {
            Class<?> controllerType = controller?.clazz
            if (!isDescribed(controller, controllerType, actionName)) {
                return
            }
            for (PathItem.HttpMethod method : httpMethods(mappedMethod, controller, actionName)) {
                for (String path : UrlMappingPaths.paths(mapping)) {
                    addOperation(mapping, path, method, controller, controllerType, controllerName, actionName,
                            operationId(controller, controllerName, actionName, method))
                }
            }
        }

        /**
         * The methods an action is described as answering: the one the mapping declares, unless the
         * controller's {@code allowedMethods} refuses it for the action, which Grails answers with
         * 405; or, where the mapping accepts any method, those {@code allowedMethods} declares for the
         * action, or the one its kind of action answers.
         */
        private List<PathItem.HttpMethod> httpMethods(String mappedMethod, GrailsControllerClass controller,
                                                      String actionName) {
            List<String> allowed = RestfulControllerActions.allowedMethods(actionName, controllers.allowedMethods(controller))
            List<String> methods
            if (mappedMethod && mappedMethod != UrlMapping.ANY_HTTP_METHOD) {
                String declared = mappedMethod.toUpperCase(Locale.ENGLISH)
                methods = allowed == null || declared in allowed ? [declared] : []
            }
            else {
                methods = allowed ?: [RestfulControllerActions.defaultMethod(actionName, controllers.isResourceController(controller))]
            }
            methods.collect { String method -> toHttpMethod(method) }.findAll().unique()
        }

        /**
         * Describes the REST controllers a mapping reaches without naming, which is how a REST
         * application is mapped: {@code get "/$controller"(action: 'index')} names the action but
         * leaves the controller to the request, and the default
         * {@code "/$controller/$action?/$id?"} mapping leaves both.
         *
         * <p>Expansion follows the mapping rather than the controllers, so only routes the
         * application actually serves are described.</p>
         */
        private void addExpandedMappings() {
            List<GrailsControllerClass> reached = controllers.controllers.findAll { GrailsControllerClass it ->
                controllers.isRestController(it) && !ActionAnnotations.isHidden(it.clazz)
            }.toList()
            if (!reached) {
                return
            }

            for (UrlMapping mapping : urlMappingsHolder.urlMappings) {
                if (asStaticName(mapping.controllerName) || mapping.viewName || isResponseCode(mapping)) {
                    continue
                }
                List<String> names = UrlMappingPaths.variableNames(mapping)
                if (!names.contains(CONTROLLER_TOKEN)) {
                    continue
                }
                String mappedAction = asStaticName(mapping.actionName)
                boolean expandsAction = mappedAction == null && names.contains(ACTION_TOKEN)
                if (mappedAction == null && !expandsAction) {
                    continue
                }

                boolean capturesNamespace = names.contains(NAMESPACE_TOKEN)
                boolean optionalAction = expandsAction && UrlMappingPaths.isOptional(mapping, ACTION_TOKEN)
                for (GrailsControllerClass controller : reached) {
                    // A mapping that leaves the namespace out reaches the controller Grails resolves for
                    // the name alone; one that captures it reaches each controller at its own.
                    if (!capturesNamespace && !controllers.controllerFor(controller.logicalPropertyName, null).is(controller)) {
                        continue
                    }
                    Collection<String> actions = expandsAction ? controller.actions : [mappedAction]
                    for (String actionName : actions) {
                        if (!controllers.isRestAction(controller, actionName)) {
                            continue
                        }
                        DocumentParts.describe("action [${controller.logicalPropertyName}.${actionName}]".toString()) {
                            addExpandedOperation(mapping, controller, actionName, expandsAction)
                        }
                    }
                    if (optionalAction && controllers.isRestAction(controller, controller.defaultAction)) {
                        // As for a mapping naming the controller, the path without the action reaches
                        // the default action: GET /book is the index of "/$controller/$action?/$id?".
                        DocumentParts.describe("action [${controller.logicalPropertyName}.${controller.defaultAction}]".toString()) {
                            Map<String, String> substitutions = [:]
                            Set<String> omitted = [] as Set
                            if (reachedAt(mapping, controller, substitutions, omitted)) {
                                addDefaultActionOperation(mapping, controller, controller.logicalPropertyName,
                                        substitutions, omitted)
                            }
                        }
                    }
                }
            }
        }

        private void addExpandedOperation(UrlMapping mapping, GrailsControllerClass controller,
                                          String actionName, boolean expandsAction) {
            // A mapping that names the action reaches every controller; one that leaves the action to
            // the request only reaches the actions that controller declares.
            if (!actionName || !controller.actions.contains(actionName)
                    || !isDescribed(controller, controller.clazz, actionName)) {
                return
            }

            String controllerName = controller.logicalPropertyName
            Map<String, String> substitutions = [:]
            Set<String> omitted = [] as Set
            if (!reachedAt(mapping, controller, substitutions, omitted)) {
                return
            }
            boolean takesId = controllers.takesId(controller, actionName)
            if (expandsAction) {
                substitutions[ACTION_TOKEN] = actionName
                if (!takesId) {
                    omitted << ID_TOKEN
                }
            }

            List<String> described = UrlMappingPaths.paths(mapping, substitutions, omitted)
            for (PathItem.HttpMethod method : httpMethods(mapping.httpMethod, controller, actionName)) {
                String operationId = operationId(controller, controllerName, actionName, method)
                if (expandsAction) {
                    operationId += '_byAction'
                }
                // Only the form that addresses a resource is described where the action takes one;
                // the shorter forms an optional identifier allows do not reach it.
                for (String path : (expandsAction && takesId ? described.take(1) : described)) {
                    addOperation(mapping, path, method, controller, controller.clazz, controllerName, actionName, operationId)
                }
            }
        }

        /**
         * Fixes the controller, and the namespace where the mapping captures it, to those of the
         * controller a mapping is expanded for.
         *
         * @return false where the mapping does not reach the controller: it captures a namespace the
         * controller does not have
         */
        private boolean reachedAt(UrlMapping mapping, GrailsControllerClass controller, Map<String, String> substitutions,
                                  Set<String> omitted) {
            substitutions[CONTROLLER_TOKEN] = controller.logicalPropertyName
            if (!UrlMappingPaths.variableNames(mapping).contains(NAMESPACE_TOKEN)) {
                return true
            }
            if (controller.namespace) {
                substitutions[NAMESPACE_TOKEN] = controller.namespace
            }
            else if (UrlMappingPaths.isOptional(mapping, NAMESPACE_TOKEN)) {
                omitted << NAMESPACE_TOKEN
            }
            else {
                return false
            }
            true
        }

        private boolean isDescribed(GrailsControllerClass controller, Class<?> controllerType, String actionName) {
            if (ActionAnnotations.isHidden(controllerType)
                    || (actionName && ActionAnnotations.isHidden(controllerType, actionName))) {
                return false
            }
            boolean restful = controllerType != null && RestfulController.isAssignableFrom(controllerType)
            if (controllers.isResourceController(controller) && !settings.includeFormActions
                    && RestfulControllerActions.isFormAction(actionName)) {
                return false
            }
            if (restful && RestfulControllerActions.refusedWhenReadOnly(controllerType, actionName)
                    && controllers.isReadOnly(controller)) {
                return false
            }
            !settings.annotatedOnly || ActionAnnotations.isAnnotated(controllerType, actionName)
        }

        private void addOperation(UrlMapping mapping, String path, PathItem.HttpMethod method,
                                  GrailsControllerClass controller, Class<?> controllerType, String controllerName,
                                  String actionName, String operationId) {
            Method action = ActionAnnotations.actionMethod(controllerType, actionName)
            if (!selection.selects(path, controllerType) || (hooks != null && !hooks.selectsAction(action))) {
                return
            }
            PathItem pathItem = paths.get(path) ?: new PathItem()
            if (pathItem.readOperationsMap().containsKey(method)) {
                return
            }
            List<String> produces = mediaTypes.responseMediaTypes(controller, actionName).keySet().toList()
            List<String> consumes = bindsBody(method, controller, controllerType, actionName)
                    ? mediaTypes.bodyMediaTypes(controller, controllerType, actionName)
                    : Collections.<String> emptyList()
            String version = MappingVersions.versionOf(mapping)
            List<String> headers = version != null
                    ? ["${HttpHeaders.ACCEPT_VERSION}=${version}".toString()]
                    : Collections.<String> emptyList()
            if (!selection.selectsConditions(produces, consumes, headers)) {
                return
            }

            Operation operation = buildOperation(mapping, path, method, controller, controllerType, controllerName,
                    actionName, operationId)
            if (version != null) {
                OperationParameters.addVersionParameter(operation, version, !versions.isLatest(mapping, version))
            }
            schemas.addedBy { ActionAnnotations.apply(operation, controllerType, actionName, components, openapi31) }
            if (hooks != null) {
                operation = hooks.customize(operation, components, controller, action)
                if (operation == null) {
                    return
                }
            }
            pathItem.operation(method, operation)
            paths.addPathItem(path, pathItem)
        }

        private Operation buildOperation(UrlMapping mapping, String path, PathItem.HttpMethod method,
                                         GrailsControllerClass controller, Class<?> controllerType,
                                         String controllerName, String actionName, String operationId) {
            boolean restful = controllers.isResourceController(controller)
            Class<?> resourceType = restful ? controllers.resourceType(controller) : null
            List<String> pathNames = UrlMappingPaths.templateVariables(path)

            Operation operation = new Operation()
            List<TagAnnotation> tags = ActionAnnotations.declaredTags(controllerType)
            operation.setTags(tags ? tags*.name().toList() : [controllerName])
            operation.setOperationId(operationId)

            parameters.addPathParameters(operation, mapping, pathNames, controllerType, actionName, resourceType)
            if (restful && actionName && RestfulControllerActions.paginates(actionName)) {
                OperationParameters.addPagingParameters(operation)
            }
            parameters.addRequestParameters(operation, controllerType, actionName, pathNames)
            if (!(method.name() in BODY_METHODS)) {
                parameters.addCommandParameters(operation, controllerType, actionName, pathNames)
            }

            if (restful && actionName) {
                boolean locates = controllerType != null && RestfulController.isAssignableFrom(controllerType)
                operation.setResponses(responses.restful(controller, resourceType, actionName, !pathNames.isEmpty(),
                        mediaTypes.responseMediaTypes(controller, actionName), locates))
            }
            else {
                operation.setResponses(OperationResponses.plain(!pathNames.isEmpty()))
            }

            if (method.name() in BODY_METHODS && !ActionAnnotations.declaresRequestBody(controllerType, actionName)) {
                Schema<?> body = requestBodySchema(controllerType, actionName, resourceType, method)
                if (body != null) {
                    Map<String, Boolean> bodyTypes = mediaTypes.bodyMediaTypes(controller, controllerType, actionName)
                            .collectEntries { String mediaType -> [(mediaType): true] } as Map<String, Boolean>
                    operation.setRequestBody(new RequestBody().content(MediaTypes.content(body, bodyTypes)))
                }
            }
            operation
        }

        /**
         * Whether an operation binds a body: one it declares, the command object its action takes,
         * or the resource a RestfulController serves.
         */
        private boolean bindsBody(PathItem.HttpMethod method, GrailsControllerClass controller,
                                  Class<?> controllerType, String actionName) {
            if (!(method.name() in BODY_METHODS)) {
                return false
            }
            if (ActionAnnotations.declaresRequestBody(controllerType, actionName)
                    || ActionAnnotations.commandObjectType(controllerType, actionName) != null) {
                return true
            }
            controllers.isResourceController(controller) && controllers.resourceType(controller) != null
        }

        /**
         * The body an action binds: the command object it takes, in preference to the resource its
         * controller serves. A patch binds only what it is sent, so nothing is required of it.
         */
        private Schema<?> requestBodySchema(Class<?> controllerType, String actionName, Class<?> resourceType,
                                            PathItem.HttpMethod method) {
            Class<?> commandType = ActionAnnotations.commandObjectType(controllerType, actionName)
            Schema<?> body = schemas.reference(commandType ?: resourceType)
            method == PathItem.HttpMethod.PATCH ? schemas.patchReference(body) : body
        }
    }

    /**
     * A mapping declared for a status code rather than a URL, whose pattern is the code itself.
     */
    private static boolean isResponseCode(UrlMapping mapping) {
        mapping.urlData instanceof ResponseCodeMappingData
    }

    /**
     * Grails exposes controller, action and namespace names as {@code Object} because a mapping
     * may define them dynamically. Only statically declared names can be documented.
     */
    private static String asStaticName(Object name) {
        name instanceof CharSequence && name ? name.toString() : null
    }

    /**
     * Grails allows a mapping to accept any HTTP method. OpenAPI requires a concrete operation, so
     * an unrestricted mapping is documented as GET.
     */
    private static PathItem.HttpMethod toHttpMethod(String httpMethod) {
        if (!httpMethod || httpMethod == UrlMapping.ANY_HTTP_METHOD) {
            return PathItem.HttpMethod.GET
        }
        try {
            return PathItem.HttpMethod.valueOf(httpMethod.toUpperCase(Locale.ENGLISH))
        }
        catch (IllegalArgumentException ignored) {
            LOG.warn('Skipping a URL mapping declared for the unsupported HTTP method [{}]', httpMethod)
            return null
        }
    }

    /**
     * Controllers of the same name in different namespaces are distinguished by the namespace.
     */
    private static String operationId(GrailsControllerClass controller, String controllerName, String actionName,
                                      PathItem.HttpMethod method) {
        String prefix = controller?.namespace ? "${controller.namespace}_${controllerName}".toString() : controllerName
        String verb = method.name().toLowerCase(Locale.ENGLISH)
        actionName ? "${prefix}_${actionName}_${verb}".toString() : "${prefix}_${verb}".toString()
    }
}
