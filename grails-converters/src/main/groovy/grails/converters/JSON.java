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
package grails.converters;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.io.Writer;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;

import groovy.lang.Closure;
import groovy.util.BuilderSupport;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import grails.io.IOUtils;
import grails.util.GrailsWebUtil;
import grails.web.mime.MimeType;
import org.grails.web.converters.AbstractConverter;
import org.grails.web.converters.Converter;
import org.grails.web.converters.ConverterUtil;
import org.grails.web.converters.IncludeExcludeConverter;
import org.grails.web.converters.configuration.ConverterConfiguration;
import org.grails.web.converters.configuration.ConvertersConfigurationHolder;
import org.grails.web.converters.configuration.DefaultConverterConfiguration;
import org.grails.web.converters.exceptions.ConverterException;
import org.grails.web.converters.jackson.ConverterJsonModule;
import org.grails.web.converters.jackson.DomainClassJacksonModule;
import org.grails.web.converters.jackson.DomainClassRenderer;
import org.grails.web.converters.jackson.DomainClassRendering;
import org.grails.web.converters.marshaller.ClosureObjectMarshaller;
import org.grails.web.converters.marshaller.ObjectMarshaller;
import org.grails.web.converters.marshaller.json.JsonMapperValueMarshaller;
import org.grails.web.json.JSONArray;
import org.grails.web.json.JSONElement;
import org.grails.web.json.JSONException;
import org.grails.web.json.JSONObject;
import org.grails.web.json.JSONTokener;
import org.grails.web.json.JSONWriter;
import org.grails.web.json.JsonDateFormat;
import org.grails.web.json.JsonMapperSupport;
import org.grails.web.json.JsonMapperValue;
import org.grails.web.json.PathCapturingJSONWriterWrapper;
import org.grails.web.json.PrettyPrintJSONWriter;

/**
 * A converter that converts domain classes, Maps, Lists, Arrays, POJOs and POGOs to JSON.
 *
 * @author Siegfried Puchbauer
 * @author Graeme Rocher
 */
public class JSON extends AbstractConverter<JSONWriter> implements IncludeExcludeConverter<JSONWriter> {

    private final static Log log = LogFactory.getLog(JSON.class);
    private static final String CACHED_JSON = "org.codehaus.groovy.grails.CACHED_JSON_REQUEST_CONTENT";

    protected Object target;
    protected final ConverterConfiguration<JSON> config;
    protected final CircularReferenceBehaviour circularReferenceBehaviour;
    protected boolean prettyPrint;
    protected JSONWriter writer;
    protected Stack<Object> referenceStack;

    /** How many JSON objects and arrays each object on the reference stack is written in, where the writer knows it. */
    private Map<Object, Integer> referenceDepths = new IdentityHashMap<>();

    /** Whether this render wraps the root value in an object, whose key is part of the path of every value in it. */
    private boolean rootWrapped;

    /** For each class written in the place of a proxy in this render, the class of the proxy, whose includes apply to it. */
    private Map<Class<?>, Class<?>> projectedAs = new ConcurrentHashMap<>();

    /** The filters and renderings of the objects being written when a proxy's includes came to apply to their class. */
    private Map<Object, BiPredicate<String, String>> pinnedFilters = new IdentityHashMap<>();
    private Map<Object, DomainClassRendering> pinnedRenderings = new IdentityHashMap<>();
    protected JsonMapperSupport jsonMapperSupport;

    private Map<Class<?>, DomainClassRendering> domainClassRenderings = new ConcurrentHashMap<>();

    private Map<Class<?>, BiPredicate<String, String>> propertyFilters = new ConcurrentHashMap<>();

    private static final BiPredicate<String, String> NO_FILTER = (property, written) -> true;
    private JSONWriter jsonWriter;

    protected ConverterConfiguration<JSON> initConfig() {
        return ConvertersConfigurationHolder.getConverterConfiguration(JSON.class);
    }

    /**
     * Default Constructor for a JSON Converter
     */
    public JSON() {
        config = initConfig();
        encoding = config != null ? config.getEncoding() : "UTF-8";
        contentType = MimeType.JSON.getName();
        circularReferenceBehaviour = config != null ? config.getCircularReferenceBehaviour() : CircularReferenceBehaviour.DEFAULT;
        prettyPrint = config != null && config.isPrettyPrint();
    }

    /**
     * Creates a new JSON Converter for the given Object
     *
     * @param target the Object to convert
     */
    public JSON(Object target) {
        this();
        setTarget(target);
    }

