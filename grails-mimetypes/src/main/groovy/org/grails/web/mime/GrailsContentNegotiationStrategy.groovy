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
package org.grails.web.mime

import java.util.function.Supplier
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

import groovy.transform.CompileStatic

import jakarta.servlet.http.HttpServletRequest

import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.context.ApplicationContext
import org.springframework.http.InvalidMediaTypeException
import org.springframework.http.MediaType
import org.springframework.web.HttpMediaTypeNotAcceptableException
import org.springframework.web.accept.ContentNegotiationManager
import org.springframework.web.accept.ContentNegotiationStrategy
import org.springframework.web.accept.HeaderContentNegotiationStrategy
import org.springframework.web.accept.ParameterContentNegotiationStrategy
import org.springframework.web.context.request.ServletWebRequest
import org.springframework.web.context.support.WebApplicationContextUtils
import org.springframework.web.servlet.HandlerMapping

import grails.config.Config
import grails.config.Settings
import grails.core.GrailsApplication
import grails.web.http.HttpHeaders
import grails.web.mapping.UrlMappingInfo
import grails.web.mime.MimeType
import grails.web.mime.MimeUtility
import org.grails.config.PropertySourcesConfig
import org.grails.web.servlet.mvc.GrailsWebRequest

/**
 * Resolves the formats of a request: the MIME types of its {@code Accept} header under the {@code grails.mime} rules,
 * or, with {@code grails.mime.negotiation.spring}, the media types Spring MVC's content negotiation manager resolves for
 * it, as the formats {@code grails.mime.types} gives them.
 * It is deliberately not a Spring {@code ContentNegotiationStrategy}: Spring MVC keeps its own
 * negotiation, and Spring Security adopts any such bean it finds.
 *
 * @since 9.0
 */
@CompileStatic
class GrailsContentNegotiationStrategy {

    private static final Logger LOG = LoggerFactory.getLogger(GrailsContentNegotiationStrategy)

    /** The name of the bean Spring MVC's content negotiation manager is, which its handler adapter is given by name. */
    static final String MVC_CONTENT_NEGOTIATION_MANAGER = 'mvcContentNegotiationManager'

    private static final String FORMAT_PARAMETER = 'format'

    private static final String ALL_FORMAT = 'all'

    private static final String REQUEST_STRATEGY_ATTRIBUTE = GrailsContentNegotiationStrategy.name + '.REQUEST_STRATEGY'

    /** The formats a type with the structured syntax suffix of RFC 6839 of their name is a document of. */
    private static final Set<String> SUFFIX_FORMATS = Set.of('json', 'xml')

    private final MimeType[] mimeTypes
    private final boolean useAcceptHeader
    private final boolean useAcceptHeaderXhr
    private final List<Pattern> disableForUserAgents
    private final boolean springNegotiation
    private final Supplier<ContentNegotiationManager> contentNegotiationManager

    /** The media type of each configured MIME type, in its place, or {@code null} for one Spring cannot read. */
    private final MediaType[] mediaTypes

    GrailsContentNegotiationStrategy(MimeType[] mimeTypes, Config config) {
        this(mimeTypes, config, null)
    }

    /**
     * @param mimeTypes the MIME types the formats are resolved against
     * @param config the application's configuration
     * @param contentNegotiationManager supplies Spring MVC's content negotiation manager, which, with
     *        {@code grails.mime.negotiation.spring}, the formats are resolved with; asked for each request, since the
     *        strategy is created before it; or {@code null}, or one that supplies none, for one that reads the
     *        {@code Accept} header alone, as Spring MVC's default does
     * @since 9.0
     */
    GrailsContentNegotiationStrategy(MimeType[] mimeTypes, Config config,
                                     Supplier<ContentNegotiationManager> contentNegotiationManager) {
        this.mimeTypes = mimeTypes
        this.useAcceptHeader = config.getProperty(Settings.MIME_USE_ACCEPT_HEADER, Boolean, true)
        this.useAcceptHeaderXhr = !config.getProperty(
                Settings.MIME_DISABLE_ACCEPT_HEADER_FOR_USER_AGENTS_XHR,
                Boolean,
                false
        )
        this.disableForUserAgents = disabledUserAgentPatterns(config)
        this.springNegotiation = config.getProperty(Settings.MIME_NEGOTIATION_SPRING, Boolean,
                Boolean.valueOf(Settings.MIME_NEGOTIATION_SPRING_DEFAULT))
        this.contentNegotiationManager = contentNegotiationManager
        this.mediaTypes = mimeTypes.collect { MimeType mimeType -> mediaTypeOf(mimeType) } as MediaType[]
    }

