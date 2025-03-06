package grails.plugin.json.view.api.internal

import grails.plugin.json.builder.JsonGenerator
import grails.plugin.json.builder.JsonOutput
import grails.plugin.json.view.api.JsonView
import grails.rest.Link
import grails.util.TypeConvertingMap
import grails.views.api.http.Parameters
import grails.views.api.internal.DefaultGrailsViewHelper
import grails.views.mvc.http.DelegatingParameters
import grails.views.utils.ViewUtils
import groovy.transform.CompileStatic
import groovy.transform.InheritConstructors
import org.codehaus.groovy.runtime.StackTraceUtils
import org.grails.core.util.IncludeExcludeSupport
import org.grails.datastore.gorm.GormEnhancer
import org.grails.datastore.mapping.model.MappingFactory
import org.grails.datastore.mapping.model.PersistentEntity
import org.springframework.http.HttpMethod
import org.springframework.util.ReflectionUtils

import java.beans.PropertyDescriptor
import java.lang.reflect.ParameterizedType

@CompileStatic
@InheritConstructors
class DefaultJsonViewHelper extends DefaultGrailsViewHelper {

    public static final String PAGINATION_SORT = "sort"
    public static final String PAGINATION_ORDER = "order"
    public static final String PAGINATION_MAX = "max"
    public static final String PAGINATION_OFFSET = "offset"
    public static final String PAGINATION_TOTAL = "total"
    public static final String PAGINATION_RESROUCE = "resource"
    public static final List<String> DEFAULT_EXCLUDES = ["class", 'metaClass', 'properties']
    public static final List<String> DEFAULT_VALIDATEABLE_EXCLUDES = DEFAULT_EXCLUDES + ["errors"]
    public static final List<String> DEFAULT_GORM_EXCLUDES = DEFAULT_VALIDATEABLE_EXCLUDES + ["version", "attached", "dirty"]

    /**
     * The expand parameter
     */
    String EXPAND = "expand"

    /**
     * The associations parameter
     */
    String ASSOCIATIONS = "associations"

    protected final Set<String> TO_STRING_TYPES = [
            "org.bson.types.ObjectId"
    ] as Set

    protected final JsonOutput.JsonWritable NULL_OUTPUT = new JsonOutput.JsonWritable() {
        @Override
        Writer writeTo(Writer out) throws IOException {
            out.write(JsonOutput.NULL_VALUE);
            return out;
        }
    }

    IncludeExcludeSupport<String> simpleIncludeExcludeSupport = new DefaultJsonViewIncludeExcludeSupport(null, DEFAULT_EXCLUDES)
    IncludeExcludeSupport<String> validateableIncludeExcludeSupport = new DefaultJsonViewIncludeExcludeSupport(null, DEFAULT_VALIDATEABLE_EXCLUDES)
    IncludeExcludeSupport<String> includeExcludeSupport = new DefaultJsonViewIncludeExcludeSupport(null, DEFAULT_GORM_EXCLUDES)

    List<String> getIncludes(Map arguments) {
        ViewUtils.getStringListFromMap(IncludeExcludeSupport.INCLUDES_PROPERTY, arguments, null)
    }

    List<String> getExcludes(Map arguments) {
        ViewUtils.getStringListFromMap(IncludeExcludeSupport.EXCLUDES_PROPERTY, arguments)
    }

    Boolean getRenderNulls(Map arguments) {
        ViewUtils.getBooleanFromMap('renderNulls', arguments, false)
    }

    protected PersistentEntity findEntity(Object object) {
        def clazz = object.getClass()
        try {
            return GormEnhancer.findEntity(clazz)
        } catch (Throwable e) {
            return ((JsonView) view)?.mappingContext?.getPersistentEntity(clazz.name)
        }
    }


    protected Class getGenericType(Class declaringClass, PropertyDescriptor descriptor) {
        def field = ReflectionUtils.findField(declaringClass, descriptor.getName())
        if (field != null) {

            def type = field.genericType
            if (type instanceof ParameterizedType) {
                def args = ((ParameterizedType) type).getActualTypeArguments()
                if (args.length > 0) {
                    def t = args[0]
                    if (t instanceof Class) {
                        return (Class) t
                    }
                }
            }
        }
        return Object
    }

    boolean isStringType(Class propertyType) {
        return TO_STRING_TYPES.contains(propertyType.name)
    }

    boolean isSimpleType(Class propertyType, value) {
        MappingFactory.isSimpleType(propertyType.name) || (value instanceof Enum) || (value instanceof Map)
    }

    protected List<Object> getJsonStackTrace(Throwable e) {
        StackTraceUtils.sanitize(e)
        e.stackTrace
                .findAll() { StackTraceElement element -> element.lineNumber > -1 }
                .collect() { StackTraceElement element ->
                    "$element.lineNumber | ${element.className}.$element.methodName".toString()
                }.toList() as List<Object>
    }