    public void setPrettyPrint(boolean prettyPrint) {
        this.prettyPrint = prettyPrint;
    }

    @SuppressWarnings("deprecation")
    private void prepareRender(Writer out) {
        // a domain class instance is rendered as the converter's marshaller for it asks, with its includes and excludes
        domainClassRenderings = new ConcurrentHashMap<>();
        propertyFilters = new ConcurrentHashMap<>();
        JsonMapperSupport configured = ConvertersConfigurationHolder.getJsonMapperSupport();
        // The legacy converter's mapper writes only the JSON text, so neither attribute is read
        jsonMapperSupport = ConvertersConfigurationHolder.isLegacyJson() ? configured : configured
                .withAttribute(DomainClassJacksonModule.RENDERINGS, (Function<Object, DomainClassRendering>) this::domainClassRendering)
                .withAttribute(DomainClassJacksonModule.STAND_INS, (BiConsumer<Object, Object>) this::projectAs)
                .withAttribute(ConverterJsonModule.PROPERTY_FILTERS, (Function<Object, BiPredicate<String, String>>) this::propertyFilter);
        jsonWriter = prettyPrint && ConvertersConfigurationHolder.isLegacyJson() ?
                new PrettyPrintJSONWriter(out, jsonMapperSupport, PrettyPrintJSONWriter.DEFAULT_INDENT_STR) :
                new JSONWriter(out, jsonMapperSupport, prettyPrint);
        writer = jsonWriter;
        if (circularReferenceBehaviour == CircularReferenceBehaviour.PATH) {
            if (log.isInfoEnabled()) {
                log.info(String.format("Using experimental CircularReferenceBehaviour.PATH for %s", getClass().getName()));
            }
            writer = new PathCapturingJSONWriterWrapper(writer);
        }
        referenceStack = new Stack<>();
        referenceDepths = new IdentityHashMap<>();
        projectedAs = new ConcurrentHashMap<>();
        pinnedFilters = new IdentityHashMap<>();
        pinnedRenderings = new IdentityHashMap<>();
    }

    private void finalizeRender(Writer out) {
        try {
            jsonWriter.flush();
            out.flush();
            out.close();
        }
        catch (Exception e) {
            log.warn("Unexpected exception while closing a writer: " + e.getMessage());
        }
    }

    /**
     * Directs the JSON Writer to the given Writer
     *
     * @param out the Writer
     * @throws org.grails.web.converters.exceptions.ConverterException where the value cannot be written, also where it is
     *         nested deeper than the thread's stack lets the converter write it
     *
     */
    public void render(Writer out) throws ConverterException {
        prepareRender(out);
        try {
            Object root = target == null || ConvertersConfigurationHolder.isLegacyJson() ? null :
                    config.getProxyHandler().unwrapIfProxy(target);
            String rootName = root == null ? null : jsonMapperSupport.rootName(root.getClass());
            rootWrapped = rootName != null;
            if (rootWrapped) {
                // as the mapper wraps a value it writes on its own, as Spring Boot wraps the body of a response
                try {
                    writer.object();
                    writer.key(rootName);
                    value(target);
                    writer.endObject();
                }
                catch (JSONException e) {
                    throw new ConverterException(e);
                }
            }
            else {
                value(target);
            }
        }
        catch (StackOverflowError e) {
            // caught once the stack has unwound: a value nested deeper than the thread's stack lets the converter write
            // it, such as an object graph without end, fails as a value the converter cannot write does, as Jackson
            // reports a bean it overflows the stack writing
            throw new ConverterException("The value is nested deeper than the thread's stack lets the converter write it, " +
                    "or is an object graph without end", e);
        }
        finalizeRender(out);
    }

    /**
     * Directs the JSON Writer to the Outputstream of the HttpServletResponse and sets the Content-Type to application/json
     *
     * @param response a HttpServletResponse
     * @throws ConverterException
     */
    public void render(HttpServletResponse response) throws ConverterException {
        ConvertersConfigurationHolder.withConverterObservation("json", () -> renderInternal(response));
    }

    private void renderInternal(HttpServletResponse response) throws ConverterException {
        response.setContentType(GrailsWebUtil.getContentType(contentType, encoding));
        try {
            render(response.getWriter());
        }
        catch (IOException e) {
            throw new ConverterException(e);
        }
    }

    public JSONWriter getWriter() throws ConverterException {
        return writer;
    }

    public void convertAnother(Object o) throws ConverterException {
        value(o);
    }

    public void build(@SuppressWarnings("rawtypes") Closure c) throws ConverterException {
        new Builder(this).execute(c);
    }

