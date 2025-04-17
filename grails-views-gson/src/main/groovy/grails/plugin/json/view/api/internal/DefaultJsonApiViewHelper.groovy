package grails.plugin.json.view.api.internal

import grails.plugin.json.builder.JsonGenerator
import grails.plugin.json.builder.JsonOutput
import grails.plugin.json.view.api.GrailsJsonViewHelper
import grails.plugin.json.view.api.JsonApiViewHelper
import grails.plugin.json.view.api.JsonView
import grails.plugin.json.view.api.jsonapi.JsonApiIdRenderStrategy
import grails.rest.Link
import grails.views.api.http.Parameters
import groovy.transform.CompileStatic
import org.codehaus.groovy.runtime.StackTraceUtils
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.springframework.validation.Errors
import org.springframework.validation.FieldError
import org.springframework.validation.ObjectError
import org.springframework.http.HttpMethod

/**
 * @Author Colin Harrington
 */
@CompileStatic
class DefaultJsonApiViewHelper extends DefaultJsonViewHelper implements JsonApiViewHelper {

    GrailsJsonViewHelper viewHelper

    /**
     * The jsonapiobject parameter
     */
    String JSON_API_OBJECT = "jsonApiObject"

    /**
     * The meta parameter
     */
    String META = "meta"

    /**
     * The pagination parameter
     */
    String PAGINATION = "pagination"

    public static final JsonOutput.JsonWritable NOOP_OUTPUT = new JsonOutput.JsonWritable() {
        @Override
        Writer writeTo(Writer out) throws IOException {
            return out
        }
    }

    DefaultJsonApiViewHelper(JsonView view, GrailsJsonViewHelper viewHelper) {
        super(view)
        this.viewHelper = viewHelper
    }

    @Override
    JsonOutput.JsonWritable render(Object object) {
        return render(object, [:])
    }

    @Override
    JsonOutput.JsonWritable render(Object object, Map arguments) {
        if (object == null) {
            return NULL_OUTPUT
        }
        JsonOutput.JsonWritable jsonWritable = new JsonOutput.JsonWritable() {
            @Override
            @CompileStatic
            Writer writeTo(Writer out) throws IOException {
                out.write(JsonOutput.OPEN_BRACE)
                def meta = arguments.get(META)
                if (arguments.get(JSON_API_OBJECT)) {
                    renderJsonApiMember(out, meta)
                    out.write(JsonOutput.COMMA)
                } else if (meta != null) {
                    renderMetaObject(out, meta)
                    out.write(JsonOutput.COMMA)
                }
                if (object instanceof Throwable) {
                    renderException(out, object)
                } else if (objectHasErrors(object)) {
                    renderErrors(object).writeTo(out)
                } else {
                    renderData(object, arguments).writeTo(out)
                    out.write(JsonOutput.COMMA)
                    renderLinks(object, arguments).writeTo(out)
                    renderIncluded(object, arguments).writeTo(out)

                }
                out.write(JsonOutput.CLOSE_BRACE)
                return out
            }

        }
        return jsonWritable
    }

    boolean objectHasErrors(Object subject) {
        if (subject.hasProperty('errors')) {
            Object errors = subject.getAt('errors')
            if (errors instanceof Errors) {
                return errors.hasErrors()
            } else {
                return errors.asBoolean()
            }
        }
        return false
    }

    private boolean isAttributeAssociation(Association a) {
        a.embedded || a instanceof Basic
    }

    List<Association> getRelationships(PersistentEntity entity) {
        entity.associations.findAll { Association a ->
            !isAttributeAssociation(a)
        }
    }

    List<PersistentProperty> getAttributes(PersistentEntity entity) {
        entity.persistentProperties.findAll { PersistentProperty p ->
            if (p instanceof Association) {
                isAttributeAssociation((Association) p)
            } else {
                true
            }
        }
    }

    private void writeKey(Writer out, Object key) {
        out.write(generator.toJson(key))
        out.write(JsonOutput.COLON)
    }

    private void writeKeyValue(Writer out, Object key, Object value) {
        out.write(generator.toJson(key))
        out.write(JsonOutput.COLON)
        out.write(generator.toJson(value))
    }

    private void renderResource(Object object, Writer out) {
        renderResource(object, out, [:], "")
    }

