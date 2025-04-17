package grails.plugin.json.view.api.internal

import grails.core.support.proxy.ProxyHandler
import grails.plugin.json.builder.JsonGenerator
import grails.plugin.json.builder.JsonOutput
import grails.plugin.json.builder.StreamingJsonBuilder
import grails.plugin.json.builder.StreamingJsonBuilder.StreamingJsonDelegate
import grails.plugin.json.view.api.GrailsJsonViewHelper
import grails.plugin.json.view.api.JsonView
import grails.plugin.json.view.template.JsonViewTemplate
import grails.util.GrailsNameUtils
import grails.validation.Validateable
import grails.views.ResolvableGroovyTemplateEngine
import grails.views.ViewException
import grails.views.ViewUriResolver
import grails.views.api.GrailsView
import grails.views.api.internal.DefaultGrailsViewHelper
import grails.views.resolve.TemplateResolverUtils
import grails.views.utils.ViewUtils
import groovy.text.Template
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import groovy.util.logging.Slf4j
import org.grails.buffer.FastStringWriter
import org.grails.core.util.IncludeExcludeSupport
import org.grails.datastore.gorm.GormValidateable
import org.grails.datastore.mapping.collection.PersistentCollection
import org.grails.datastore.mapping.model.IdentityMapping
import org.grails.datastore.mapping.model.MappingFactory
import org.grails.datastore.mapping.model.PersistentEntity
import org.grails.datastore.mapping.model.PersistentProperty
import org.grails.datastore.mapping.model.types.Association
import org.grails.datastore.mapping.model.types.Basic
import org.grails.datastore.mapping.model.types.Custom
import org.grails.datastore.mapping.model.types.Embedded
import org.grails.datastore.mapping.model.types.EmbeddedCollection
import org.grails.datastore.mapping.model.types.ToMany
import org.grails.datastore.mapping.model.types.ToOne
import org.grails.datastore.mapping.reflect.ClassPropertyFetcher

import java.beans.PropertyDescriptor


/**
 * Extended version of {@link DefaultGrailsViewHelper} with methods specific to JSON view rendering
 *
 * @author Graeme Rocher
 */
@CompileStatic
@InheritConstructors
@Slf4j
class DefaultGrailsJsonViewHelper extends DefaultJsonViewHelper implements GrailsJsonViewHelper {

    public static final String BEFORE_CLOSURE = "beforeClosure"
    public static final String PROCESSED_OBJECT_VARIABLE = "org.json.views.RENDER_PROCESSED_OBJECTS"

    @Override
    JsonOutput.JsonWritable render(Object object, @DelegatesTo(StreamingJsonDelegate) Closure customizer) {
        render object, Collections.emptyMap(), customizer
    }

    void inline(Object object, Map arguments = Collections.emptyMap(), @DelegatesTo(StreamingJsonDelegate) Closure customizer = null, StreamingJsonDelegate jsonDelegate) {
        JsonView jsonView = (JsonView)view
        Map<Object, JsonOutput.JsonWritable> processedObjects = initializeProcessedObjects(jsonView.binding)
        boolean isDeep = ViewUtils.getBooleanFromMap(DEEP, arguments)
        boolean includeAssociations = includeAssociations(arguments)
        List<String> expandProperties = getExpandProperties(jsonView, arguments)

        List<String> incs = getIncludes(arguments)
        List<String> excs = getExcludes(arguments)
        boolean renderNulls = getRenderNulls(arguments)

        def mappingContext = jsonView.mappingContext
        object = mappingContext.proxyHandler.unwrap(object)

        PersistentEntity entity = mappingContext.getPersistentEntity(object.getClass().name)

        if(entity != null) {
            process(jsonDelegate, entity, object, processedObjects, incs, excs, "", isDeep, renderNulls, expandProperties, includeAssociations, customizer)
        }
        else {
            processSimple(jsonDelegate, object, processedObjects, incs, excs, "", renderNulls, customizer)
        }
    }

    @Override
    void inline(Object object, Map arguments = Collections.emptyMap(), @DelegatesTo(StreamingJsonDelegate) Closure customizer = null) {
        def jsonDelegate = new StreamingJsonDelegate(view.out, true)
        inline(object, arguments, customizer, jsonDelegate)
    }

    @Override
    void inline(Object object, @DelegatesTo(StreamingJsonDelegate) Closure customizer) {
        inline(object, Collections.emptyMap(), customizer)
    }