    /**
     * @param o
     * @throws ConverterException
     */
    public void value(Object o) throws ConverterException {
        Object unwrapped = config.getProxyHandler().unwrapIfProxy(o);
        if (unwrapped != o && unwrapped != null) {
            standIn(o, unwrapped);
        }
        o = unwrapped;
        try {
            if (o == null) {
                writer.valueNull();
            }
            else if (!ConvertersConfigurationHolder.isLegacyJson() && isScalar(o) &&
                    getJsonMapperSupport().hasCustomSerializer(o.getClass())) {
                // as the mapper writes it inside a container, with the serializer a module of the application gives its type
                writer.value(new JsonMapperValue(o, getJsonMapperSupport()));
            }
            else if (o instanceof CharSequence) {
                writer.value(o);
            }
            else if (o instanceof Class<?>) {
                writer.value(((Class<?>) o).getName());
            }
            else if (o instanceof Number number && (ConvertersConfigurationHolder.isLegacyJson() || isJdkNumber(number))) {
                // another Number is rendered by its marshaller, such as one the JsonMapper has a serializer for
                writer.value(number);
            } else if (o instanceof Boolean) {
                writer.value((Boolean) o);
            } else if (o.getClass().isPrimitive() && !o.getClass().equals(byte[].class)) {
                writer.value(o);
            }
            else {
                ObjectMarshaller<JSON> marshaller = config.getMarshaller(o);
                if (referenceIndex(o) >= 0 && !writesObjectId(o, marshaller)) {
                    handleCircularRelationship(o);
                }
                else {
                    referenceStack.push(o);
                    int depth = jsonWriter != null && !ConvertersConfigurationHolder.isLegacyJson() ? jsonWriter.nestingDepth() : -1;
                    if (depth >= 0) {
                        referenceDepths.put(o, depth);
                    }
                    if (marshaller == null) {
                        throw new ConverterException("Unconvertable Object of class: " + o.getClass().getName());
                    }
                    marshaller.marshalObject(o, this);
                    referenceStack.pop();
                    referenceDepths.remove(o);
                }
            }
        }
        catch (ConverterException ce) {
            throw ce;
        }
        catch (JSONException e) {
            throw new ConverterException(e);
        }
    }

    /**
     * Whether a value reached again is written as the object id the mapper gives it, rather than as a circular
     * reference: only when the mapper writes the value, and writes it with object ids.
     */
    private boolean writesObjectId(Object o, ObjectMarshaller<JSON> marshaller) {
        return !ConvertersConfigurationHolder.isLegacyJson() && JsonMapperValueMarshaller.writesWithMapper(marshaller) &&
                getJsonMapperSupport().writesObjectIds(o.getClass());
    }

    /**
     * Records that an instance is written in the place of a value that stands for it, such as the instance a proxy
     * proxies: it is written as the {@code JsonMapper} writes it in the place of the value, and, from then on in this
     * render, the includes and excludes the converter has for the class of the value apply to the instances of the class
     * of the instance that have none of their own, as the projection of {@code respond} is given it; an object of that
     * class the converter is already writing keeps those it started with. A marshaller that writes such an instance calls
     * it before it writes the instance.
     *
     * @param value the value, such as a proxy
     * @param instance the instance written in its place
     * @since 9.0
     */
    public void standIn(Object value, Object instance) {
        if (writer != null) {
            writer.standIn(value, instance);
        }
        projectAs(value, instance);
    }

    /**
     * The includes and excludes for the class of a proxy apply to the class of the instance written in its place, until the
     * render ends; the domain class serializer tells of a proxy it unwraps itself, where the proxy handler leaves a GORM
     * proxy as it is. An object of that class the converter is writing keeps the includes and excludes it started with.
     */
    private void projectAs(Object value, Object instance) {
        Class<?> type = value.getClass();
        if (instance == null || instance.getClass() == type || !includes.containsKey(type) && !excludes.containsKey(type)) {
            return;
        }
        Class<?> written = instance.getClass();
        if (projectedAs.containsKey(written)) {
            return;
        }
        for (Object being : referenceStack != null ? referenceStack : List.of()) {
            if (being.getClass() == written) {
                pinnedFilters.putIfAbsent(being, propertyFilters.computeIfAbsent(written, this::createPropertyFilter));
                pinnedRenderings.putIfAbsent(being, domainClassRendering(being));
            }
        }
        projectedAs.put(written, type);
        propertyFilters.remove(written);
        domainClassRenderings.remove(written);
    }