    /**
     * @return the MIME types this strategy resolves against: in an application, those of its {@code MimeUtility}, which
     * {@code grails.mime.types}, or the defaults, and the {@code MimeTypeProvider} beans give
     */
    MimeType[] getConfiguredMimeTypes() {
        return mimeTypes
    }

    /**
     * @return whether the formats are resolved with Spring MVC's content negotiation manager, as
     * {@code grails.mime.negotiation.spring} says
     * @since 9.0
     */
    boolean isSpringNegotiation() {
        return springNegotiation
    }

    /**
     * @return the MIME types of the {@code Accept} header under the {@code grails.mime} rules, without
     * any {@code format} applied. This is what {@code response.mimeTypes}, and so {@code withFormat}
     * when the format has no block of its own, resolves. With {@code grails.mime.negotiation.spring}, they are the
     * configured types the media types Spring MVC's content negotiation manager resolves for the request stand for, in
     * its order, without the format the request names, and none where the request accepts no configured type.
     */
    MimeType[] resolveAcceptedMimeTypes(HttpServletRequest request) {
        if (springNegotiation) {
            List<MediaType> requested = requestedMediaTypes(request)
            return requested == null ? new MimeType[0] : acceptedMimeTypes(requested)
        }
        String acceptHeader = resolveAcceptHeader(request)
        MimeType[] resolved = new DefaultAcceptHeaderParser(mimeTypes).parse(acceptHeader)
        if (!acceptHeader) {
            MimeType allMimeType = resolved.find { MimeType mimeType -> mimeType.extension == 'all' }
            if (allMimeType != null) {
                return [allMimeType] as MimeType[]
            }
        }
        return resolved
    }

    /**
     * The MIME type a format a request names, with the {@code format} parameter or a URL extension, or that
     * {@code withFormat} selected, stands for.
     *
     * @param format the format
     * @return its first configured type; for a format that is not configured, the first configured type, as Grails 9
     * answers, or, with {@code grails.mime.negotiation.spring}, {@code null}, since the request accepts no configured
     * type, except for the {@code all} format, which is {@code *&#47;*}, any type
     * @since 9.0
     */
    MimeType resolveFormat(Object format) {
        MimeType mimeType = mimeTypes.find { MimeType configured -> configured.extension == format }
        if (mimeType != null) {
            return mimeType
        }
        if (springNegotiation) {
            // the all format is any type, as Spring MVC reads */*, also where grails.mime.types does not list it
            return format == ALL_FORMAT ? new MimeType(MimeType.ALL.name, ALL_FORMAT) : null
        }
        return mimeTypes.find { it }
    }

    /**
     * The format the request names with its {@code format} parameter or a URL extension, which overrides its
     * {@code Accept} header: with {@code grails.mime.negotiation.spring}, the first value of the parameter, as Spring MVC
     * reads a parameter it negotiates with, unless it names no type, as the {@code all} format does, which leaves the
     * negotiation to the {@code Accept} header, as Spring MVC's content negotiation manager does.
     *
     * @param webRequest the request
     * @return the format, or {@code null} where the request names none
     * @since 9.0
     */
    Object requestedFormat(GrailsWebRequest webRequest) {
        Object format = webRequest?.params?.get(FORMAT_PARAMETER)
        if (!springNegotiation || !format) {
            return format
        }
        String named = format instanceof String[] ? (((String[]) format).length ? ((String[]) format)[0] : null) :
                format.toString()
        if (!named) {
            return null
        }
        List<MediaType> types = mediaTypesOf(named)
        return types != null && types.every { MediaType type -> type.isWildcardType() } ? null : named
    }