    private JsonOutput.JsonWritable preProcessedOutput(Object object, Map<Object, JsonOutput.JsonWritable> processedObjects) {
        JsonView jsonView = (JsonView)view
        boolean rootRender = processedObjects.isEmpty()
        object = jsonView.proxyHandler?.unwrapIfProxy(object) ?: object
        if(object == null) {
            return NULL_OUTPUT
        }

        if(!rootRender && processedObjects.containsKey(object)) {
            def existingOutput = processedObjects.get(object)
            if(!NULL_OUTPUT.equals(existingOutput)) {
                return existingOutput
            }
        }
        return null
    }

    private boolean notCircular(JsonViewTemplate template) {
        template.templateClass != view.class
    }

    private JsonOutput.JsonWritable renderTemplate(Object value, Class type, String...qualifiers) {
        Locale locale = view.locale
        ProxyHandler proxyHandler = view.proxyHandler
        if (proxyHandler.isProxy(value) && proxyHandler.isInitialized(value)) {
            value = proxyHandler.unwrapIfProxy(value)
        }
        ResolvableGroovyTemplateEngine templateEngine = view.templateEngine
        JsonViewTemplate childTemplate = (JsonViewTemplate)templateEngine?.resolveTemplate(type, locale, qualifiers)
        if(childTemplate != null && notCircular(childTemplate)) {
            renderChildTemplate(childTemplate, type, value)
        } else {
            null
        }
    }

    private JsonOutput.JsonWritable renderTemplate(Object value, String...qualifiers) {
        ProxyHandler proxyHandler = view.proxyHandler
        if (proxyHandler.isProxy(value) && proxyHandler.isInitialized(value)) {
            value = proxyHandler.unwrapIfProxy(value)
        }
        renderTemplate(value, value.class, qualifiers)
    }

    private JsonOutput.JsonWritable renderTemplateOrDefault(Object object, Map arguments, Closure customizer, Map<Object, JsonOutput.JsonWritable> processedObjects, String path = "") {
        JsonOutput.JsonWritable preProcessed = preProcessedOutput(object, processedObjects)
        if (preProcessed != null) {
            return preProcessed
        }
        if (arguments == Collections.emptyMap() && customizer == null) {
            def template = renderTemplate(object)
            if(template != null) {
                return template
            }
        }
        renderDefault(object, arguments, customizer, processedObjects, path)
    }

    private JsonOutput.JsonWritable renderDefault(Object object, Map arguments, Closure customizer, Map<Object, JsonOutput.JsonWritable> processedObjects, String path = "") {
        JsonOutput.JsonWritable preProcessed = preProcessedOutput(object, processedObjects)
        if (preProcessed != null) {
            return preProcessed
        }

        JsonView jsonView = (JsonView)view
        boolean rootRender = processedObjects.isEmpty()
        def binding = jsonView.getBinding()
        def entity = findEntity(object)

        final boolean isDeep = ViewUtils.getBooleanFromMap(DEEP, arguments)
        List<String> expandProperties = getExpandProperties(jsonView, arguments)
        final Closure beforeClosure = (Closure)arguments.get(BEFORE_CLOSURE)
        boolean renderNulls = getRenderNulls(arguments)


        Closure doProcessEntity = { StreamingJsonBuilder.StreamingJsonDelegate jsonDelegate, List<String> incs, List<String> excs ->
            process(jsonDelegate, entity, object, processedObjects, incs, excs, path, isDeep, renderNulls, expandProperties, true, customizer)
        }

        Closure doProcessSimple = { StreamingJsonBuilder.StreamingJsonDelegate jsonDelegate, List<String> incs, List<String> excs ->
            processSimple(jsonDelegate, object, processedObjects, incs, excs, path, renderNulls, customizer)
        }

        JsonGenerator generator = getGenerator()
        def jsonWritable = new JsonOutput.JsonWritable() {
            @Override
            @CompileStatic
            Writer writeTo(Writer out) throws IOException {
                try {
                    if (entity != null) {

                        if(inline) {
                            StreamingJsonBuilder.StreamingJsonDelegate jsonDelegate = new StreamingJsonBuilder.StreamingJsonDelegate(out, first)
                            if (beforeClosure != null) {
                                beforeClosure.setDelegate(jsonDelegate)
                                beforeClosure.call()
                            }
                            List<String> incs = ViewUtils.getStringListFromMap(IncludeExcludeSupport.INCLUDES_PROPERTY, arguments, null)
                            List<String> excs = ViewUtils.getStringListFromMap(IncludeExcludeSupport.EXCLUDES_PROPERTY, arguments)

                            doProcessEntity(jsonDelegate,  incs, excs)

                        }
                        else {

                            StreamingJsonBuilder builder = new StreamingJsonBuilder(out, generator)
                            builder.call {
                                StreamingJsonDelegate jsonDelegate = (StreamingJsonDelegate) getDelegate()
                                if (beforeClosure != null) {
                                    beforeClosure.setDelegate(jsonDelegate)
                                    beforeClosure.call(object)
                                }
                                List<String> incs = ViewUtils.getStringListFromMap(IncludeExcludeSupport.INCLUDES_PROPERTY, arguments, null)
                                List<String> excs = ViewUtils.getStringListFromMap(IncludeExcludeSupport.EXCLUDES_PROPERTY, arguments)

                                doProcessEntity(jsonDelegate,  incs, excs)
                            }
                        }

                    } else {
                        if(inline) {
                            StreamingJsonBuilder.StreamingJsonDelegate jsonDelegate = new StreamingJsonBuilder.StreamingJsonDelegate(out, first)
                            if (beforeClosure != null) {
                                beforeClosure.setDelegate(jsonDelegate)
                                beforeClosure.call(object)
                            }
                            List<String> incs = ViewUtils.getStringListFromMap(IncludeExcludeSupport.INCLUDES_PROPERTY, arguments, null)
                            List<String> excs = ViewUtils.getStringListFromMap(IncludeExcludeSupport.EXCLUDES_PROPERTY, arguments)
                            doProcessSimple(jsonDelegate, incs, excs)
                        }
                        else {

                            StreamingJsonBuilder builder = new StreamingJsonBuilder(out, generator)
                            builder.call {
                                StreamingJsonDelegate jsonDelegate = (StreamingJsonDelegate) getDelegate()
                                if (beforeClosure != null) {
                                    beforeClosure.setDelegate(jsonDelegate)
                                    beforeClosure.call(object)
                                }
                                List<String> incs = ViewUtils.getStringListFromMap(IncludeExcludeSupport.INCLUDES_PROPERTY, arguments, null)
                                List<String> excs = ViewUtils.getStringListFromMap(IncludeExcludeSupport.EXCLUDES_PROPERTY, arguments)

                                doProcessSimple(jsonDelegate, incs, excs)
                            }
                        }
                    }


                    processedObjects.put(object, this)
                    return out
                } finally {

                    if (rootRender) {
                        binding.variables.remove(PROCESSED_OBJECT_VARIABLE)
                    }
                }
            }

        }

        return jsonWritable
    }