    /**
     * The class whose includes and excludes apply to a class: the proxy class it was written in the place of, where it has
     * none of its own, or else itself.
     */
    private Class<?> projectionClass(Class<?> type) {
        if (type == null || includes.containsKey(type) || excludes.containsKey(type)) {
            return type;
        }
        return projectedAs.getOrDefault(type, type);
    }

    /** The includes for a class, or else those of the proxy class whose instances it was written in the place of. */
    @Override
    @SuppressWarnings("rawtypes")
    public List<String> getIncludes(Class type) {
        return includes.get(projectionClass(type));
    }

    /** The excludes for a class, or else those of the proxy class whose instances it was written in the place of. */
    @Override
    @SuppressWarnings("rawtypes")
    public List<String> getExcludes(Class type) {
        return excludes.get(projectionClass(type));
    }

    /** A string, class name, boolean or number the converter writes as a single JSON value itself. */
    private static boolean isScalar(Object o) {
        return o instanceof CharSequence || o instanceof Class<?> || o instanceof Boolean ||
                (o instanceof Number number && isJdkNumber(number));
    }

    private static boolean isJdkNumber(Number number) {
        return number instanceof Integer || number instanceof Long || number instanceof Double || number instanceof BigDecimal ||
                number instanceof Short || number instanceof Byte || number instanceof Float || number instanceof BigInteger;
    }

    /**
     * Which properties of a bean the converter writes: those its includes and excludes for the class of the bean allow,
     * which name a property by the name the class gives it or by the name it is written with, as those of
     * {@code render} and {@code respond} do.
     */
    private BiPredicate<String, String> propertyFilter(Object bean) {
        BiPredicate<String, String> filter = pinnedFilters.get(bean);
        if (filter == null) {
            filter = propertyFilters.computeIfAbsent(bean.getClass(), this::createPropertyFilter);
        }
        return filter == NO_FILTER ? null : filter;
    }

    private BiPredicate<String, String> createPropertyFilter(Class<?> type) {
        List<String> includes = getIncludes(type);
        List<String> excludes = getExcludes(type);
        // empty includes include none of the properties, as the legacy marshallers have them
        if (includes == null && (excludes == null || excludes.isEmpty())) {
            return NO_FILTER;
        }
        return (property, written) -> (excludes == null || !excludes.contains(property) && !excludes.contains(written)) &&
                (includes == null || includes.contains(property) || includes.contains(written));
    }

    private DomainClassRendering domainClassRendering(Object instance) {
        if (pinnedRenderings.containsKey(instance)) {
            return pinnedRenderings.get(instance);
        }
        if (!(lookupObjectMarshaller(instance) instanceof DomainClassRenderer renderer)) {
            return null;
        }
        return domainClassRenderings.computeIfAbsent(instance.getClass(), type -> renderer.getRendering(this, type));
    }

    /**
     * @return the JsonMapper this converter writes with
     * @since 9.0
     */
    public JsonMapperSupport getJsonMapperSupport() {
        return jsonMapperSupport != null ? jsonMapperSupport : ConvertersConfigurationHolder.getJsonMapperSupport();
    }

    /**
     * The JSON object key for a map key, as the JsonMapper writes it: a {@code String} key as it is, and a
     * {@code Date} key, for example, in the mapper's date format. Rendering as Grails 8 did
     * ({@code grails.converters.json.legacy}), a date key as a UTC instant and any other key as its {@code toString()}.
     *
     * @param key a non-null map key
     * @return the JSON object key
     * @since 9.0
     */
    public String formatKey(Object key) {
        return ConvertersConfigurationHolder.isLegacyJson() ? JsonDateFormat.formatKey(key) : getJsonMapperSupport().formatKey(key);
    }

    public ObjectMarshaller<JSON> lookupObjectMarshaller(Object target) {
        return config.getMarshaller(target);
    }

    public int getDepth() {
        return referenceStack.size();
    }

    public void property(String key, Object value) throws JSONException, ConverterException {
        writer.key(key);
        value(value);
    }

    /**
     * Performs the conversion and returns the resulting JSON as String
     *
     * @param prettyPrint true, if the output should be indented, otherwise false
     * @return a JSON String
     * @throws JSONException
     */
    public String toString(boolean prettyPrint) throws JSONException {
        if (ConvertersConfigurationHolder.isLegacyJson()) {
            // as Grails 8 rendered it: with the configured pretty printing, and indented by three spaces for true
            String json = super.toString();
            if (prettyPrint) {
                Object value = new JSONTokener(json).nextValue();
                if (value instanceof JSONObject jsonObject) {
                    return jsonObject.toString(3);
                }
                if (value instanceof JSONArray jsonArray) {
                    return jsonArray.toString(3);
                }
            }
            return json;
        }
        boolean configuredPrettyPrint = this.prettyPrint;
        this.prettyPrint = prettyPrint;
        try {
            return super.toString();
        }
        finally {
            this.prettyPrint = configuredPrettyPrint;
        }
    }