    private void renderResource(Object object, Writer out, Map arguments, String basePath) {
        PersistentEntity entity = findEntity(object)

        if (entity == null) {
            throw new IllegalArgumentException("Rendering non persistent entities is not supported")
        }

        List<String> includes = getIncludes(arguments)
        List<String> excludes = getExcludes(arguments)
        boolean includeAssociations = includeAssociations(arguments)

        out.write(JsonOutput.OPEN_BRACE)

        writeKeyValue(out, 'type', entity.decapitalizedName)

        PersistentProperty identity = entity.identity
        String idName = identity?.name

        if(idName != null) {
            out.write(JsonOutput.COMMA)
            writeKeyValue(out, 'id', idGenerator.render(object, identity))
        }

        if (entity.persistentProperties) {
            List<PersistentProperty> attributes = getAttributes(entity)
            List<Association> relationships = getRelationships(entity)

            if (attributes) {
                out.write(JsonOutput.COMMA)
                out.write(generator.toJson("attributes"))
                out.write(JsonOutput.COLON)
                out.write(JsonOutput.OPEN_BRACE)

                boolean firstAttribute = true
                for (persistentProperty in attributes) {
                    if (!includeExcludeSupport.shouldInclude(includes, excludes, "${basePath}${persistentProperty.name}".toString())) continue

                    if (!firstAttribute) {
                        out.write(JsonOutput.COMMA)
                    }

                    out.write(generator.toJson(persistentProperty.name))
                    out.write(JsonOutput.COLON)

                    Object prop = ((GroovyObject) object).getProperty(persistentProperty.name)
                    if (persistentProperty instanceof Embedded) {
                        renderEmbeddedEntity(prop, (Association)persistentProperty, out, "${basePath}${persistentProperty.name}.".toString(), includes, excludes)
                    } else if (persistentProperty instanceof EmbeddedCollection && prop instanceof Iterable) {
                        out.write(JsonOutput.OPEN_BRACKET)
                        Iterator iterator = ((Iterable) prop).iterator()
                        while (iterator.hasNext()) {
                            def o = iterator.next()
                            renderEmbeddedEntity(o, (Association)persistentProperty, out, "${basePath}${persistentProperty.name}.".toString(), includes, excludes)
                            if (iterator.hasNext()) {
                                out.write(JsonOutput.COMMA)
                            }
                        }
                        out.write(JsonOutput.CLOSE_BRACKET)
                    } else {
                        out.write(generator.toJson(((GroovyObject) object).getProperty(persistentProperty.name)))
                    }

                    firstAttribute = false
                }
                out.write(JsonOutput.CLOSE_BRACE)
            }
            if (relationships && includeAssociations) {

                out.write(JsonOutput.COMMA)
                out.write(generator.toJson("relationships"))
                out.write(JsonOutput.COLON)
                out.write(JsonOutput.OPEN_BRACE)
                boolean firstRelationship = true

                for (association in relationships) {
                    if (!includeExcludeSupport.shouldInclude(includes, excludes, "${basePath}${association.name}".toString())) continue

                    def value = ((GroovyObject) object).getProperty(association.name)
                    if (!firstRelationship) {
                        out.write(JsonOutput.COMMA)
                    }
                    firstRelationship = false
                    out.write(generator.toJson(association.name))
                    out.write(JsonOutput.COLON)
                    out.write(JsonOutput.OPEN_BRACE)

                    if (association instanceof ToOne && value != null) {
                        renderRelationshipLinks(value).writeTo(out)
                        out.write(JsonOutput.COMMA)
                    }

                    out.write(generator.toJson("data"))
                    out.write(JsonOutput.COLON)
                    PersistentEntity associatedEntity = association.associatedEntity
                    if (association instanceof ToMany && Iterable.isAssignableFrom(association.type)) {
                        out.write(JsonOutput.OPEN_BRACKET)
                        if (value != null) {
                            Iterator iterator = ((Iterable) value).iterator()
                            String type = associatedEntity.decapitalizedName

                            while (iterator.hasNext()) {
                                def o = iterator.next()
                                out.write(JsonOutput.OPEN_BRACE)
                                writeKeyValue(out, 'type', type)
                                out.write(JsonOutput.COMMA)
                                writeKeyValue(out, 'id', idGenerator.render(o, associatedEntity.identity))
                                out.write(JsonOutput.CLOSE_BRACE)
                                if (iterator.hasNext()) {
                                    out.write(JsonOutput.COMMA)
                                }
                            }
                        }

                        out.write(JsonOutput.CLOSE_BRACKET)

                    } else {
                        if (value != null) {
                            out.write(JsonOutput.OPEN_BRACE)

                            out.write(generator.toJson("type"))
                            out.write(JsonOutput.COLON)
                            out.write(generator.toJson(associatedEntity.decapitalizedName))
                            out.write(JsonOutput.COMMA)

                            out.write(generator.toJson("id"))
                            out.write(JsonOutput.COLON)
                            out.write(generator.toJson(idGenerator.render(value, associatedEntity.identity)))

                            out.write(JsonOutput.CLOSE_BRACE)
                        } else {
                            NULL_OUTPUT.writeTo(out)
                        }
                    }
                    out.write(JsonOutput.CLOSE_BRACE)
                }
                out.write(JsonOutput.CLOSE_BRACE)
            }
        }

        if (basePath != "") {
            out.write(JsonOutput.COMMA)
            renderRelationshipLinks(object).writeTo(out)
        }
        out.write(JsonOutput.CLOSE_BRACE)
    }