    protected JsonOutput.JsonWritable getIterableWritable(Iterable object, Map arguments, Closure customizer, Map<Object, JsonOutput.JsonWritable> processedObjects, String path = "") {
        return getIterableWritable(object) { Object o, Writer out ->
            handleValue(o, out, arguments, customizer, processedObjects, path)
        }

    }

    protected JsonOutput.JsonWritable getIterableWritable(Iterable object, Closure forEach) {
        return new JsonOutput.JsonWritable() {
            @Override
            Writer writeTo(Writer out) throws IOException {
                Iterable iterable = (Iterable)object
                boolean first = true
                out.append JsonOutput.OPEN_BRACKET
                for(o in iterable) {
                    if(!first) {
                        out.append JsonOutput.COMMA
                    }
                    forEach.call(o, out)
                    first = false
                }
                out.append JsonOutput.CLOSE_BRACKET
            }
        }
    }

    protected JsonOutput.JsonWritable getMapWritable(Map object, Map arguments, Closure customizer, Map<Object, JsonOutput.JsonWritable> processedObjects) {
        return new JsonOutput.JsonWritable() {

            @Override
            Writer writeTo(Writer out) throws IOException {
                List<String> incs = ViewUtils.getStringListFromMap(IncludeExcludeSupport.INCLUDES_PROPERTY, arguments, null)
                List<String> excs = ViewUtils.getStringListFromMap(IncludeExcludeSupport.EXCLUDES_PROPERTY, arguments)
                Map map = (Map)object
                boolean entryRendered = false

                out.append JsonOutput.OPEN_BRACE
                for(entry in map.entrySet()) {
                    if (!simpleIncludeExcludeSupport.shouldInclude(incs, excs, entry.key.toString())) {
                        continue
                    }

                    if (entryRendered) {
                        out.append JsonOutput.COMMA
                    }
                    out.append(JsonOutput.toJson(entry.key.toString()))
                    out.append(JsonOutput.COLON)
                    def value = entry.value
                    if (value instanceof Iterable) {
                        getIterableWritable(value, arguments, customizer, processedObjects, entry.key.toString() + ".").writeTo(out)
                    } else {
                        handleValue(value, out, arguments, customizer, processedObjects, entry.key.toString() + ".")
                    }
                    entryRendered = true
                }
                out.append JsonOutput.CLOSE_BRACE
                return out
            }
        }
    }