    /**
     * Parses the given JSON String and returns either a JSONObject or a JSONArray
     *
     * @param reader JSON source
     * @return either a JSONObject or a JSONArray - depending on the given JSON
     * @throws ConverterException when the JSON content is not valid
     */
    public static JSONElement parse(Reader reader) throws ConverterException {
        return parse(IOUtils.toString(reader));
    }

    /**
     * Parses the given JSON String and returns either a JSONObject or a JSONArray. With
     * {@code grails.converters.json.legacy} set to {@code false}, the application's Jackson mapper parses it, and what it
     * accepts is what its read features allow, such as {@code spring.jackson.json.read.allow-java-comments}; otherwise Grails'
     * own parser does, which also accepts single quotes, comments, unquoted values and the like.
     *
     * @param source A string containing some JSON
     * @return either a JSONObject or a JSONArray - depending on the given JSON
     * @throws ConverterException when the JSON content is not valid
     */
    public static JSONElement parse(String source) throws ConverterException {
        try {
            final Object value = ConvertersConfigurationHolder.isLegacyJson() ?
                    new JSONTokener(source).nextValue() : ConvertersConfigurationHolder.getJsonMapperSupport().parse(source);
            if (value instanceof JSONElement) {
                return (JSONElement) value;
            }

            // return empty object
            return new JSONObject();
        }
        catch (JSONException e) {
            throw new ConverterException("Error parsing JSON", e);
        }
    }

    /**
     * Parses the given JSON and returns either a JSONObject or a JSONArray
     *
     * @param is       An InputStream which delivers some JSON
     * @param encoding the Character Encoding to use
     * @return either a JSONObject or a JSONArray - depending on the given JSON
     * @throws ConverterException when the JSON content is not valid
     */
    public static JSONElement parse(InputStream is, String encoding) throws ConverterException {
        return parse(IOUtils.toString(is, encoding));
    }

    /**
     * Parses the given request's InputStream and returns either a JSONObject or a JSONArray
     *
     * @param request the JSON Request
     * @return either a JSONObject or a JSONArray - depending on the given JSON
     * @throws ConverterException when the JSON content is not valid
     */
    public static Object parse(HttpServletRequest request) throws ConverterException {
        Object json = request.getAttribute(CACHED_JSON);
        if (json != null) {
            return json;
        }

        String encoding = request.getCharacterEncoding();
        if (encoding == null) {
            encoding = Converter.DEFAULT_REQUEST_ENCODING;
        }
        try {
            PushbackInputStream pushbackInputStream = null;
            int firstByte = -1;
            try {
                pushbackInputStream = new PushbackInputStream(request.getInputStream());
                firstByte = pushbackInputStream.read();
            } catch (IOException ioe) {}

            // code has only been changed from here down
            if (firstByte == -1) {
                json = new JSONObject();
            }
            else {
                pushbackInputStream.unread(firstByte);
                json = parse(pushbackInputStream, encoding);
            }
            request.setAttribute(CACHED_JSON, json);
            return json;
        }
        catch (IOException e) {
            throw new ConverterException("Error parsing JSON", e);
        }
    }

    /**
     * Sets the Object which is later converted to JSON
     *
     * @param target the Object
     * @see org.grails.web.converters.Converter
     */
    @Override
    public void setTarget(Object target) {
        this.target = target;
    }