    /**
     * The MIME types of the {@code Content-Type} of a request, with {@code grails.mime.negotiation.spring}: the type
     * Spring reads it as, as the format {@code grails.mime.types} gives it, a type with the {@code +json} or {@code +xml}
     * suffix it does not configure as the {@code json} or {@code xml} format, and another type as it is, with no format.
     * A request without one is read, as Grails 9 reads it, as of every configured type, so that its format is the first.
     *
     * @param request the request
     * @return the MIME types of its content
     * @since 9.0
     */
    MimeType[] resolveContentMimeTypes(HttpServletRequest request) {
        String header = request.contentType ?: request.getHeader(HttpHeaders.CONTENT_TYPE)
        if (!header) {
            return mimeTypes
        }
        MediaType contentType
        try {
            contentType = MediaType.parseMediaType(header)
        }
        catch (InvalidMediaTypeException e) {
            LOG.debug('The Content-Type [{}] is not a media type Spring reads: {}', header, e.message)
            MimeType unread = new MimeType(header)
            unread.name = unread.name?.toLowerCase(Locale.ROOT)
            return [unread] as MimeType[]
        }
        MimeType configured = configuredFor(contentType)
        return [configured != null ? withParameters(configured, contentType) : SpringMediaTypeAdapter.toMimeType(contentType)] as MimeType[]
    }

    /**
     * The media types a Grails format stands for, which Spring MVC's content negotiation manager resolves a request that
     * names the format to: its configured types, and for the {@code json} and {@code xml} formats any type with their
     * suffix, as Spring MVC's JSON and XML converters write them.
     *
     * @param format the format
     * @return the media types, {@code *&#47;*} for the {@code all} format where no type of it is configured, or
     * {@code null} where no type of another format is
     * @since 9.0
     */
    List<MediaType> mediaTypesOf(String format) {
        List<MediaType> types = []
        for (int i = 0; i < mimeTypes.length; i++) {
            if (mimeTypes[i].extension == format && mediaTypes[i] != null && !types.contains(mediaTypes[i])) {
                types << mediaTypes[i]
            }
        }
        if (types.isEmpty()) {
            // the all format is any type, also where grails.mime.types does not list it
            return format == ALL_FORMAT ? [MediaType.ALL] : null
        }
        if (format != null && SUFFIX_FORMATS.contains(format)) {
            MediaType suffixed = new MediaType('application', "*+${format}".toString())
            if (!types.contains(suffixed)) {
                types << suffixed
            }
        }
        return types
    }

    /**
     * The media type Spring MVC's content negotiation manager maps a format to, whatever its case, for a format no
     * type of {@code grails.mime.types} stands for, so that what Spring MVC writes for a request that names it is in
     * that type, as without {@code grails.mime.negotiation.spring}. The manager maps the data formats Grails
     * registers with it, the formats {@code spring.mvc.contentnegotiation.media-types} adds, and those Spring MVC
     * registers for the types its message converters support, such as {@code json}; since the lookup ignores case, a
     * data format {@code grails.mime.types} lists, named in another case, such as {@code HAL}, is mapped too.
     *
     * @param format the format
     * @return the media type, as a list, or {@code null} where the manager maps no type to the format
     * @since 9.0
     */
    List<MediaType> springMediaTypesOf(String format) {
        MediaType mediaType = format != null ?
                contentNegotiationManager?.get()?.mediaTypeMappings?.get(format.toLowerCase(Locale.ENGLISH)) : null
        return mediaType != null ? [mediaType] : null
    }