    protected void handleValue(Object value, Writer out, Map arguments, Closure customizer, Map<Object, JsonOutput.JsonWritable> processedObjects, String path = "") {
        if(isSimpleValue(value)) {
            out.append(generator.toJson((Object)value))
        }
        else {
            renderTemplateOrDefault(value, arguments, customizer, processedObjects, path).writeTo(out)
        }
    }

    @Override
    JsonOutput.JsonWritable render(Object object, Map arguments = Collections.emptyMap(), @DelegatesTo(StreamingJsonDelegate) Closure customizer = null ) {

        JsonView jsonView = (JsonView)view
        def binding = jsonView.getBinding()
        JsonGenerator generator = getGenerator()
        Map<Object, JsonOutput.JsonWritable> processedObjects = initializeProcessedObjects(binding)
        if(object instanceof Iterable) {
            return getIterableWritable((Iterable)object, arguments, customizer, processedObjects)
        }
        else if(object instanceof Map) {
            return getMapWritable((Map)object, arguments, customizer, processedObjects)
        }
        else if(object instanceof Throwable) {
            Throwable e = (Throwable)object
            List<Object> stacktrace = getJsonStackTrace(e)
            return new JsonOutput.JsonWritable() {
                @Override
                Writer writeTo(Writer out) throws IOException {
                    new StreamingJsonBuilder(out, generator).call {
                        StreamingJsonBuilder.StreamingJsonDelegate jsonDelegate = (StreamingJsonBuilder.StreamingJsonDelegate)getDelegate()
                        jsonDelegate.call("message", e.message)
                        jsonDelegate.call("stacktrace", stacktrace)
                    }
                    return out
                }
            }
        }
        else {
            return renderTemplateOrDefault(object, arguments, customizer, processedObjects)
        }
    }

    protected Map<Object, JsonOutput.JsonWritable> initializeProcessedObjects(Binding binding) {
        Map<Object, JsonOutput.JsonWritable> processedObjects

        if (binding.hasVariable(PROCESSED_OBJECT_VARIABLE)) {
            processedObjects = (Map<Object, JsonOutput.JsonWritable>) binding.getVariable(PROCESSED_OBJECT_VARIABLE)
        } else {
            processedObjects = new LinkedHashMap<Object, JsonOutput.JsonWritable>()
            binding.setVariable(PROCESSED_OBJECT_VARIABLE, processedObjects)
        }
        processedObjects
    }

    protected void processSimple(StreamingJsonBuilder.StreamingJsonDelegate jsonDelegate, Object object, Map<Object, JsonOutput.JsonWritable> processedObjects, List<String> incs, List<String> excs, String path, Boolean renderNulls, Closure customizer = null) {

        if(!processedObjects.containsKey(object)) {
            processedObjects.put(object, NULL_OUTPUT)


            def declaringClass = object.getClass()
            def cpf = ClassPropertyFetcher.forClass(declaringClass)
            def descriptors = cpf.getPropertyDescriptors()
            IncludeExcludeSupport includeExcludeSupport = (object instanceof GormValidateable || object instanceof Validateable) ? validateableIncludeExcludeSupport : simpleIncludeExcludeSupport
            for (PropertyDescriptor desc in descriptors) {
                def readMethod = desc.readMethod
                if (readMethod != null && desc.writeMethod != null) {
                    def propertyName = desc.name
                    String qualified = "${path}${propertyName}"
                    if (includeExcludeSupport.shouldInclude(incs, excs, qualified)) {
                        def value = cpf.getPropertyValue(object, desc.name)
                        if(value != null) {
                            def propertyType = desc.propertyType
                            boolean isArray = propertyType.isArray()
                            if(isStringType(propertyType)) {
                                jsonDelegate.call propertyName, value.toString()
                            }
                            else if(isSimpleType(propertyType, value)) {
                                jsonDelegate.call propertyName, value
                            }
                            else if(isArray || Iterable.isAssignableFrom(propertyType)) {
                                Class componentType

                                if(isArray) {
                                    componentType = propertyType.componentType
                                }
                                else {
                                    componentType = getGenericType(declaringClass, desc)
                                }

                                if(!Object.is(componentType) && MappingFactory.isSimpleType(componentType.name) || componentType.isEnum()) {
                                    jsonDelegate.call(propertyName, value)
                                }
                                else {
                                    Iterable iterable = isArray ? value as List : (Iterable)value
                                    jsonDelegate.call(propertyName, getIterableWritable(iterable) { Object o, Writer out ->
                                        if(isStringType(o.class)) {
                                            out.append(o.toString())
                                        }
                                        else if(isSimpleType(o.class, o)) {
                                            out.append(JsonOutput.toJson((Object)o))
                                        }
                                        else {
                                            out.append JsonOutput.OPEN_BRACE
                                            processSimple(new StreamingJsonDelegate(out, true), o, processedObjects, incs, excs,"${path}${propertyName}.", renderNulls)
                                            out.append JsonOutput.CLOSE_BRACE
                                        }
                                    })
                                }
                            }
                            else {
                                if(!processedObjects.containsKey(value)) {
                                    def template = renderTemplate(value, propertyType)
                                    if(template != null) {
                                        jsonDelegate.call(propertyName, template)
                                    } else {
                                        jsonDelegate.call( propertyName ) {
                                            if (delegate instanceof StreamingJsonBuilder.StreamingJsonDelegate ) {
                                                jsonDelegate = (StreamingJsonBuilder.StreamingJsonDelegate) getDelegate()
                                            }
                                            processSimple(jsonDelegate, value, processedObjects, incs, excs,"${path}${propertyName}.", renderNulls)
                                        }
                                    }
                                }
                            }
                        }
                        else if (renderNulls) {
                            jsonDelegate.call(propertyName, NULL_OUTPUT)
                        }
                    }
                }
            }

            jsonDelegate.first = false

            if (customizer != null) {
                customizer.setDelegate(jsonDelegate)
                if (customizer.maximumNumberOfParameters == 1) {
                    customizer.call(object)
                } else {
                    customizer.call()
                }
            }

        }
    }