    protected void handleCircularRelationship(Object o) throws ConverterException {
        switch (circularReferenceBehaviour) {
            case DEFAULT:
                final boolean isCollection = Collection.class.isAssignableFrom(o.getClass());
                final boolean isMap = Map.class.isAssignableFrom(o.getClass());
                if (!(isMap || isCollection)) {
                    // One step up for each JSON object or array from the one the reference is written in to the value the
                    // object is written as, its type wrapper included, as respond counts them; the legacy converter, and a
                    // writer that writes to no generator, count one for each object the converter is writing in between, as
                    // Grails 8 did, which a marshaller that writes an object as another value counts twice
                    int idx = referenceIndex(o);
                    Integer start = ConvertersConfigurationHolder.isLegacyJson() ? null : referenceDepths.get(o);
                    int current = jsonWriter != null ? jsonWriter.nestingDepth() : -1;
                    int levels = start != null && current >= 0 ? Math.max(0, current - start - 1) : referenceStack.size() - 1 - idx;
                    // "." where the object repeats the one the reference is written in, where Grails 8 failed
                    writeReference("_ref", levels == 0 ? "." : String.join("/", Collections.nCopies(levels, "..")), o);
                }
                else {
                    if (isMap) {
                        writer.object(); writer.endObject();
                    }
                    else {
                        writer.array(); writer.endArray();
                    }
                }
                break;
            case EXCEPTION:
                throw new ConverterException("Circular Reference detected: class " + o.getClass().getName());
            case INSERT_NULL:
                value(null);
                break;
            case PATH:
                int idx = referenceIndex(o);
                PathCapturingJSONWriterWrapper pcWriter = (PathCapturingJSONWriterWrapper) writer;
                // a wrapped root value is a step further from the root of the JSON text
                writeReference("ref", String.format("root%s", pcWriter.getStackReference(rootWrapped ? idx + 1 : idx)), o);
                break;
            case IGNORE:
                break;
        }
    }