    protected List<String> getExpandProperties(JsonView jsonView, Map arguments) {
        List<String> expandProperties
        def templateEngine = jsonView.templateEngine
        def viewConfiguration = templateEngine?.viewConfiguration
        if (viewConfiguration == null || viewConfiguration.isAllowResourceExpansion()) {
            expandProperties = (List<String>) (jsonView.params.list(EXPAND) ?: ViewUtils.getStringListFromMap(EXPAND, arguments))
        } else {
            expandProperties = Collections.emptyList()
        }
        expandProperties
    }

    protected boolean includeAssociations(Map arguments) {
        ViewUtils.getBooleanFromMap(ASSOCIATIONS, arguments, true)
    }

    protected List<Link> getPaginationLinks(Object object, Integer total, Parameters params) {
        int offset = params.int(PAGINATION_OFFSET, 0)
        int max = params.int(PAGINATION_MAX, 10)
        String sort = params.get(PAGINATION_SORT)
        String order = params.get(PAGINATION_ORDER)
        getPaginationLinks(object, total, max, offset, sort, order)
    }

    protected List<Link> getPaginationLinks(Object object, Integer total, Integer max, Integer offset, String sort, String order) {
        Map<String, Object> linkParams = buildPaginateParams(max, offset, sort, order)
        List<Link> links = []

        if (total > 0) {
            if (total > max) {
                Map firstParams = paramsWithOffset(linkParams, 0)
                links << new Link("first", link(resource: object, method: HttpMethod.GET, absolute: true, params: firstParams))
                Integer prevOffset = getPrevOffset(offset, max)
                if (prevOffset != null) {
                    Map prevParams = paramsWithOffset(linkParams, prevOffset)
                    links << new Link("prev", link(resource: object, method: HttpMethod.GET, absolute: true, params: prevParams))
                }
                Integer nextOffset = getNextOffset(total, offset, max)
                if (nextOffset) {
                    Map nextParams = paramsWithOffset(linkParams, nextOffset)
                    links << new Link("next", link(resource: object, method: HttpMethod.GET, absolute: true, params: nextParams))
                }
                Integer lastOffset = getLastOffset(total, max)
                if (lastOffset) {
                    Map lastParams = paramsWithOffset(linkParams, lastOffset)
                    links << new Link("last", link(resource: object, method: HttpMethod.GET, absolute: true, params: lastParams))
                }
            }
        }
        return links
    }

    protected Map<String, Object> buildPaginateParams(Integer max, Integer offset, String sort, String order) {
        Map<String, Object> params = [:]
        params.put(PAGINATION_OFFSET, offset)
        params.put(PAGINATION_MAX, max)
        if (sort) {
            params.put(PAGINATION_SORT, sort)
        }
        if (order) {
            params.put(PAGINATION_ORDER, order)
        }
        return params
    }

    protected Parameters defaultPaginateParams(Map arguments) {
        TypeConvertingMap params = new TypeConvertingMap()
        params.put(PAGINATION_OFFSET, arguments.get(PAGINATION_OFFSET) ?: view.params.get(PAGINATION_OFFSET))
        params.put(PAGINATION_MAX, arguments.get(PAGINATION_MAX) ?: view.params.get(PAGINATION_MAX))
        if (arguments.containsKey(PAGINATION_SORT) || view.params.containsKey(PAGINATION_SORT)) {
            params.put(PAGINATION_SORT, arguments.get(PAGINATION_SORT) ?: view.params.get(PAGINATION_SORT))
        }
        if (arguments.containsKey(PAGINATION_ORDER) || view.params.containsKey(PAGINATION_ORDER)) {
            params.put(PAGINATION_ORDER, arguments.get(PAGINATION_ORDER) ?: view.params.get(PAGINATION_ORDER))
        }
        return new DelegatingParameters(params)
    }

    /**
     * Creates a new Parameter map with the new offset
     * Note: necessary to avoid clone until Groovy 2.5.x https://issues.apache.org/jira/browse/GROOVY-7325
     *
     * @param map The parameter map to copy
     * @param offset The new offset to use
     * @return The resulting parameters
     */
    protected Map<String, Object> paramsWithOffset(Map<String, Object> originalParameters, Integer offset) {
        Map<String, Object> params = [:]
        originalParameters.each { String k, Object v ->
            params.put(k, v)
        }
        params.put(PAGINATION_OFFSET, offset)
        return params
    }

    protected Integer getPrevOffset(Integer offset, Integer max) {
        if (offset <= 0) {
            return null
        }
        return Math.max(offset - max, 0)
    }

    protected Integer getNextOffset(Integer total, Integer offset, Integer max) {
        if (offset < 0 || offset + max >= total) {
            return null
        }
        return offset + max
    }

    protected Integer getLastOffset(Integer total, Integer max) {
        if (total <= 0) {
            return null
        }
        Integer laststep = ((int) Math.round(Math.ceil((double) total / max))) - 1
        return Math.max((laststep * max), 0)
    }

    JsonGenerator getGenerator() {
        ((JsonView) view).generator
    }

}