    protected boolean isSimpleValue(Object value) {
        if(value == null) {
            return true
        }

        Class propertyType = value.getClass()
        JsonView jsonView = (JsonView)view
        MappingFactory mappingFactory = jsonView.mappingContext?.mappingFactory
        if(mappingFactory != null) {
            return mappingFactory.isSimpleType(propertyType) || (value instanceof Enum) || (value instanceof Map)
        }
        else {
            return MappingFactory.isSimpleType(propertyType.getName()) || (value instanceof Enum) || (value instanceof Map)
        }

    }

    protected void process(StreamingJsonBuilder.StreamingJsonDelegate jsonDelegate, PersistentEntity entity, Object object, Map<Object, JsonOutput.JsonWritable> processedObjects, List<String> incs, List<String> excs, String path, boolean isDeep, boolean renderNulls, List<String> expandProperties = [], boolean includeAssociations = true, Closure customizer = null) {

        ResolvableGroovyTemplateEngine templateEngine = view.templateEngine
        Locale locale = view.locale

        renderEntityId(jsonDelegate, processedObjects, incs, excs, path, isDeep, renderNulls, expandProperties, getValidIdProperties(entity, object, incs, excs, path))

        for (prop in entity.persistentProperties) {
            def propertyName = prop.name
            String qualified = "${path}${propertyName}"

            if (!includeExcludeSupport.shouldInclude(incs, excs, qualified)) {
                continue
            }

            def value = ((GroovyObject) object).getProperty(propertyName)
            if(value == null) {
                if (renderNulls) {
                    jsonDelegate.call(propertyName, NULL_OUTPUT)
                }
                continue
            }

            if (!(prop instanceof Association)) {
                processSimpleProperty(jsonDelegate, (PersistentProperty) prop, propertyName, value)
            } else if(includeAssociations) {
                Association ass = (Association) prop
                def associatedEntity = ass.associatedEntity

                if (ass instanceof Embedded) {
                    def propertyType = ass.type
                    def template = renderTemplate(value, propertyType)
                    if(template != null) {
                        jsonDelegate.call(propertyName, template)
                    }
                    else {
                        jsonDelegate.call(propertyName) {
                            StreamingJsonBuilder.StreamingJsonDelegate embeddedDelegate = (StreamingJsonBuilder.StreamingJsonDelegate)getDelegate()
                            if(associatedEntity != null) {
                                process(embeddedDelegate, associatedEntity,value, processedObjects, incs, excs , "${qualified}.", isDeep, renderNulls)
                            }
                            else {
                                processSimple(embeddedDelegate, value, processedObjects, incs, excs, "${qualified}.", renderNulls)
                            }
                        }
                    }
                }
                else if(ass instanceof ToOne) {
                    if(associatedEntity != null) {
                        def propertyType = ass.type

                        if(!ass.circular && (isDeep || expandProperties.contains(qualified))) {
                            def childTemplate = templateEngine?.resolveTemplate(TemplateResolverUtils.shortTemplateNameForClass(propertyType), locale)
                            if(childTemplate != null && notCircular((JsonViewTemplate)childTemplate)) {
                                def model = [(GrailsNameUtils.getPropertyName(propertyType)): value]
                                def childView = prepareWritable(childTemplate, model)
                                def writer = new FastStringWriter()
                                childView.writeTo(writer)
                                jsonDelegate.call(propertyName, JsonOutput.unescaped(writer.toString()))
                            } else if (!ass.isOwningSide() && ass.isBidirectional() && !expandProperties.contains(qualified)) {
                                continue
                            }
                            else {
                                jsonDelegate.call(propertyName) {
                                    StreamingJsonBuilder.StreamingJsonDelegate embeddedDelegate = (StreamingJsonBuilder.StreamingJsonDelegate)getDelegate()

                                    process(embeddedDelegate, getPersistentEntity(associatedEntity, value), value, processedObjects, incs, excs , "${qualified}.", isDeep, renderNulls, expandProperties)
                                }
                            }

                        }
                        else {
                            Map validIdProperties = getValidIdProperties(associatedEntity, value, incs, excs, "${qualified}.")
                            if (validIdProperties.size() > 0) {
                                jsonDelegate.call(propertyName) {
                                    renderEntityId(delegate, processedObjects, incs, excs, "${qualified}.", isDeep, renderNulls, expandProperties, validIdProperties)
                                }
                            }
                        }

                    }
                }
                else if((ass instanceof ToMany) && Iterable.isAssignableFrom(ass.type)) {

                    if(ass instanceof Basic) {
                        // basic collection types like lists of strings etc. just render directly
                        jsonDelegate.call(propertyName, value)
                    }
                    else {
                        boolean shouldExpand = expandProperties.contains(qualified)
                        if(!isDeep && !shouldExpand) {
                            def proxyHandler = ((JsonView) view).getProxyHandler()
                            if(proxyHandler?.isProxy(value) && !proxyHandler.isInitialized(value)) {
                                continue
                            }
                            if(value instanceof PersistentCollection) {
                                PersistentCollection pc = (PersistentCollection)value
                                if(!pc.isInitialized()) continue
                            }
                        }

                        if(isDeep || shouldExpand) {
                            def propertyType = ass.associatedEntity.javaClass
                            def childTemplate = templateEngine?.resolveTemplate(propertyType, locale)
                            if(childTemplate != null && notCircular((JsonViewTemplate)childTemplate)) {
                                def writer = new FastStringWriter()
                                def iterator = ((Iterable) value).iterator()
                                writer.write(JsonOutput.OPEN_BRACKET)
                                def childPropertyName = GrailsNameUtils.getPropertyName(propertyType)

                                while(iterator.hasNext()) {
                                    def o = iterator.next()

                                    def model = [(childPropertyName): o]
                                    def childView = prepareWritable(childTemplate, model)
                                    childView.writeTo(writer)
                                    if(iterator.hasNext()) {
                                        writer.write(JsonOutput.COMMA)
                                    }
                                }
                                writer.write(JsonOutput.CLOSE_BRACKET)
                                jsonDelegate.call(propertyName, JsonOutput.unescaped(writer.toString()))
                            }  else if (!ass.isOwningSide() && ass.isBidirectional() && !expandProperties.contains(qualified)) {
                                continue
                            } else {
                                jsonDelegate.call(propertyName, (Iterable)value) { child ->
                                    StreamingJsonBuilder.StreamingJsonDelegate embeddedDelegate = (StreamingJsonBuilder.StreamingJsonDelegate)getDelegate()

                                    process(embeddedDelegate, getPersistentEntity(associatedEntity, child), child, processedObjects, incs, excs , "${qualified}.", isDeep, renderNulls)
                                }
                            }
                        } else {
                            jsonDelegate.call(propertyName, (Iterable)value) { child ->
                                Map idProperties = getValidIdProperties(associatedEntity, child, incs, excs, "${qualified}.") as Map
                                if (idProperties.size() > 0) {
                                    renderEntityId((StreamingJsonBuilder.StreamingJsonDelegate) getDelegate(), processedObjects, incs, excs, "${qualified}.", isDeep, renderNulls, expandProperties, idProperties)
                                } else {
                                    StreamingJsonBuilder.StreamingJsonDelegate embeddedDelegate = (StreamingJsonBuilder.StreamingJsonDelegate)getDelegate()

                                    process(embeddedDelegate, getPersistentEntity(associatedEntity, child), child, processedObjects, incs, excs , "${qualified}.", isDeep, renderNulls, expandProperties)
                                }
                            }
                        }
                    }
                }
                else if(ass instanceof EmbeddedCollection) {
                    if(Iterable.isAssignableFrom(ass.type) && associatedEntity != null) {
                        jsonDelegate.call(propertyName, (Iterable)value) { child ->
                            StreamingJsonBuilder.StreamingJsonDelegate embeddedDelegate = (StreamingJsonBuilder.StreamingJsonDelegate)getDelegate()
                            process(embeddedDelegate, getPersistentEntity(associatedEntity, child), child, processedObjects, incs, excs , "${qualified}.", isDeep, renderNulls, expandProperties)
                        }
                    }
                } else if (ass instanceof Basic) {
                    jsonDelegate.call(propertyName, value)
                }

            }
        }

        if (customizer != null) {
            customizer.setDelegate(jsonDelegate)
            if (customizer.maximumNumberOfParameters == 1) {
                customizer.call(object)
            } else {
                customizer.call()
            }
        }

    }

