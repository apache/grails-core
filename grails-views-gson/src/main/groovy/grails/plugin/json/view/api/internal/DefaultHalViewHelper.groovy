package grails.plugin.json.view.api.internal

import grails.gorm.PagedResultList
import grails.plugin.json.builder.JsonGenerator
import grails.plugin.json.builder.JsonOutput
import grails.plugin.json.builder.StreamingJsonBuilder
import grails.plugin.json.builder.StreamingJsonBuilder.StreamingJsonDelegate
import grails.plugin.json.view.api.GrailsJsonViewHelper
import grails.plugin.json.view.api.HalViewHelper
import grails.plugin.json.view.api.JsonView
import grails.rest.Link
import grails.util.GrailsNameUtils
import grails.views.WritableScriptTemplate
import grails.views.api.GrailsView
import grails.views.api.HttpView
import grails.views.api.http.Parameters
import grails.views.utils.ViewUtils
import grails.web.mime.MimeType
import groovy.transform.CompileDynamic
import groovy.transform.CompileStatic
import org.grails.core.util.IncludeExcludeSupport
import org.grails.datastore.mapping.model.MappingContext
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.*
import org.grails.datastore.mapping.reflect.EntityReflector
import org.springframework.http.HttpMethod

import java.lang.reflect.Field