    /**
     * @return the media types of every configured MIME type Spring can read, and of every format Spring MVC's content
     * negotiation manager maps, which a request that names a format neither maps is not acceptable in
     * @since 9.0
     */
    List<MediaType> getConfiguredMediaTypes() {
        List<MediaType> configured = mediaTypes.findAll { it } as List<MediaType>
        Map<String, MediaType> springMappings = contentNegotiationManager?.get()?.mediaTypeMappings
        if (springMappings) {
            configured.addAll(springMappings.values())
        }
        return configured.unique()
    }

    /**
     * @return whether the {@code Accept} header of a request is ignored, as {@code grails.mime.use.accept.header} and
     * {@code grails.mime.disable.accept.header.*} say
     * @since 9.0
     */
    boolean ignoresAcceptHeader(HttpServletRequest request) {
        if (!useAcceptHeader) {
            return true
        }
        String userAgent = request.getHeader(HttpHeaders.USER_AGENT)
        boolean xhr = request.getHeader('X-Requested-With') == 'XMLHttpRequest'
        return !(useAcceptHeaderXhr && xhr) &&
                userAgent != null &&
                disableForUserAgents.any { Pattern pattern -> pattern.matcher(userAgent).find() }
    }

    /**
     * Whether a URL mapping handles a request, for which the {@code grails.mime} rules Grails keeps apply to what Spring
     * MVC negotiates: the mapping is the request's best matching handler, which each of Spring MVC's handler mappings sets
     * for the dispatch it handles, and which a URL mapping takes off the request once its dispatch completes. A web unit
     * test sets it for the action it calls.
     *
     * @param request the request
     * @return whether a URL mapping handles it
     * @since 9.0
     */
    static boolean handlesRequest(HttpServletRequest request) {
        return request.getAttribute(HandlerMapping.BEST_MATCHING_HANDLER_ATTRIBUTE) instanceof UrlMappingInfo
    }

    /**
     * Whether a strategy of Spring MVC's manager reads the parameter Grails reads the format a request names from, as
     * the parameter negotiation {@code spring.mvc.contentnegotiation.favor-parameter} enables does by default: Grails
     * reads it on its own, ahead of Spring MVC's strategies, for the requests a URL mapping handles, so that a format
     * Spring MVC does not know, such as {@code all}, is not read again.
     *
     * @param strategy a strategy of Spring MVC's manager
     * @return whether it reads the {@code format} parameter
     * @since 9.0
     */
    static boolean readsFormatParameter(ContentNegotiationStrategy strategy) {
        return strategy instanceof ParameterContentNegotiationStrategy &&
                ((ParameterContentNegotiationStrategy) strategy).parameterName == FORMAT_PARAMETER
    }

    /**
     * The strategy that resolves the formats of a request: that of the application's {@code MimeTypesConfiguration}, or,
     * in a context without it, such as one a test builds by hand, one built for the request from the application's
     * settings. It is looked up once for each request, which keeps it as an attribute, so that {@code request.format},
     * {@code response.format}, {@code withFormat} and {@code respond} do not look it up again.
     *
     * @param request the request
     * @return the strategy
     * @since 9.0
     */
    static GrailsContentNegotiationStrategy forRequest(HttpServletRequest request) {
        Object kept = request.getAttribute(REQUEST_STRATEGY_ATTRIBUTE)
        if (kept instanceof GrailsContentNegotiationStrategy) {
            return (GrailsContentNegotiationStrategy) kept
        }
        GrailsContentNegotiationStrategy strategy = lookUp(request)
        request.setAttribute(REQUEST_STRATEGY_ATTRIBUTE, strategy)
        return strategy
    }