    /**
     * Retrieves the persistent entity of the value if it exists, defaults to {@param entity}
     *
     * @param entity The default entity
     * @param value The object to be rendered
     * @return The entity to render the object with
     */
    private PersistentEntity getPersistentEntity(PersistentEntity entity, Object value) {
        if (value.getClass() != entity.javaClass) {
            return entity.mappingContext.getPersistentEntity(value.getClass().getName())
        } else {
            return entity
        }
    }

    protected void processSimpleProperty(StreamingJsonDelegate jsonDelegate, PersistentProperty prop, String propertyName, Object value) {
        if (prop instanceof Custom) {
            def propertyType = value.getClass()
            def template = renderTemplate(value, propertyType)
            if (template != null) {
                jsonDelegate.call(propertyName, template)
                return
            }
        }

        if (isStringType(prop.type)) {
            jsonDelegate.call propertyName, value.toString()
        } else if (prop.type.isEnum()) {
            jsonDelegate.call propertyName, ((Enum) value).name()
        } else if (value instanceof TimeZone) {
            jsonDelegate.call propertyName, value.getID()
        } else {
            jsonDelegate.call(propertyName, value)
        }

    }

    private Map<PersistentProperty, Object> getValidIdProperties(PersistentEntity entity, Object object, List<String> incs, List<String> excs, String path) {
        Map<PersistentProperty, Object> ids = [:]
        IdentityMapping identityMapping = entity.mapping.identifier
        if (identityMapping == null) {
            return ids
        }
        String[] identity = identityMapping.identifierName
        for (String idName : identity) {
            String idQualified = "${path}${idName}"

            if (idName != null && includeExcludeSupport.shouldInclude(incs, excs, idQualified)) {
                PersistentProperty property
                if (entity.identity != null && entity.identity.name == idName) {
                    property = entity.identity
                }
                if (property == null) {
                    property = entity.getPropertyByName(idName)
                }
                if (property != null) {
                    def idValue = entity.mappingContext.getEntityReflector(entity).getPropertyReader(idName).read(object)
                    if (idValue == null) {
                        idValue = ((GroovyObject) object).getProperty(idName)
                    }
                    if (idValue != null) {
                        ids[property] = idValue
                    }
                }
            }
        }
        ids
    }