    private void renderEmbeddedEntity(Object object, Association property, Writer out, String basePath, List<String> includes, List<String> excludes) {
        PersistentEntity persistentEntity = property.getAssociatedEntity()
        out.write(JsonOutput.OPEN_BRACE)
        boolean firstAttribute = true
        for (PersistentProperty prop: persistentEntity.getPersistentProperties()) {
            String qualified = "${basePath}${prop.name}".toString()

            if (!includeExcludeSupport.shouldInclude(includes, excludes, qualified)) {
                continue
            }

            if (!firstAttribute) {
                out.write(JsonOutput.COMMA)
            }

            out.write(generator.toJson(prop.name))
            out.write(JsonOutput.COLON)
            out.write(generator.toJson(((GroovyObject) object).getProperty(prop.name)))


            firstAttribute = false
        }
        out.write(JsonOutput.CLOSE_BRACE)
    }

    private JsonOutput.JsonWritable renderData(Object object, Map arguments) {
        JsonGenerator generator = getGenerator()
        new JsonOutput.JsonWritable() {
            @Override
            Writer writeTo(Writer out) throws IOException {
                out.write(generator.toJson("data"))
                out.write(JsonOutput.COLON)

                if (object instanceof Collection) {
                    out.write(JsonOutput.OPEN_BRACKET)
                    boolean first = true
                    for (o in object) {
                        if (!first) {
                            out.write(JsonOutput.COMMA)
                        }
                        first = false
                        renderResource(o, out, arguments, "")
                    }
                    out.write(JsonOutput.CLOSE_BRACKET)
                } else {
                    renderResource(object, out, arguments, "")
                }
                out
            }
        }
    }

    JsonOutput.JsonWritable renderErrors(Object object) {
        JsonGenerator generator = getGenerator()
        JsonOutput.JsonWritable writable = new JsonOutput.JsonWritable() {

            @Override
            Writer writeTo(Writer out) throws IOException {
                out.write(generator.toJson("errors"))
                out.write(JsonOutput.COLON)

                Errors errors = (Errors) object.getAt('errors')

                out.write(JsonOutput.OPEN_BRACKET)

                List<ObjectError> allErrors = errors.allErrors
                allErrors.eachWithIndex { ObjectError error, int idx ->
                    this.writeError(out, error)
                    if (idx < allErrors.size() - 1) {
                        out.write(JsonOutput.COMMA)
                    }
                }

                out.write(JsonOutput.CLOSE_BRACKET)

                return out
            }

            protected writeError(Writer out, ObjectError error) {
                out.write(JsonOutput.OPEN_BRACE)
                out.write(generator.toJson("code"))
                out.write(JsonOutput.COLON)
                out.write(generator.toJson(error.code))
                out.write(JsonOutput.COMMA)

                out.write(generator.toJson("detail"))
                out.write(JsonOutput.COLON)
                out.write(generator.toJson(message([error: error])))
                out.write(JsonOutput.COMMA)

                out.write(generator.toJson("source"))
                out.write(JsonOutput.COLON)
                out.write(JsonOutput.OPEN_BRACE)

                out.write(generator.toJson("object"))
                out.write(JsonOutput.COLON)
                out.write(generator.toJson(error.getObjectName()))
                out.write(JsonOutput.COMMA)

                if (error instanceof FieldError) {
                    FieldError fieldError = (FieldError) error

                    out.write(generator.toJson("field"))
                    out.write(JsonOutput.COLON)
                    out.write(generator.toJson(fieldError.getField()))
                    out.write(JsonOutput.COMMA)

                    out.write(generator.toJson("rejectedValue"))
                    out.write(JsonOutput.COLON)
                    out.write(generator.toJson(fieldError.getRejectedValue()))
                    out.write(JsonOutput.COMMA)

                    out.write(generator.toJson("bindingError"))
                    out.write(JsonOutput.COLON)
                    out.write(generator.toJson(fieldError.isBindingFailure()))
                }

                out.write(JsonOutput.CLOSE_BRACE)//source
                out.write(JsonOutput.CLOSE_BRACE)//error
            }
        }
        return writable
    }

    JsonOutput.JsonWritable renderRelationshipLinks(Object object) {
        JsonGenerator generator = getGenerator()
        new JsonOutput.JsonWritable() {
            @Override
            Writer writeTo(Writer out) throws IOException {
                out.write(generator.toJson("links"))
                out.write(JsonOutput.COLON)
                out.write(JsonOutput.OPEN_BRACE)
                out.write(generator.toJson("self"))
                out.write(JsonOutput.COLON)
                out.write(generator.toJson(view.linkGenerator.link(resource: object, method: HttpMethod.GET)))
                out.write(JsonOutput.CLOSE_BRACE)
                out
            }
        }
    }