    /**
     * Where an object is on the stack of the objects being written: the first that is the object itself, as Jackson and
     * {@code respond} find an object reached again, or, in the legacy converter, the first equal to it, as Grails 8 found
     * it.
     *
     * @return its index, or {@code -1} where it is not being written
     */
    private int referenceIndex(Object o) {
        if (ConvertersConfigurationHolder.isLegacyJson()) {
            return referenceStack.indexOf(o);
        }
        for (int i = 0; i < referenceStack.size(); i++) {
            if (referenceStack.get(i) == o) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Writes a reference to an object reached again with its keys in the order Grails 8 wrote them, whatever order the
     * mapper writes the entries of a map in. The legacy converter writes it as a map, as Grails 8 did.
     */
    private void writeReference(String key, String reference, Object o) {
        if (ConvertersConfigurationHolder.isLegacyJson()) {
            // a map, as Grails 8 wrote it, which a marshaller the application registers for maps writes
            Map<String, Object> props = new HashMap<>();
            props.put("class", o.getClass());
            props.put(key, reference);
            value(props);
            return;
        }
        writer.object();
        writer.key(key);
        writer.value(reference);
        writer.key("class");
        writer.value(o.getClass().getName());
        writer.endObject();
    }

    /**
     * @deprecated Scheduled for removal in Grails 11. Inject {@link grails.converters.json.NamedJsonConfigurationRegistry} and call
     * {@link grails.converters.json.NamedJsonConfigurationRegistry#writer(String)}.
     */
    @Deprecated(since = "9.0", forRemoval = true)
    public static ConverterConfiguration<JSON> getNamedConfig(String configName) throws ConverterException {
        ConverterConfiguration<JSON> cfg = ConvertersConfigurationHolder.getNamedConverterConfiguration(configName, JSON.class);
        if (cfg == null) {
            throw new ConverterException(String.format("Converter Configuration with name '%s' not found!", configName));
        }
        return cfg;
    }

    /**
     * @deprecated Scheduled for removal in Grails 11. Inject {@link grails.converters.json.NamedJsonConfigurationRegistry} and call
     * {@link grails.converters.json.NamedJsonConfigurationRegistry#writeValueAsString(String, Object)} or
     * {@link grails.converters.json.NamedJsonConfigurationRegistry#writeValue(String, java.io.Writer, Object)} with a
     * configuration such as {@link grails.converters.json.NamedJsonConfigurationRegistry#DEEP}.
     */
    @Deprecated(since = "9.0", forRemoval = true)
    public static Object use(String configName, Closure<?> callable) throws ConverterException {
        ConverterConfiguration<JSON> old = ConvertersConfigurationHolder.getThreadLocalConverterConfiguration(JSON.class);
        ConverterConfiguration<JSON> cfg = getNamedConfig(configName);
        ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(JSON.class, cfg);
        try {
            return callable.call();
        }
        finally {
            ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(JSON.class, old);
        }
    }

    /**
     * @deprecated Scheduled for removal in Grails 11. Inject {@link grails.converters.json.NamedJsonConfigurationRegistry} and select the
     * configuration explicitly for each write, such as {@link grails.converters.json.NamedJsonConfigurationRegistry#DEEP}. The
     * replacement does not mutate thread-local state.
     */
    @Deprecated(since = "9.0", forRemoval = true)
    public static void use(String cfgName) throws ConverterException {
        if (cfgName == null || "default".equals(cfgName)) {
            ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(JSON.class, null);
        }
        else {
            ConvertersConfigurationHolder.setThreadLocalConverterConfiguration(JSON.class, getNamedConfig(cfgName));
        }
    }

    /**
     * Registers a supported converter customization. This applies to {@code render value as JSON}
     * and legacy {@code respond}; Spring response conversion uses Jackson serializers instead.
     */
    public static void registerObjectMarshaller(Class<?> clazz, Closure<?> callable) throws ConverterException {
        registerObjectMarshaller(new ClosureObjectMarshaller<>(clazz, callable));
    }

    /**
     * Registers a supported converter customization. This applies to {@code render value as JSON}
     * and legacy {@code respond}; Spring response conversion uses Jackson serializers instead.
     */
    public static void registerObjectMarshaller(Class<?> clazz, int priority, Closure<?> callable) throws ConverterException {
        registerObjectMarshaller(new ClosureObjectMarshaller<>(clazz, callable), priority);
    }

    /**
     * Registers a supported converter customization. This applies to {@code render value as JSON}
     * and legacy {@code respond}; Spring response conversion uses Jackson serializers instead.
     */
    public static void registerObjectMarshaller(ObjectMarshaller<JSON> om) throws ConverterException {
        ConverterConfiguration<JSON> cfg = ConvertersConfigurationHolder.getConverterConfiguration(JSON.class);
        if (cfg == null) {
            throw new ConverterException("Default Configuration not found for class " + JSON.class.getName());
        }
        if (!(cfg instanceof DefaultConverterConfiguration<?>)) {
            cfg = new DefaultConverterConfiguration<>(cfg);
            ConvertersConfigurationHolder.setDefaultConfiguration(JSON.class, cfg);
        }
        ((DefaultConverterConfiguration<JSON>) cfg).registerObjectMarshaller(om);
    }

    /**
     * Registers a supported converter customization. This applies to {@code render value as JSON}
     * and legacy {@code respond}; Spring response conversion uses Jackson serializers instead.
     */
    public static void registerObjectMarshaller(ObjectMarshaller<JSON> om, int priority) throws ConverterException {
        ConverterConfiguration<JSON> cfg = ConvertersConfigurationHolder.getConverterConfiguration(JSON.class);
        if (cfg == null) {
            throw new ConverterException("Default Configuration not found for class " + JSON.class.getName());
        }
        if (!(cfg instanceof DefaultConverterConfiguration<?>)) {
            cfg = new DefaultConverterConfiguration<>(cfg);
            ConvertersConfigurationHolder.setDefaultConfiguration(JSON.class, cfg);
        }
        ((DefaultConverterConfiguration<JSON>) cfg).registerObjectMarshaller(om, priority);
    }

    /**
     * @deprecated Scheduled for removal in Grails 11. Inject {@link grails.converters.json.NamedJsonConfigurationRegistry} and use
     * {@link grails.converters.json.NamedJsonConfigurationRegistry#register(String, java.util.function.Consumer)}.
     */
    @Deprecated(since = "9.0", forRemoval = true)
    public static void createNamedConfig(String name, Closure<?> callable) throws ConverterException {
        DefaultConverterConfiguration<JSON> cfg = new DefaultConverterConfiguration<>(ConvertersConfigurationHolder.getConverterConfiguration(JSON.class));
        try {
            callable.call(cfg);
            ConvertersConfigurationHolder.setNamedConverterConfiguration(JSON.class, name, cfg);
        }
        catch (Exception e) {
            throw ConverterUtil.resolveConverterException(e);
        }
    }

    /**
     * @deprecated Scheduled for removal in Grails 11. Register marshallers with
     * {@link #registerObjectMarshaller(Class, Closure)}, and set what else the default configuration held with the
     * converter's settings: {@code grails.converters.json.pretty.print}, {@code grails.converters.encoding},
     * {@code grails.converters.json.circular.reference.behaviour} and
     * {@code grails.converters.json.cacheObjectMarshallerSelectionByClass}. A Spring Boot {@code JsonMapperBuilderCustomizer}
     * configures the {@code JsonMapper} the converter writes values with when {@code grails.converters.json.legacy} is
     * {@code false}, though not how it lays out what it writes, which the converter decides.
     */
    @Deprecated(since = "9.0", forRemoval = true)
    public static void withDefaultConfiguration(Closure<?> callable) throws ConverterException {
        ConverterConfiguration<JSON> cfg = ConvertersConfigurationHolder.getConverterConfiguration(JSON.class);
        if (!(cfg instanceof DefaultConverterConfiguration<?>)) {
            cfg = new DefaultConverterConfiguration<>(cfg);
        }
        try {
            callable.call(cfg);
            ConvertersConfigurationHolder.setDefaultConfiguration(JSON.class, cfg);
        }
        catch (Throwable t) {
            throw ConverterUtil.resolveConverterException(t);
        }
    }

    @Override
    public void setIncludes(List<String> includes) {
        setIncludes(target.getClass(), includes);
    }

    @Override
    public void setExcludes(List<String> excludes) {
        setExcludes(target.getClass(), excludes);
    }

    public class Builder extends BuilderSupport {

        private JSON json;

        public Builder(JSON json) {
            this.json = json;
            writer = json.writer;
        }

        public void execute(Closure<?> callable) {
            callable.setDelegate(this);
            // callable.setDelegate(Closure.DELEGATE_FIRST);
            invokeMethod("json", new Object[] { callable });
        }

        private Stack<BuilderMode> stack = new Stack<>();
        private boolean start = true;
        private JSONWriter writer;

        @Override
        protected Object createNode(Object name) {
            int retVal = 1;
            try {
                if (start) {
                    start = false;
                    writeObject();
                }
                else {
                    if (getCurrent() == null && stack.peek() == BuilderMode.OBJECT) {
                        throw new IllegalArgumentException("only call to [element { }] is allowed when creating array");
                    }
                    if (stack.peek() == BuilderMode.ARRAY) {
                        writeObject();
                        retVal = 2;
                    }
                    writer.key(String.valueOf(name)).array();
                    stack.push(BuilderMode.ARRAY);
                }
            }
            catch (JSONException e) {
                throw new IllegalArgumentException("invalid element");
            }

            return retVal;
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected Object createNode(Object key, Map valueMap) {
            try {
                if (stack.peek().equals(BuilderMode.OBJECT)) {
                    writer.key(String.valueOf(key));
                }
                writer.object();
                for (Object o : valueMap.entrySet()) {
                    Map.Entry element = (Map.Entry) o;
                    Object elementKey = element.getKey();
                    writer.key(elementKey == null ? "null" : json.formatKey(elementKey));
                    json.convertAnother(element.getValue());
                }
                writer.endObject();
                return null;
            }
            catch (JSONException e) {
                throw new IllegalArgumentException("invalid element");
            }
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected Object createNode(Object arg0, Map arg1, Object arg2) {
            throw new IllegalArgumentException("not implemented");
        }

        @SuppressWarnings("rawtypes")
        @Override
        protected Object createNode(Object key, Object value) {
            if (getCurrent() == null && stack.peek() == BuilderMode.OBJECT) {
                throw new IllegalArgumentException("only call to [element { }] is allowed when creating array");
            }

            try {
                int retVal = 0;
                if (stack.peek().equals(BuilderMode.ARRAY)) {
                    writeObject();
                    retVal = 1;
                }
                if (value instanceof Collection) {
                    Collection c = (Collection) value;
                    writer.key(String.valueOf(key));
                    handleCollectionRecurse(c);
                }
                else {
                    writer.key(String.valueOf(key));
                    json.convertAnother(value); //.value(value);
                }
                return retVal != 0 ? retVal : null;
            }
            catch (JSONException e) {
                throw new IllegalArgumentException("invalid element");
            }
        }

        @SuppressWarnings("rawtypes")
        private void handleCollectionRecurse(Collection c) throws JSONException {
            writer.array();
            for (Object element : c) {
                if (element instanceof Collection) {
                    handleCollectionRecurse((Collection) element);
                }
                else {
                    json.convertAnother(element);
                }
            }
            writer.endArray();
        }

        @Override
        protected void nodeCompleted(Object parent, Object node) {
            Object last = null;

            if (node == null) {
                return;
            }

            try {
                int i = ((Integer) node);
                while (i-- > 0) {
                    last = stack.pop();
                    if (BuilderMode.ARRAY == last) {
                        writer.endArray();
                    }
                    if (BuilderMode.OBJECT == last) {
                        writer.endObject();
                    }
                }
            }
            catch (JSONException e) {
                throw new IllegalArgumentException("invalid element on the stack");
            }
        }

        @Override
        protected void setParent(Object arg0, Object arg1) {
            /* do nothing */
        }

        private void writeObject() throws JSONException {
            writer.object();
            stack.push(BuilderMode.OBJECT);
        }
    }

    private enum BuilderMode {
        ARRAY,
        OBJECT
    }
}