    private renderEntityId(StreamingJsonBuilder.StreamingJsonDelegate jsonDelegate, Map<Object, JsonOutput.JsonWritable> processedObjects, List<String> incs, List<String> excs, String path, boolean isDeep, boolean renderNulls, List<String> expandProperties, Map<PersistentProperty, Object> idProperties) {

        idProperties.each { PersistentProperty property, Object idValue ->
            def idType = property.type
            def idName = property.name
            String idQualified = "${path}${idName}"
            def template = renderTemplate(idValue, idType)
            if(template != null) {
                jsonDelegate.call(idName, template)
            }
            else {
                if (property instanceof Association) {
                    def ass = (Association)property
                    if(!ass.circular && (isDeep || expandProperties.contains(idQualified))) {
                        jsonDelegate.call(idName) {
                            StreamingJsonBuilder.StreamingJsonDelegate embeddedDelegate = (StreamingJsonBuilder.StreamingJsonDelegate)getDelegate()
                            process(embeddedDelegate, ass.associatedEntity, idValue, processedObjects, incs, excs , "${idQualified}.", isDeep, renderNulls, expandProperties)
                        }
                    } else {
                        jsonDelegate.call(idName) {
                            renderEntityId((StreamingJsonBuilder.StreamingJsonDelegate) getDelegate(), processedObjects, incs, excs, "${idQualified}.", isDeep, renderNulls, expandProperties, getValidIdProperties(ass.associatedEntity, idValue, incs, excs, "${idQualified}."))
                        }
                    }
                } else if (isStringType(idValue.getClass())) {
                    jsonDelegate.call(idName, idValue.toString())
                } else {
                    jsonDelegate.call(idName, idValue)
                }
            }
        }
    }