    JsonOutput.JsonWritable renderLinks(Object object, Map arguments) {
        JsonGenerator generator = getGenerator()
        JsonOutput.JsonWritable writable = new JsonOutput.JsonWritable() {

            @Override
            Writer writeTo(Writer out) throws IOException {

                out.write(generator.toJson("links"))
                out.write(JsonOutput.COLON)

                out.write(JsonOutput.OPEN_BRACE)
                out.write(generator.toJson("self"))
                out.write(JsonOutput.COLON)

                if (object instanceof Collection) {
                    out.write(generator.toJson(view.request.uri))

                    if (arguments.get(PAGINATION) instanceof Map) {
                        Map paginationArgs = (Map)arguments.get(PAGINATION)
                        if (!paginationArgs.containsKey(PAGINATION_TOTAL) || !paginationArgs.containsKey(PAGINATION_RESROUCE)) {
                            throw new IllegalArgumentException("JSON API pagination arguments must contain resource and total")
                        }
                        Integer total = (Integer)paginationArgs.get(PAGINATION_TOTAL)
                        Object resource = paginationArgs.get(PAGINATION_RESROUCE)
                        Parameters params = defaultPaginateParams(paginationArgs)
                        List<Link> links = getPaginationLinks(resource, total, params)
                        for(link in links) {
                            out.write(JsonOutput.COMMA)
                            writeKeyValue(out, link.rel, link.href)
                        }
                    }
                } else {
                    out.write(generator.toJson(view.linkGenerator.link(resource: object, method: HttpMethod.GET)))
                }

                out.write(JsonOutput.CLOSE_BRACE)
                return out
            }
        }
        return writable
    }

    JsonOutput.JsonWritable renderIncluded(Object object, Map arguments) {

        List<String> expandProperties = getExpandProperties((JsonView)view, arguments)
        if (!expandProperties.empty && includeAssociations(arguments)) {

            new JsonOutput.JsonWritable() {

                @Override
                Writer writeTo(Writer out) throws IOException {
                    out.write(JsonOutput.COMMA)
                    writeKey(out, "included")
                    out.write(JsonOutput.OPEN_BRACKET)
                    boolean first = true

                    for (String prop in expandProperties) {
                        if (!first) {
                            out.write(JsonOutput.COMMA)
                        }
                        Object itemToInclude = object.getAt(prop)

                        if (itemToInclude instanceof Collection) {
                            for (o in itemToInclude) {
                                if (!first) {
                                    out.write(JsonOutput.COMMA)
                                }
                                first = false
                                renderResource(o, out, arguments, "${prop}.")
                            }
                        } else {
                            renderResource(itemToInclude, out, arguments, "${prop}.")
                        }
                        first = false
                    }
                    out.write(JsonOutput.CLOSE_BRACKET)
                    out
                }
            }

        } else {
            return NOOP_OUTPUT
        }


    }

    void renderMetaObject(Writer out, Object meta) {
        writeKey(out, "meta")
        viewHelper.render(meta, [:]).writeTo(out)
    }

    void renderJsonApiMember(Writer out, Object meta) {
        writeKey(out, "jsonapi")
        out.write(JsonOutput.OPEN_BRACE)
        writeKeyValue(out, 'version', '1.0')
        if (meta != null) {
            out.write(JsonOutput.COMMA)
            renderMetaObject(out, meta)
        }
        out.write(JsonOutput.CLOSE_BRACE)
    }

    void renderException(Writer out, Throwable object) {
        JsonGenerator generator = getGenerator()

        StackTraceUtils.sanitize(object)
        out.write(generator.toJson("errors"))
        out.write(JsonOutput.COLON)
        out.write(JsonOutput.OPEN_BRACKET)
        out.write(JsonOutput.OPEN_BRACE)
        writeKeyValue(out, 'status', 500)
        out.write(JsonOutput.COMMA)
        writeKeyValue(out, 'title', object.class.name)
        out.write(JsonOutput.COMMA)
        writeKeyValue(out, 'detail', object.localizedMessage)
        out.write(JsonOutput.COMMA)
        out.write(generator.toJson("source"))
        out.write(JsonOutput.COLON)
        out.write(JsonOutput.OPEN_BRACE)
        writeKeyValue(out, 'stacktrace', getJsonStackTrace(object))
        out.write(JsonOutput.CLOSE_BRACE)//source
        out.write(JsonOutput.CLOSE_BRACE)//error
        out.write(JsonOutput.CLOSE_BRACKET)
    }

    JsonApiIdRenderStrategy getIdGenerator() {
        ((JsonView)view).jsonApiIdRenderStrategy
    }
}