/**
 * Helps creating HAL links
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@CompileStatic
class DefaultHalViewHelper extends DefaultJsonViewHelper implements HalViewHelper {

    public static final String LINKS_ATTRIBUTE = "_links"
    public static final String SELF_ATTRIBUTE = "self"
    public static final String HREF_ATTRIBUTE = "href"
    public static final String HREFLANG_ATTRIBUTE = "hreflang"
    public static final String TYPE_ATTRIBUTE = "type"
    public static final String EMBEDDED_ATTRIBUTE = "_embedded"
    public static final String EMBEDDED_PARAMETER = "embedded"


    private StreamingJsonDelegate jsonDelegate

    GrailsJsonViewHelper viewHelper
    String contentType = MimeType.HAL_JSON.name

    DefaultHalViewHelper(JsonView view, GrailsJsonViewHelper viewHelper) {
        super(view)
        this.viewHelper = viewHelper
    }

    //TODO: Once GROOVY-9662 is fixed, remove explicit delegate call and typecast to StreamingJsonDelegate
    /**
     * Same as {@link GrailsJsonViewHelper#render(java.lang.Object, java.util.Map, groovy.lang.Closure)} but renders HAL links too
     */
    @Override
    JsonOutput.JsonWritable render(Object object, Map arguments, @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure customizer) {
        if(arguments == null) {
            arguments = new LinkedHashMap()
        }

        if(!arguments.containsKey("beforeClosure")) {
            arguments.put("beforeClosure", createlinksRenderingClosure(arguments))
        }

        JsonOutput.JsonWritable jsonWritable = viewHelper.render(object, arguments, customizer)
        if(object instanceof Iterable) {
            Iterable iterable = (Iterable)object
            int size = iterable.size()
            Object firstObject = size > 0 ? iterable.first() : null
            DefaultHalViewHelper helper = this
            JsonGenerator generator = getGenerator()
            return new JsonOutput.JsonWritable() {
                @Override
                Writer writeTo(Writer out) throws IOException {
                    StreamingJsonBuilder builder = new StreamingJsonBuilder(out, generator)
                    builder.call {
                        helper.setDelegate((StreamingJsonDelegate) delegate)
                        if(firstObject != null) {
                            helper.links(GrailsNameUtils.getPropertyName(firstObject.getClass()) )
                        }
                        call(EMBEDDED_ATTRIBUTE, jsonWritable)

                        if(iterable instanceof PagedResultList) {
                            call("totalCount", ((PagedResultList)iterable).getTotalCount())
                        }
                    }
                    return out
                }
            }
        } else {
            return jsonWritable
        }
    }


    @Override
    JsonOutput.JsonWritable render(Object object, Map arguments) {
        render(object, arguments, null)
    }

    /**
     * Same as {@link GrailsJsonViewHelper#render(java.lang.Object, java.util.Map, groovy.lang.Closure)} but renders HAL links too
     */
    @Override
    JsonOutput.JsonWritable render(Object object,  @DelegatesTo(StreamingJsonBuilder.StreamingJsonDelegate) Closure customizer ) {
        render(object, (Map)null, customizer)
    }

    @Override
    JsonOutput.JsonWritable render(Object object) {
        render(object, (Map)null, (Closure)null)
    }

    @Override
    void inline(Object object, Map arguments = [:], @DelegatesTo(StreamingJsonDelegate) Closure customizer = null) {
        arguments.put(ASSOCIATIONS, false)
        viewHelper.inline(object, arguments, customizer, jsonDelegate)
    }


    @Override
    void inline(Object object, @DelegatesTo(StreamingJsonDelegate) Closure customizer) {
        inline(object, [:], customizer)
    }

    /**
     * @param name Sets the HAL response type
     */
    @Override
    void type(String name) {
        def mimeType = view.mimeUtility?.getMimeTypeForExtension(name)
        this.contentType = mimeType?.name ?: name
        if(view instanceof HttpView) {
            ((HttpView)view).response?.contentType(contentType)
        }
    }

    /**
     * Define the hal links
     *
     * @param callable The closure
     */
    @Override
    void links(Closure callable) {
        jsonDelegate.call(LINKS_ATTRIBUTE) {

            callable.setDelegate(new HalStreamingJsonDelegate(this, (StreamingJsonDelegate)delegate))
            callable.call()
        }
    }
    /**
     * Creates HAL links for the given object
     *
     * @param object The object to create links for
     */
    void links(Object object, String contentType = this.contentType) {
        writeLinks(jsonDelegate, object, contentType)
    }

    //TODO: Once GROOVY-9662 is fixed, remove explicit delegate call and typecast to StreamingJsonDelegate
    void links(Map model, Object paginationObject, Number total, String contentType = this.contentType) {
        def jsonView = view
        jsonDelegate.call(LINKS_ATTRIBUTE) {
            def linkGenerator = jsonView.linkGenerator
            def locale = jsonView.locale
            for(entry in model.entrySet()) {
                def object = entry.value
                if(object instanceof Iterable) {
                    ((StreamingJsonDelegate) delegate).call(entry.key.toString(), (Iterable)object) { o ->
                        ((StreamingJsonDelegate) delegate).call(HREF_ATTRIBUTE, linkGenerator.link(resource:o, absolute:true))
                        if(locale != null) {
                            ((StreamingJsonDelegate) delegate).call(HREFLANG_ATTRIBUTE, locale.toString())
                        }
                        def linkType = contentType
                        if (linkType) {
                            ((StreamingJsonDelegate) delegate).call(TYPE_ATTRIBUTE, linkType)
                        }
                    }
                }
                else if(object instanceof Map) {
                    ((StreamingJsonDelegate) delegate).call(entry.key.toString(), (Map) object)
                }
                else {
                    ((StreamingJsonDelegate) delegate).call(entry.key.toString()) {
                        ((StreamingJsonDelegate) delegate).call(HREF_ATTRIBUTE, linkGenerator.link(resource:object, absolute:true))
                        if(locale != null) {
                            ((StreamingJsonDelegate) delegate).call(HREFLANG_ATTRIBUTE, locale.toString())
                        }
                        def linkType = contentType
                        if (linkType) {
                            ((StreamingJsonDelegate) delegate).call(TYPE_ATTRIBUTE, linkType)
                        }
                    }
                }

                if(paginationObject != null) {
                    List<Link> links = getPaginationLinks(paginationObject, total.intValue(), jsonView.params) as List<Link>
                    for(link in links) {
                        ((StreamingJsonDelegate) delegate).call(link.rel) {
                            ((StreamingJsonDelegate) delegate).call(HREF_ATTRIBUTE, link.href)
                            ((StreamingJsonDelegate) delegate).call(HREFLANG_ATTRIBUTE, link.hreflang?.toString() ?: locale.toString())
                            def linkType = link.contentType
                            if(linkType) {
                                ((StreamingJsonDelegate) delegate).call(TYPE_ATTRIBUTE, linkType)
                            }
                        }
                    }

                }
            }
        }
    }

    void links(Map model, String contentType = this.contentType) {
        links(model, null, 0)
    }

    /**
     * Pagination support which outputs hal links to the resulting pages
     *
     * @param object The object to create links for
     * @param total The total number of objects to be paginated
     * @param offset The numerical offset where the page starts (defaults to 0)
     * @param max The maximum number of objects to be shown (defaults to 10)
     * @param sort The field to sort on (defaults to null)
     * @param order The order in which the results are to be sorted eg: DESC or ASC
     */
    //TODO: Once GROOVY-9662 is fixed, remove explicit delegate call and typecast to StreamingJsonDelegate
    void paginate(Object object, Integer total, Integer offset = null, Integer max = null,  String sort = null, String order = null) {
        Map<String, Object> linkParams = buildPaginateParams(max, offset, sort, order)

        GrailsView jsonView = view
        Parameters httpParams = jsonView.params
        offset = offset ?: httpParams.int(PAGINATION_OFFSET, 0)
        max = max ?: httpParams.int(PAGINATION_MAX, 10)
        sort = sort ?: httpParams.get(PAGINATION_SORT)
        order = order ?: httpParams.get(PAGINATION_ORDER)

        String contentType = this.contentType
        MimeType contentTypeMimeType = jsonView.mimeUtility?.getMimeTypeForExtension(contentType)
        Locale locale = jsonView.locale ?: Locale.ENGLISH
        jsonDelegate.call(LINKS_ATTRIBUTE) {
            ((StreamingJsonDelegate) delegate).call(SELF_ATTRIBUTE) {
                ((StreamingJsonDelegate) delegate).call(HREF_ATTRIBUTE, viewHelper.link(resource:object, method: HttpMethod.GET, absolute:true, params: linkParams))  //TODO handle the max/offset here
                ((StreamingJsonDelegate) delegate).call(HREFLANG_ATTRIBUTE, locale.toString())
                ((StreamingJsonDelegate) delegate).call(TYPE_ATTRIBUTE, contentTypeMimeType ?: contentType)
            }
            List<Link> links = getPaginationLinks(object, total, max, offset, sort, order) as List<Link>
            for(link in links) {
                ((StreamingJsonDelegate) delegate).call(link.rel) {
                    ((StreamingJsonDelegate) delegate).call(HREF_ATTRIBUTE, link.href)
                    ((StreamingJsonDelegate) delegate).call(HREFLANG_ATTRIBUTE, link.hreflang?.toString() ?: locale.toString())
                    def linkType = link.contentType
                    if(linkType) {
                        ((StreamingJsonDelegate) delegate).call(TYPE_ATTRIBUTE, linkType)
                    }
                }
            }

        }
    }

    /**
     * Renders embedded associations for the given entity
     *
     * @param object The object
     * @param arguments Any arguments. Supported arguments: 'includes', 'excludes', 'deep'
     */
    void embedded(Object object, Map arguments = [:]) {

        MappingContext mappingContext = view.mappingContext
        def proxyHandler = mappingContext.proxyHandler
        object = proxyHandler != null ? proxyHandler.unwrap(object) : object
        PersistentEntity entity = findEntity(object)

        List<String> incs = getIncludes(arguments)
        List<String> excs = getExcludes(arguments)
        List<String> expandProperties = getExpandProperties((JsonView)view, arguments)
        boolean deep = ViewUtils.getBooleanFromMap(GrailsJsonViewHelper.DEEP, arguments)
        arguments.put(IncludeExcludeSupport.EXCLUDES_PROPERTY, excs)
        if(entity != null) {
            EntityReflector entityReflector = mappingContext.getEntityReflector(entity)
            List<Association> associations = entity.associations
            Map<Association, Object> embeddedValues = [:]
            for(Association association in associations) {
                if(!association.isEmbedded()) {
                    def propertyName = association.name
                    if (includeExcludeSupport.shouldInclude(incs, excs, propertyName)) {
                        def value = entityReflector.getProperty(object, propertyName)
                        if(value != null) {

                            if(association instanceof ToMany && !( association instanceof Basic)) {
                                if(deep || expandProperties.contains(propertyName) || proxyHandler == null || proxyHandler.isInitialized(value)) {
                                    embeddedValues.put((Association) association, value)
                                }
                                excs.add(propertyName)
                            }
                            else if(association instanceof ToOne) {
                                if(deep || expandProperties.contains(propertyName) || proxyHandler == null || proxyHandler.isInitialized(value)) {
                                    embeddedValues.put((Association) association, value)
                                }
                                excs.add(propertyName)
                            }
                        }
                    }
                }
            }

            if(!embeddedValues.isEmpty()) {
                embedded {
                    StreamingJsonBuilder.StreamingJsonDelegate jsonDelegate = (StreamingJsonBuilder.StreamingJsonDelegate)getDelegate()
                    for(entry in embeddedValues) {
                        def embeddedObject = entry.value
                        Association association = entry.key
                        def associatedEntity = association.associatedEntity
                        def associationReflector = mappingContext.getEntityReflector(associatedEntity)
                        def propertyName = association.name
                        if(association instanceof ToOne) {
                            jsonDelegate.call(propertyName) {
                                def associationJsonDelegate = (StreamingJsonDelegate) getDelegate()
                                writeLinks(associationJsonDelegate, embeddedObject, this.contentType)
                                renderEntityProperties(associatedEntity, embeddedObject, associationReflector, associationJsonDelegate)
                            }

                        }
                        else if(association instanceof ToMany) {
                            if(embeddedObject instanceof Iterable) {
                                jsonDelegate.call(propertyName, (Iterable)embeddedObject) { e ->
                                    def associationJsonDelegate = (StreamingJsonDelegate) getDelegate()
//                                    writeLinks(associationJsonDelegate, e, this.contentType)
                                    renderEntityProperties(associatedEntity, e, associationReflector, associationJsonDelegate)
                                }
                            }
                        }
                    }
                }
            }
        }

    }

    /**
     * Render the given embedded objects
     *
     * @param model The embedded model
     */
    @Override
    void embedded(Map model) {
        if(!model?.isEmpty()) {
            MappingContext mappingContext = view.mappingContext
            def proxyHandler = mappingContext.proxyFactory

            embedded {
                StreamingJsonBuilder.StreamingJsonDelegate jsonDelegate = (StreamingJsonBuilder.StreamingJsonDelegate)getDelegate()
                for(entry in model.entrySet()) {
                    Object embeddedObject = proxyHandler.unwrap( entry.value )

                    if(Iterable.isInstance(embeddedObject)) {
                        jsonDelegate.call(entry.key.toString(), (Iterable)embeddedObject) { e ->
                            PersistentEntity entity = mappingContext.getPersistentEntity(e.getClass().name)
                            def entityReflector = mappingContext.getEntityReflector(entity)
                            def associationJsonDelegate = (StreamingJsonDelegate) getDelegate()
                            writeLinks(associationJsonDelegate, e, this.contentType)
                            renderEntityProperties(entity, e, entityReflector, associationJsonDelegate)
                        }
                    }
                    else {
                        PersistentEntity entity = mappingContext.getPersistentEntity(embeddedObject.getClass().name)
                        if(entity != null) {
                            def entityReflector = mappingContext.getEntityReflector(entity)
                            jsonDelegate.call(entry.key.toString()) {
                                if(entity != null) {
                                    def associationJsonDelegate = (StreamingJsonDelegate) getDelegate()
                                    writeLinks(associationJsonDelegate, embeddedObject, this.contentType)
                                    renderEntityProperties(entity, embeddedObject, entityReflector, associationJsonDelegate)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    //TODO: Once GROOVY-9662 is fixed, remove explicit delegate call and typecast to StreamingJsonDelegate
    protected void writeLinks(StreamingJsonDelegate jsonDelegate, object, String contentType) {
        def locale = view.locale ?: Locale.ENGLISH
        contentType = view.mimeUtility?.getMimeTypeForExtension(contentType) ?: contentType
        jsonDelegate.call(LINKS_ATTRIBUTE) {
            ((StreamingJsonDelegate) delegate).call(SELF_ATTRIBUTE) {
                ((StreamingJsonDelegate) delegate).call(HREF_ATTRIBUTE, viewHelper.link(resource: object, method: HttpMethod.GET, absolute: true))
                ((StreamingJsonDelegate) delegate).call(HREFLANG_ATTRIBUTE, locale.toString())
                ((StreamingJsonDelegate) delegate).call(TYPE_ATTRIBUTE, contentType)
            }

            Set<Link> links = getLinks(object) as Set<Link>
            for (link in links) {
                ((StreamingJsonDelegate) delegate).call(link.rel) {
                    ((StreamingJsonDelegate) delegate).call(HREF_ATTRIBUTE, link.href)
                    ((StreamingJsonDelegate) delegate).call(HREFLANG_ATTRIBUTE, link.hreflang?.toString() ?: locale.toString())
                    def linkType = link.contentType
                    if (linkType) {
                        ((StreamingJsonDelegate) delegate).call(TYPE_ATTRIBUTE, linkType)
                    }
                }
            }
        }
    }

    protected void renderEntityProperties(PersistentEntity entity, Object instance, EntityReflector entityReflector, StreamingJsonDelegate associationJsonDelegate) {
        for (prop in entity.persistentProperties) {
            renderProperty(instance, prop, entityReflector, associationJsonDelegate)
        }
    }

    protected void renderProperty(Object embeddedObject, PersistentProperty prop,  EntityReflector associationReflector, StreamingJsonBuilder.StreamingJsonDelegate jsonDelegate) {
        def propertyName = prop.name
        def propVal = associationReflector.getProperty(embeddedObject, propertyName)
        def propertyType = prop.type
        if (propVal != null) {
            if ((prop instanceof Simple) || (prop instanceof Basic)) {
                if (((DefaultGrailsJsonViewHelper)viewHelper).isStringType(propertyType)) {
                    jsonDelegate.call(propertyName, propVal.toString())
                } else {
                    jsonDelegate.call(propertyName, propVal)
                }
            } else if (prop instanceof Custom) {
                def childTemplate = view.templateEngine.resolveTemplate(propertyType, view.locale)
                if (childTemplate != null) {
                    JsonOutput.JsonWritable jsonWritable = ((DefaultGrailsJsonViewHelper) viewHelper).renderChildTemplate(childTemplate, propertyType, propVal)
                    jsonDelegate.call(propertyName, jsonWritable)
                } else {
                    jsonDelegate.call(propertyName, propVal)
                }
            } else if(prop instanceof Embedded) {
                Embedded embedded = (Embedded)prop
                def childTemplate = view.templateEngine.resolveTemplate(propertyType, view.locale)
                if (childTemplate != null) {
                    JsonOutput.JsonWritable jsonWritable = ((DefaultGrailsJsonViewHelper) viewHelper).renderChildTemplate(childTemplate, propertyType, propVal)
                    jsonDelegate.call(propertyName, jsonWritable)
                } else {
                    jsonDelegate.call(propertyName) {
                        def associationJsonDelegate = (StreamingJsonDelegate) getDelegate()
                        def associatedEntity = embedded.associatedEntity
                        def embeddedReflector = associatedEntity.getMappingContext().getEntityReflector(associatedEntity)
                        renderEntityProperties(associatedEntity, propVal, embeddedReflector , associationJsonDelegate)
                    }
                }
            } else if(prop instanceof EmbeddedCollection) {
                EmbeddedCollection embedded = (EmbeddedCollection)prop
                PersistentEntity associatedEntity = embedded.associatedEntity
                Class associatedType = associatedEntity.javaClass
                EntityReflector embeddedReflector = associatedEntity.getMappingContext().getEntityReflector(associatedEntity)
                WritableScriptTemplate childTemplate = view.templateEngine.resolveTemplate(associatedType, view.locale)
                if (childTemplate != null) {
                    if(propVal instanceof Iterable) {
                        List<JsonOutput.JsonWritable> childResults = ((Iterable) propVal).collect() { eo ->
                            ((DefaultGrailsJsonViewHelper) viewHelper).renderChildTemplate(childTemplate, associatedType, eo)
                        }.toList()
                        jsonDelegate.call(embedded.name, childResults as List<Object>)
                    }
                }
                else {
                    if(propVal instanceof Iterable) {
                        jsonDelegate.call(embedded.name, (Iterable)propVal) { eo ->
                            StreamingJsonDelegate associationJsonDelegate = (StreamingJsonDelegate) getDelegate()
                            renderEntityProperties(associatedEntity, eo, embeddedReflector , associationJsonDelegate)
                        }
                    }
                }
            }

        }
    }

    /**
     * Outputs a HAL embedded entry for the given closure
     *
     * @param callable The callable
     */
    void embedded(@DelegatesTo(StreamingJsonDelegate) Closure callable) {
        jsonDelegate.call(EMBEDDED_ATTRIBUTE) {

            callable.setDelegate(new HalStreamingJsonDelegate(this, (StreamingJsonDelegate)delegate))
            callable.call()
        }
    }

    /**
     * Outputs a HAL embedded entry for the content type and closure
     *
     * @param callable The callable
     */
    void embedded(String contentType, @DelegatesTo(StreamingJsonDelegate) Closure callable) {
        jsonDelegate.call(EMBEDDED_ATTRIBUTE) {

            callable.setDelegate(new HalStreamingJsonDelegate(contentType, this, (StreamingJsonDelegate)delegate))
            callable.call()
        }
    }

    @CompileDynamic
    private Set<Link> getLinks(Object o) {
        if(o.respondsTo("links")) {
            return o.links()
        }
        else {
            Collections.emptySet()
        }
    }

    static class HalStreamingJsonDelegate extends StreamingJsonDelegate {
        String contentType
        DefaultHalViewHelper viewHelper
        StreamingJsonDelegate delegate

        HalStreamingJsonDelegate(DefaultHalViewHelper viewHelper, StreamingJsonDelegate delegate) {
            super(delegate.getWriter(), true)
            this.viewHelper = viewHelper
            this.delegate = delegate
            this.contentType = viewHelper.contentType
        }

        HalStreamingJsonDelegate(String contentType, DefaultHalViewHelper viewHelper, StreamingJsonDelegate delegate) {
            super(delegate.getWriter(), true)
            this.viewHelper = viewHelper
            this.delegate = delegate
            this.contentType = contentType
        }

        @Override
        Object invokeMethod(String name, Object args) {
            Object[] arr = (Object[]) args
                switch(arr.length) {
                    case 1:
                        final Object value = arr[0]
                        if(value instanceof Closure) {
                            call(name, (Closure)value)
                        }
                        else {
                            call(name, value)
                        }
                        return null
                    case 2:
                        if(arr[-1] instanceof Closure) {
                            final Object obj = arr[0]
                            final Closure callable = (Closure) arr[1]
                            if(obj instanceof Iterable) {
                                call(name, (Iterable)obj, callable)
                                return null
                            }
                            else if(obj.getClass().isArray()) {
                                call(name, Arrays.asList(obj as Object[]), callable)
                                return null
                            }
                            else {
                                call(name, obj, callable)
                                return null
                            }
                        }
                    default:
                        return delegate.invokeMethod(name, args)
                }
        }

        @Override
        void call(String name, List<Object> list) throws IOException {
            first = false
            delegate.call(name, list)
        }

        @Override
        void call(String name, Object... array) throws IOException {
            first = false
            delegate.call(name, array)
        }

        @Override
        void call(String name, Iterable coll, Closure c) throws IOException {
            Field field = delegate.getClass().getDeclaredField("first")
            field.setAccessible(true)
            field.set(delegate, false)
            writeName(name)
            verifyValue()
            def w = writer
            w.write(JsonOutput.OPEN_BRACKET)
            boolean first = true
            for (Object it in coll) {
                if (!first) {
                    w.write(JsonOutput.COMMA)
                } else {
                    first = false
                }

                writeObject( it, c)
            }
            w.write(JsonOutput.CLOSE_BRACKET)
        }

        @Override
        void call(String name, Object value) throws IOException {
            first = false
            delegate.call(name, value)
        }

        @Override
        void call(String name, Object value,
                  @DelegatesTo(StreamingJsonDelegate.class) Closure callable) throws IOException {
            writeName(name)
            verifyValue()
            writeObject(value, callable)
        }

        protected void writeObject(value, Closure callable) {
            writer.write(JsonOutput.OPEN_BRACE)
            StreamingJsonDelegate delegate = new StreamingJsonDelegate(writer, true)
            Closure curried = callable.curry(value)
            curried.setDelegate(delegate)
            curried.setResolveStrategy(Closure.DELEGATE_FIRST)
            def oldDelegate = this.delegate
            viewHelper.setDelegate(delegate)
            viewHelper.links(value, contentType)
            curried.call()
            viewHelper.setDelegate(oldDelegate)
            writer.write(JsonOutput.CLOSE_BRACE)
        }

        @Override
        void call(String name, @DelegatesTo(value = StreamingJsonDelegate.class, strategy = Closure.DELEGATE_FIRST) Closure value) throws IOException {
            first = false
            delegate.call(name, value)
        }

        @Override
        void call(String name, JsonOutput.JsonUnescaped json) throws IOException {
            first = false
            delegate.call(name, json)
        }
    }

    @CompileDynamic
    protected Closure<Void> createlinksRenderingClosure(Map<String, Object> arguments = [:]) {
        def hal = this
        return { Object object ->
            StreamingJsonDelegate local = (StreamingJsonDelegate) getDelegate()
            hal.setDelegate(local)
            def shouldRenderEmbedded = ViewUtils.getBooleanFromMap(EMBEDDED_PARAMETER, arguments, true)
            if(shouldRenderEmbedded) {
                embedded(object, arguments)
            }
            links(object)
        }
    }

    @Override
    void setDelegate(StreamingJsonDelegate jsonDelegate) {
        this.jsonDelegate = jsonDelegate
    }
}