    JsonOutput.JsonWritable renderChildTemplate(Template template, Class modelType, modelValue) {
        def childView = (JsonView)prepareWritable(template, [(GrailsNameUtils.getPropertyName(modelType)): modelValue])
        return new JsonOutput.JsonWritable() {

            @Override
            Writer writeTo(Writer out) throws IOException {
                childView.writeTo(out)
                return out
            }
        }
    }

    @Override
    JsonOutput.JsonWritable render(Map arguments) {
        def template = arguments.template

        def templateEngine = view.templateEngine
        if(template) {
            // Reset the previous state in case were are rendering the same template
            if (view.binding.variables.containsKey(PROCESSED_OBJECT_VARIABLE)) {
                view.binding.variables.remove(PROCESSED_OBJECT_VARIABLE)
            }

            Map model = (Map)arguments.model ?: [:]
            def collection = arguments.containsKey('collection') ? (arguments.collection ?: []) : null
            def var = arguments.var ?: 'it'
            String templateName = template.toString()
            String namespace = view.getControllerNamespace()
            String controllerName = view.getControllerName()


            ViewUriResolver viewUriResolver = templateEngine
                                                 .getViewUriResolver()

            String templateUri
            Template childTemplate

            if(controllerName != null) {
                log.debug("Resolving template [{}] for namespace [{}] and controller [{}]", templateName, namespace, controllerName)
                templateUri = viewUriResolver
                                .resolveTemplateUri(namespace, controllerName, templateName)
                childTemplate = templateEngine.resolveTemplate(templateUri, view.locale)
            }

            if (childTemplate == null) {
                String parentPath = view.viewTemplate.parentPath
                log.debug("Resolving template [{}] for parent path [{}]", templateName, parentPath)

                templateUri = viewUriResolver
                        .resolveTemplateUri(parentPath, templateName)
                childTemplate = templateEngine.resolveTemplate(templateUri, view.locale)
            }

            if(childTemplate != null) {
                return new JsonOutput.JsonWritable() {

                    @Override
                    Writer writeTo(Writer out) throws IOException {
                        if(collection instanceof Iterable) {
                            Iterable iterable = (Iterable)collection
                            int size = iterable.size()
                            int i = 0
                            out.append JsonOutput.OPEN_BRACKET
                            for(o in collection) {
                                model.put(var, o)
                                model.put(GrailsNameUtils.getPropertyName(o.class), o)
                                def writable = prepareWritable(childTemplate, model)
                                writable.writeTo( out )
                                if(++i != size) {
                                    out.append JsonOutput.COMMA
                                }
                            }
                            out.append JsonOutput.CLOSE_BRACKET
                        }
                        else {
                            GrailsView writable = prepareWritable(childTemplate, model)
                            writable.writeTo( out )
                        }
                    }
                }

            }
            else {
                throw new ViewException("Template not found for name $template")
            }
        }
        else {
            return render((Object) arguments)
        }

    }

    protected void populateModelWithViewState(Map model) {
        def parentViewBinding = view.binding
        if (parentViewBinding.variables.containsKey(PROCESSED_OBJECT_VARIABLE)) {
            model.put(PROCESSED_OBJECT_VARIABLE, parentViewBinding.getVariable(PROCESSED_OBJECT_VARIABLE))
        }
    }

    protected GrailsView prepareWritable(Template childTemplate, Map model) {
        populateModelWithViewState(model)
        GrailsView writable = (GrailsView) (model ? childTemplate.make((Map) model) : childTemplate.make())
        writable.locale = view.locale
        writable.response = view.response
        writable.request = view.request
        writable.params = view.params
        writable.controllerNamespace = view.controllerNamespace
        writable.controllerName = view.controllerName
        writable.actionName = view.actionName
        writable.config = view.config
        return writable
    }

    /**
     * Obtains a model value for the given name and type
     *
     * @param name The name
     * @param targetType The type
     * @return The model value or null if it doesn't exist
     */
    def <T> T model(String name, Class<T> targetType = Object) {
        def value = view.binding.variables.get(name)
        if(targetType.isInstance(value)) {
            return (T)value
        }
        return null
    }
}