    private static GrailsContentNegotiationStrategy lookUp(HttpServletRequest request) {
        ApplicationContext applicationContext = GrailsWebRequest.lookup()?.applicationContext
        if (applicationContext == null && request.servletContext != null) {
            applicationContext = WebApplicationContextUtils.getWebApplicationContext(request.servletContext)
        }
        GrailsContentNegotiationStrategy strategy = applicationContext?.getBeanProvider(
                GrailsMimeTypesWebMvcConfigurer
        )?.getIfAvailable()?.contentNegotiationStrategy
        if (strategy != null) {
            return strategy
        }
        Config config = applicationContext?.getBeanProvider(GrailsApplication)?.getIfAvailable()?.config
        MimeUtility mimeUtility = applicationContext?.getBeanProvider(MimeUtility)?.getIfAvailable()
        MimeType[] mimeTypes = mimeUtility?.knownMimeTypes as MimeType[] ?: MimeType.createDefaults()
        ApplicationContext context = applicationContext
        Supplier<ContentNegotiationManager> manager = () -> context?.containsBean(MVC_CONTENT_NEGOTIATION_MANAGER) ?
                context.getBean(MVC_CONTENT_NEGOTIATION_MANAGER, ContentNegotiationManager) : null
        return new GrailsContentNegotiationStrategy(mimeTypes, config ?: new PropertySourcesConfig(), manager)
    }

    /**
     * The media types Spring MVC's content negotiation manager resolves for a request, in its order, without the format
     * the request names, which Grails resolves on its own, also where Spring MVC's parameter negotiation reads it, and with its {@code Accept} header ignored where the
     * {@code grails.mime} settings ignore it, whether or not a URL mapping handles the request.
     *
     * @return the media types, or {@code null} where the manager finds the request not acceptable, as for an
     * {@code Accept} header Spring cannot read
     */
    private List<MediaType> requestedMediaTypes(HttpServletRequest request) {
        ContentNegotiationManager manager = contentNegotiationManager?.get()
        List<ContentNegotiationStrategy> strategies = manager != null ? manager.strategies :
                [new HeaderContentNegotiationStrategy()] as List<ContentNegotiationStrategy>
        boolean acceptHeaderIgnored = ignoresAcceptHeader(request)
        ServletWebRequest webRequest = new ServletWebRequest(request)
        try {
            for (ContentNegotiationStrategy strategy in strategies) {
                if (strategy instanceof GrailsFormatContentNegotiationStrategy || readsFormatParameter(strategy) ||
                        (acceptHeaderIgnored && strategy instanceof HeaderContentNegotiationStrategy)) {
                    continue
                }
                List<MediaType> resolved = strategy.resolveMediaTypes(webRequest)
                if (resolved != ContentNegotiationStrategy.MEDIA_TYPE_ALL_LIST) {
                    return preferringSuffixedXml(resolved)
                }
            }
        }
        catch (HttpMediaTypeNotAcceptableException e) {
            LOG.debug('The request accepts no format: {}', e.message)
            return null
        }
        return ContentNegotiationStrategy.MEDIA_TYPE_ALL_LIST
    }

    /**
     * Media types in the order Grails reads them, which keeps Spring MVC's order but for the types with the {@code +xml}
     * suffix of the quality of the XML type, the first {@code application/xml} or {@code text/xml}, or
     * {@code application/problem+xml} where no configured type names it, since Grails reads that problem type as
     * {@code application/xml}: they come ahead of it, as the more specific types, each one later in the header in front
     * of those before it, as the Grails rules order them. Unlike those rules, which leave a {@code text/xml} the header
     * lists without {@code application/xml} first, it is the XML type too. Such an unconfigured
     * {@code application/problem+xml} is one of the XML types, so it is not moved ahead of another. A type the header
     * lists more than once is moved from its own place, so no {@code +xml} type of the quality of the XML type is left
     * behind it, and the order is the same however often it is applied.
     *
     * @param mediaTypes the media types in Spring MVC's order
     * @return the media types in the order Grails reads them
     * @since 9.0
     */
    List<MediaType> preferringSuffixedXml(List<MediaType> mediaTypes) {
        boolean problemXmlAsXml = !mimeTypes.any { MimeType configured ->
            configured?.name?.equalsIgnoreCase(MediaType.APPLICATION_PROBLEM_XML_VALUE)
        }
        int xml = mediaTypes.findIndexOf { MediaType mediaType -> isXmlType(mediaType, problemXmlAsXml) }
        if (xml < 0) {
            return mediaTypes
        }
        double quality = mediaTypes[xml].qualityValue
        List<MediaType> ordered = new ArrayList<>(mediaTypes)
        for (int i = xml + 1; i < mediaTypes.size(); i++) {
            MediaType mediaType = mediaTypes[i]
            if (mediaType.subtypeSuffix?.equalsIgnoreCase('xml') && mediaType.qualityValue >= quality &&
                    !isXmlType(mediaType, problemXmlAsXml)) {
                // still at i: each type moved before it was taken from between the XML type and i and put at the XML type
                ordered.removeAt(i)
                ordered.add(xml, mediaType)
            }
        }
        return ordered
    }

    /**
     * @return whether Grails reads the media type as XML: {@code application/xml}, {@code text/xml}, or, where no
     * configured type names it, {@code application/problem+xml}, which Grails reads as {@code application/xml}
     */
    private static boolean isXmlType(MediaType mediaType, boolean problemXmlAsXml) {
        return mediaType.type == 'application' && mediaType.subtype == 'xml' ||
                mediaType.type == 'text' && mediaType.subtype == 'xml' ||
                problemXmlAsXml && mediaType.equalsTypeAndSubtype(MediaType.APPLICATION_PROBLEM_XML)
    }

    /**
     * The configured MIME types media types stand for, in the order of the media types, each once, with the parameters,
     * such as the quality, of the first that stands for it; and a wildcard no type is configured with the name of, such as
     * {@code text/*}, where a configured type is compatible with it, as itself, of the {@code all} format, which
     * {@code withFormat} and {@code respond} answer, as Spring MVC answers a wildcard, in the first of their formats that
     * has a type compatible with it.
     */
    private MimeType[] acceptedMimeTypes(List<MediaType> requested) {
        Set<MimeType> listed = Collections.newSetFromMap(new IdentityHashMap<MimeType, Boolean>())
        Set<String> wildcards = []
        List<MimeType> resolved = []
        for (MediaType mediaType in requested) {
            MimeType configured = configuredFor(mediaType)
            if (configured != null) {
                if (listed.add(configured)) {
                    resolved << withParameters(configured, mediaType)
                }
                continue
            }
            String name = "${mediaType.type}/${mediaType.subtype}"
            if (!mediaType.isConcrete() && compatibleWithConfigured(mediaType) && wildcards.add(name)) {
                resolved << withParameters(new MimeType(name, ALL_FORMAT), mediaType)
            }
        }
        return resolved as MimeType[]
    }

    /**
     * Whether a type a request accepts accepts a format: a type of the format; or, of the {@code all} format, the type
     * {@code *&#47;*}, which accepts any, or a wildcard, such as {@code text/*}, which accepts a format of which a
     * configured type is compatible with it.
     *
     * @param accepted a type {@code response.mimeTypes} resolves
     * @param format a format
     * @return whether it accepts the format
     * @since 9.0
     */
    boolean acceptsFormat(MimeType accepted, String format) {
        if (accepted == null) {
            return false
        }
        if (accepted.extension != ALL_FORMAT) {
            return accepted.extension == format
        }
        MediaType wildcard = mediaTypeOf(new MimeType(accepted.name))
        if (wildcard == null || wildcard.isWildcardType()) {
            return wildcard != null
        }
        for (int i = 0; i < mimeTypes.length; i++) {
            if (mimeTypes[i].extension == format && mediaTypes[i] != null && wildcard.isCompatibleWith(mediaTypes[i])) {
                return true
            }
        }
        return false
    }

    /** Whether a configured type that is not itself a wildcard is compatible with a wildcard. */
    private boolean compatibleWithConfigured(MediaType wildcard) {
        return mediaTypes.any { MediaType configured -> configured != null && configured.isConcrete() && wildcard.isCompatibleWith(configured) }
    }

    /**
     * The configured MIME type a media type stands for: the one of its name, of its version where several are; and, for
     * a type that is not a wildcard with the {@code +json} or {@code +xml} suffix no type is configured with the name of,
     * the own type of the format of that suffix, {@code application/json} or {@code application/xml} where that is
     * configured, and otherwise the first type of the format.
     *
     * @return the type, or {@code null} where no configured type stands for it
     */
    private MimeType configuredFor(MediaType mediaType) {
        String name = "${mediaType.type}/${mediaType.subtype}"
        List<MimeType> named = mimeTypes.findAll { MimeType configured -> configured.name?.equalsIgnoreCase(name) } as List<MimeType>
        if (named) {
            String version = mediaType.getParameter('v')
            return (version ? named.find { MimeType configured -> configured.version == version } : null) ?: named.first()
        }
        String format = mediaType.isConcrete() ? mediaType.subtypeSuffix?.toLowerCase(Locale.ROOT) : null
        if (format != null && SUFFIX_FORMATS.contains(format)) {
            String own = "application/${format}"
            return mimeTypes.find { MimeType configured ->
                configured.extension == format && configured.name?.equalsIgnoreCase(own)
            } ?: mimeTypes.find { MimeType configured -> configured.extension == format }
        }
        return null
    }

    /** A configured MIME type with the parameters of the media type it is resolved for, as Grails reads them. */
    private static MimeType withParameters(MimeType configured, MediaType mediaType) {
        Map<String, String> parameters = [:]
        mediaType.parameters.each { String name, String value -> parameters.put(name.toLowerCase(Locale.ROOT), value) }
        return new MimeType(configured.name, configured.extension, parameters)
    }

    /** The media type of a configured MIME type, without the quality Grails gives every MIME type. */
    private static MediaType mediaTypeOf(MimeType mimeType) {
        try {
            return SpringMediaTypeAdapter.toMediaType(mimeType)?.removeQualityValue()
        }
        catch (IllegalArgumentException ignored) {
            return null
        }
    }

    private String resolveAcceptHeader(HttpServletRequest request) {
        return ignoresAcceptHeader(request) ? null : request.getHeader(HttpHeaders.ACCEPT)
    }

    /**
     * @param config the application's configuration
     * @return the patterns of the user agents whose {@code Accept} header is ignored, any of which is found anywhere in the
     * {@code User-Agent} header: each regular expression {@code grails.mime.disable.accept.header.userAgents} lists, as a
     * list or a comma-separated string, on its own and ignoring case, so that the flags and groups of one do not change
     * another, a blank one left out; a {@link Pattern} configured itself, or listed, as it is; or none
     * @throws IllegalArgumentException if it lists one that is not a regular expression
     */
    static List<Pattern> disabledUserAgentPatterns(Config config) {
        Object configured = config.getProperty(Settings.MIME_DISABLE_ACCEPT_HEADER_FOR_USER_AGENTS, Object)
        if (configured instanceof Pattern) {
            return [(Pattern) configured]
        }
        Collection userAgents = configured instanceof Collection ? (Collection) configured :
                configured instanceof CharSequence ? configured.toString().split(',')*.trim() : []
        List<Pattern> patterns = []
        for (Object userAgent in userAgents) {
            if (userAgent == null || String.valueOf(userAgent).isBlank()) {
                continue
            }
            if (userAgent instanceof Pattern) {
                // as it is, with its own case and flags
                patterns << (Pattern) userAgent
                continue
            }
            try {
                patterns << Pattern.compile(String.valueOf(userAgent), Pattern.CASE_INSENSITIVE)
            }
            catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("${Settings.MIME_DISABLE_ACCEPT_HEADER_FOR_USER_AGENTS} lists " +
                        "[${userAgent}], which is not a regular expression: ${e.description}", e)
            }
        }
        patterns
    }
}
