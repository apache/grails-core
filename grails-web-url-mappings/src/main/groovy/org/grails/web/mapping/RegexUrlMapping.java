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
package org.grails.web.mapping;

import grails.core.GrailsApplication;
import grails.core.GrailsControllerClass;
import grails.gorm.validation.Constrained;
import grails.gorm.validation.ConstrainedProperty;
import grails.plugins.VersionComparator;
import grails.util.GrailsStringUtils;
import grails.web.mapping.UrlMapping;
import grails.web.mapping.UrlMappingData;
import grails.web.mapping.UrlMappingInfo;
import grails.web.mapping.exceptions.UrlMappingException;
import groovy.lang.Closure;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.servlet.mvc.exceptions.ControllerExecutionException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.Assert;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;
import org.springframework.web.context.request.RequestContextHolder;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * <p>A UrlMapping implementation that takes a Grails URL pattern and turns it into a regex matcher so that
 * URLs can be matched and information captured from the match.</p>
 * <p>
 * <p>A Grails URL pattern is not a regex, but is an extension to the form defined by Apache Ant and used by
 * Spring AntPathMatcher. Unlike regular Ant paths Grails URL patterns allow for capturing groups in the form:</p>
 * <p>
 * <code>/blog/(*)&#47;**</code>
 * <p>The parenthesis define a capturing group. This implementation transforms regular Ant paths into regular expressions
 * that are able to use capturing groups</p>
 *
 * @author Graeme Rocher
 * @see org.springframework.util.AntPathMatcher
 * @since 0.5
 */
@SuppressWarnings("rawtypes")
public class RegexUrlMapping extends AbstractUrlMapping {

    public static final String FORMAT_PARAMETER = "format";
    private Pattern[] patterns;
    private Map<Integer, List<Pattern>> patternByTokenCount = new HashMap<Integer, List<Pattern>>();
    private UrlMappingData urlData;
    private static final String DEFAULT_ENCODING = "UTF-8";
    private static final Logger LOG = LoggerFactory.getLogger(RegexUrlMapping.class);
    public static final Pattern DOUBLE_WILDCARD_PATTERN = Pattern.compile("\\(\\*\\*?\\)\\??");
    public static final Pattern OPTIONAL_EXTENSION_WILDCARD_PATTERN = Pattern.compile("[^/]+\\(\\.\\(\\*\\)\\)");

    /**
     * Constructs a new RegexUrlMapping for the given pattern that maps to the specified URI
     *
     * @param data The pattern
     * @param uri The URI
     * @param constraints Any constraints etc.
     * @param grailsApplication The GrailsApplication instance
     */
    public RegexUrlMapping(UrlMappingData data, URI uri, ConstrainedProperty[] constraints, GrailsApplication grailsApplication) {
        super(uri, constraints, grailsApplication);
        parse(data, constraints);
    }
    public RegexUrlMapping(UrlMappingData data, Object controllerName, Object actionName, Object namespace, Object pluginName, Object viewName, String httpMethod, String version, ConstrainedProperty[] constraints, GrailsApplication grailsApplication) {
        this(null, data, controllerName, actionName, namespace, pluginName, viewName, httpMethod, version, constraints, grailsApplication);
    }

    /**
     * Constructs a new RegexUrlMapping for the given pattern, controller name, action name and constraints.
     *
     * @param data           An instance of the UrlMappingData class that holds necessary information of the URL mapping
     * @param controllerName The name of the controller the URL maps to (required)
     * @param actionName     The name of the action the URL maps to
     * @param namespace The controller namespace
     * @param pluginName The name of the plugin which provided the controller
     * @param viewName       The name of the view as an alternative to the name of the action. If the action is specified it takes precedence over the view name during mapping
     * @param httpMethod     The http method
     * @param version     The version
     * @param constraints    A list of ConstrainedProperty instances that relate to tokens in the URL
     * @param grailsApplication The Grails application
     * @see ConstrainedProperty
     */
    public RegexUrlMapping(Object redirectInfo, UrlMappingData data, Object controllerName, Object actionName, Object namespace, Object pluginName, Object viewName, String httpMethod, String version, ConstrainedProperty[] constraints, GrailsApplication grailsApplication) {
        super(redirectInfo, controllerName, actionName, namespace, pluginName, viewName, constraints != null ? constraints : new ConstrainedProperty[0], grailsApplication);
        if (httpMethod != null) {
            this.httpMethod = httpMethod;
        }
        if (version != null) {
            this.version = version;
        }
        parse(data, constraints);
    }

    private void parse(UrlMappingData data, ConstrainedProperty[] constraints) {
        Assert.notNull(data, "Argument [data] cannot be null");

        String[] urls = data.getLogicalUrls();
        urlData = data;
        patterns = new Pattern[urls.length];

        for (int i = 0; i < urls.length; i++) {
            String url = urls[i];
            Integer slashCount = org.springframework.util.StringUtils.countOccurrencesOf(url, "/");
            List<Pattern> tokenCountPatterns = patternByTokenCount.get(slashCount);
            if (tokenCountPatterns == null) {
                tokenCountPatterns = new ArrayList<>();
                patternByTokenCount.put(slashCount, tokenCountPatterns);
            }

            Pattern pattern = convertToRegex(url);
            if (pattern == null) {
                throw new IllegalStateException("Cannot use null pattern in regular expression mapping for url [" + data.getUrlPattern() + "]");
            }
            tokenCountPatterns.add(pattern);
            this.patterns[i] = pattern;

        }

        if (constraints != null) {
            String[] tokens = data.getTokens();
            int pos = 0;
            int currentToken = 0;
            int tokensLength = tokens.length - 1;
            int constraintUpperBound = constraints.length;
            if (data.hasOptionalExtension()) {
                constraintUpperBound--;
                setNullable(constraints[constraintUpperBound]);
            }

            for (int i = 0; i < constraintUpperBound; i++) {
                ConstrainedProperty constraint = constraints[i];
                if (currentToken > tokensLength) break;
                String token = tokens[currentToken];
                int shiftLength = 3;
                pos = token.indexOf(CAPTURED_WILDCARD, pos);
                while(pos == -1) {
                    boolean isLastToken = currentToken == tokensLength-1;
                    if (currentToken < tokensLength) {

                        token = tokens[++currentToken];
                        // special handling for last token to deal with optional extension
                        if (isLastToken) {
                            if (token.startsWith(CAPTURED_WILDCARD + '?') ) {
                                setNullable(constraint);
                            }
                            if (token.endsWith(OPTIONAL_EXTENSION_WILDCARD + '?')) {
                                setNullable(constraints[constraints.length-1]);
                            }
                        }
                        else {
                            pos = token.indexOf(CAPTURED_WILDCARD, pos);
                        }
                    }
                    else {
                        break;
                    }
                }

                if (pos != -1 && pos + shiftLength < token.length() && token.charAt(pos + shiftLength) == '?') {
                    setNullable(constraint);
                }

                // Move on to the next place-holder.
                pos += shiftLength;
                if (token.indexOf(CAPTURED_WILDCARD, pos) == -1) {
                    currentToken++;
                    pos = 0;
                }
            }
        }
    }

    private void setNullable(ConstrainedProperty constraint) {
        ConstrainedProperty constrainedProperty = constraint;
        if(!constrainedProperty.isNullable()) {
               constrainedProperty.applyConstraint(ConstrainedProperty.NULLABLE_CONSTRAINT, true);
        }
    }

    /**
     * Converts a Grails URL provides via the UrlMappingData interface to a regular expression.
     *
     * @param url The URL to convert
     * @return A regex Pattern objet
     */
    protected Pattern convertToRegex(String url) {
        Pattern regex;
        String pattern = null;
        try {
            // Escape any characters that have special meaning in regular expressions,
            // such as '.' and '+'.
            pattern = url.replace(".", "\\.");
            pattern = pattern.replace("+", "\\+");

            int lastSlash = pattern.lastIndexOf('/');

            String urlRoot = lastSlash > -1 ? pattern.substring(0, lastSlash) : pattern;
            String urlEnd = lastSlash > -1 ? pattern.substring(lastSlash, pattern.length()) : "";

            // Now replace "*" with "[^/]" and "**" with ".*".
            pattern = "^" + urlRoot
                    .replace("(\\.(*))", "(\\.[^/]+)?")
                    .replaceAll("([^\\*])\\*([^\\*])", "$1[^/]+?$2")
                    .replaceAll("([^\\*])\\*$", "$1[^/]+?")
                    .replaceAll("\\*\\*", ".*");

            if("/(*)(\\.(*))".equals(urlEnd)) {
                // shortcut this common special case which will
                // happen any time a URL mapping ends with a pattern like
                // /$someVariable(.$someExtension)
                pattern += "/([^/]+)\\.([^/.]+)?";
            } else {
                pattern += urlEnd
                        .replace("(\\.(*))", "(\\.[^/]+)?")
                        .replaceAll("([^\\*])\\*([^\\*])", "$1[^/]+?$2")
                        .replaceAll("([^\\*])\\*$", "$1[^/]+?")
                        .replaceAll("\\*\\*", ".*")
                        .replaceAll("\\(\\[\\^\\/\\]\\+\\)\\\\\\.", "([^/.]+?)\\\\.")
                        .replaceAll("\\(\\[\\^\\/\\]\\+\\)\\?\\\\\\.", "([^/.]+?)\\?\\\\.")
                ;
            }
            pattern += "/??$";
            regex = Pattern.compile(pattern);
        }
        catch (PatternSyntaxException pse) {
            throw new UrlMappingException("Error evaluating mapping for pattern [" + pattern +
                    "] from Grails URL mappings: " + pse.getMessage(), pse);
        }

        return regex;
    }

    /**
     * Matches the given URI and returns a DefaultUrlMappingInfo instance or null
     *
     * @param uri The URI to match
     * @return A UrlMappingInfo instance or null
     * @see grails.web.mapping.UrlMappingInfo
     */
    public UrlMappingInfo match(String uri) {
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(uri);
            if (m.matches()) {
                UrlMappingInfo urlInfo = createUrlMappingInfo(uri, m);
                if (urlInfo != null) {
                    return urlInfo;
                }
            }
        }
        return null;
    }

    /**
     * @see grails.web.mapping.UrlMapping
     */
    public String createURL(Map paramValues, String encoding) {
        return createURLInternal(paramValues, encoding, true);
    }

    @SuppressWarnings({"unchecked"})
    private String createURLInternal(Map paramValues, String encoding, boolean includeContextPath) {

        if (encoding == null) encoding = "utf-8";

        String contextPath = "";
        if (includeContextPath) {
            GrailsWebRequest webRequest = (GrailsWebRequest) RequestContextHolder.getRequestAttributes();
            if (webRequest != null) {
                contextPath = webRequest.getContextPath();
            }
        }
        if (paramValues == null) paramValues = Collections.emptyMap();
        StringBuilder uri = new StringBuilder(contextPath);
        Set usedParams = new HashSet();


        String[] tokens = urlData.getTokens();
        int paramIndex = 0;
        for (int i = 0; i < tokens.length; i++) {
            String token = tokens[i];
            if (i == tokens.length - 1 && urlData.hasOptionalExtension()) {
                token += OPTIONAL_EXTENSION_WILDCARD;
            }
            Matcher m = OPTIONAL_EXTENSION_WILDCARD_PATTERN.matcher(token);
            if (m.find()) {

                boolean tokenSet = false;
                if (token.startsWith(CAPTURED_WILDCARD)) {
                    ConstrainedProperty prop = constraints[paramIndex++];
                    String propName = prop.getPropertyName();

                    Object value = paramValues.get(propName);
                    usedParams.add(propName);

                    if (value != null) {
                        token = token.replaceFirst(DOUBLE_WILDCARD_PATTERN.pattern(), Matcher.quoteReplacement(value.toString()));
                        tokenSet = true;
                    }
                    else {
                        token = token.replaceFirst(DOUBLE_WILDCARD_PATTERN.pattern(), "");
                    }
                }
                else {
                    tokenSet = true;
                }
                if(tokenSet) {

                    uri.append(SLASH);
                }
                ConstrainedProperty prop = constraints[paramIndex++];
                String propName = prop.getPropertyName();
                Object value = paramValues.get(propName);
                usedParams.add(propName);
                if (value != null) {
                    String ext = "." + value;
                    uri.append(token.replace(OPTIONAL_EXTENSION_WILDCARD+'?', ext).replace(OPTIONAL_EXTENSION_WILDCARD, ext));
                }
                else {
                    uri.append(token.replace(OPTIONAL_EXTENSION_WILDCARD+'?', "").replace(OPTIONAL_EXTENSION_WILDCARD, ""));
                }

                continue;
            }
            if (token.endsWith("?")) {
                token = token.substring(0,token.length()-1);
            }
            m = DOUBLE_WILDCARD_PATTERN.matcher(token);
            if (m.find()) {
                StringBuffer buf = new StringBuffer();
                do {
                    ConstrainedProperty prop = constraints[paramIndex++];
                    String propName = prop.getPropertyName();
                    Object value = paramValues.get(propName);
                    usedParams.add(propName);
                    if (value == null && !prop.isNullable()) {
                        throw new UrlMappingException("Unable to create URL for mapping [" + this +
                                "] and parameters [" + paramValues + "]. Parameter [" +
                                prop.getPropertyName() + "] is required, but was not specified!");
                    }
                    else if (value == null) {
                        m.appendReplacement(buf, "");
                    }
                    else {
                        m.appendReplacement(buf, Matcher.quoteReplacement(value.toString()));
                    }
                }
                while (m.find());

                m.appendTail(buf);

                try {
                    String v = buf.toString();
                    if (v.indexOf(SLASH) > -1 && CAPTURED_DOUBLE_WILDCARD.equals(token)) {
                        // individually URL encode path segments
                        if (v.startsWith(SLASH)) {
                            // get rid of leading slash
                            v = v.substring(SLASH.length());
                        }
                        String[] segs = v.split(SLASH);
                        for (String segment : segs) {
                            uri.append(SLASH).append(encode(segment, encoding));
                        }
                    }
                    else if (v.length() > 0) {
                        // original behavior
                        uri.append(SLASH).append(encode(v, encoding));
                    }
                    else {
                        // Stop processing tokens once we hit an empty one.
                        break;
                    }
                }
                catch (UnsupportedEncodingException e) {
                    throw new ControllerExecutionException("Error creating URL for parameters [" +
                            paramValues + "], problem encoding URL part [" + buf + "]: " + e.getMessage(), e);
                }
            }
            else {
                uri.append(SLASH).append(token);
            }
        }
        populateParameterList(paramValues, encoding, uri, usedParams);

        if (LOG.isDebugEnabled()) {
            LOG.debug("Created reverse URL mapping [" + uri.toString() + "] for parameters [" + paramValues + "]");
        }
        return uri.toString();
    }

    protected String encode(String s, String encoding) throws UnsupportedEncodingException {
        return URLEncoder.encode(s, encoding).replaceAll("\\+", "%20");
    }

    public String createURL(Map paramValues, String encoding, String fragment) {
        String url = createURL(paramValues, encoding);
        return createUrlWithFragment(url, fragment, encoding);
    }

    public String createURL(String controller, String action, Map paramValues, String encoding) {
        return createURL(controller, action, null, null, paramValues, encoding);
    }

    public String createURL(String controller, String action, String pluginName, Map parameterValues, String encoding) {
        return createURL(controller, action, null, pluginName, parameterValues, encoding);
    }

    public String createURL(String controller, String action, String namespace, String pluginName, Map paramValues, String encoding) {
        return createURLInternal(controller, action, namespace, pluginName, paramValues, encoding, true);
    }

    @SuppressWarnings("unchecked")
    private String createURLInternal(String controller, String action, String namespace, String pluginName, Map paramValues,
                                     String encoding, boolean includeContextPath) {

        if (paramValues == null) paramValues = new HashMap();

        boolean hasController = !GrailsStringUtils.isBlank(controller);
        boolean hasAction = !GrailsStringUtils.isBlank(action);
        boolean hasPlugin = !GrailsStringUtils.isBlank(pluginName);
        boolean hasNamespace = !GrailsStringUtils.isBlank(namespace);

        try {
            if (hasController) {
                paramValues.put(CONTROLLER, controller);
            }
            if (hasAction) {
                paramValues.put(ACTION, action);
            }
            if (hasPlugin) {
                paramValues.put(PLUGIN, pluginName);
            }
            if (hasNamespace) {
                paramValues.put(NAMESPACE, namespace);
            }

            return createURLInternal(paramValues, encoding, includeContextPath);
        }
        finally {
            if (hasController) {
                paramValues.remove(CONTROLLER);
            }
            if (hasAction) {
                paramValues.remove(ACTION);
            }
            if (hasPlugin) {
                paramValues.remove("plugin");
            }
        }
    }

    public String createRelativeURL(String controller, String action, Map paramValues, String encoding) {
        return createRelativeURL(controller, action, null, null, paramValues, encoding);
    }
    public String createRelativeURL(String controller, String action, String pluginName, Map paramValues, String encoding) {
        return createRelativeURL(controller, action, null, pluginName, paramValues, encoding);
    }

    public String createRelativeURL(String controller, String action, String namespace, String pluginName, Map paramValues, String encoding) {
        return createURLInternal(controller, action, namespace, pluginName, paramValues, encoding, false);
    }

    public String createRelativeURL(String controller, String action, Map paramValues, String encoding, String fragment) {
        return createRelativeURL(controller, action, null, null, paramValues, encoding, fragment);
    }

    public String createRelativeURL(String controller, String action, String namespace, String pluginName, Map paramValues,
                                    String encoding, String fragment) {
        final String url = createURLInternal(controller, action, namespace, pluginName, paramValues, encoding, false);
        return createUrlWithFragment(url, fragment, encoding);
    }

    public String createURL(String controller, String action, Map paramValues,
                            String encoding, String fragment) {
        return createURL(controller, action, null, null, paramValues, encoding, fragment);
    }

    public String createURL(String controller, String action, String namespace, String pluginName, Map paramValues,
                            String encoding, String fragment) {
        String url = createURL(controller, action, namespace, pluginName, paramValues, encoding);
        return createUrlWithFragment(url, fragment, encoding);
    }

    private String createUrlWithFragment(String url, String fragment, String encoding) {
        if (fragment != null) {
            // A 'null' encoding will cause an exception, so default to 'UTF-8'.
            if (encoding == null) {
                encoding = DEFAULT_ENCODING;
            }

            try {
                return url + '#' + URLEncoder.encode(fragment, encoding);
            }
            catch (UnsupportedEncodingException ex) {
                throw new ControllerExecutionException("Error creating URL  [" + url +
                        "], problem encoding URL fragment [" + fragment + "]: " + ex.getMessage(), ex);
            }
        }

        return url;
    }

    @SuppressWarnings("unchecked")
    private void populateParameterList(Map paramValues, String encoding, StringBuilder uri, Set usedParams) {
        boolean addedParams = false;
        usedParams.add("controller");
        usedParams.add("action");
        usedParams.add("namespace");
        usedParams.add("plugin");

        // A 'null' encoding will cause an exception, so default to 'UTF-8'.
        if (encoding == null) {
            encoding = DEFAULT_ENCODING;
        }

        for (Object o1 : paramValues.keySet()) {
            String name = o1.toString();
            if (!usedParams.contains(name)) {
                if (!addedParams) {
                    uri.append(QUESTION_MARK);
                    addedParams = true;
                }
                else {
                    uri.append(AMPERSAND);
                }
                Object value = paramValues.get(name);
                if (value != null && value instanceof Collection) {
                    Collection multiValues = (Collection) value;
                    for (Iterator j = multiValues.iterator(); j.hasNext();) {
                        Object o = j.next();
                        appendValueToURI(encoding, uri, name, o);
                        if (j.hasNext()) {
                            uri.append(AMPERSAND);
                        }
                    }
                }
                else if (value != null && value.getClass().isArray()) {
                    Object[] multiValues = (Object[]) value;
                    for (int j = 0; j < multiValues.length; j++) {
                        Object o = multiValues[j];
                        appendValueToURI(encoding, uri, name, o);
                        if (j + 1 < multiValues.length) {
                            uri.append(AMPERSAND);
                        }
                    }
                }
                else {
                    appendValueToURI(encoding, uri, name, value);
                }
            }
        }
    }

    private void appendValueToURI(String encoding, StringBuilder uri, String name, Object value) {
        try {
            uri.append(URLEncoder.encode(name, encoding)).append('=')
                    .append(URLEncoder.encode(value != null ? value.toString() : "", encoding));
        }
        catch (UnsupportedEncodingException e) {
            throw new ControllerExecutionException("Error redirecting request for url [" + name + ":" +
                    value + "]: " + e.getMessage(), e);
        }
    }

    public UrlMappingData getUrlData() {
        return urlData;
    }

    @SuppressWarnings("unchecked")
    private UrlMappingInfo createUrlMappingInfo(String uri, Matcher m) {
        boolean hasOptionalExtension = urlData.hasOptionalExtension();
        Map params = new HashMap();
        Errors errors = new MapBindingResult(params, "urlMapping");
        int groupCount = m.groupCount();
        String lastGroup = null;
        for (int i = 0; i < groupCount; i++) {
            lastGroup = m.group(i + 1);
            // if null optional.. ignore
            if (i == groupCount - 1 && hasOptionalExtension) {
                ConstrainedProperty cp = constraints[constraints.length-1];
                cp.validate(this, lastGroup, errors);

                if (errors.hasErrors()) {
                    return null;
                }

                String propertyName = cp.getPropertyName();
                if(lastGroup != null) {
                    if(FORMAT_PARAMETER.equals(propertyName) && lastGroup.startsWith(".")) {
                        lastGroup = lastGroup.substring(1);
                    }
                }
                // if the format is specified but the value is empty, ignore it
                if (!(FORMAT_PARAMETER.equals(propertyName) && GrailsStringUtils.isEmpty(lastGroup))) {
                    params.put(propertyName, lastGroup);
                }
                break;
            }
            else {
                if (lastGroup == null) continue;
                int j = lastGroup.indexOf('?');
                if (j > -1) {
                    lastGroup = lastGroup.substring(0, j);
                }
                if (constraints.length > i) {
                    ConstrainedProperty cp = constraints[i];
                    cp.validate(this, lastGroup, errors);

                    if (errors.hasErrors()) {
                        return null;
                    }

                    String propertyName = cp.getPropertyName();
                    if(FORMAT_PARAMETER.equals(propertyName) && lastGroup.startsWith(".")) {
                        lastGroup = lastGroup.substring(1);
                    }
                    // if the format is specified but the value is empty, ignore it
                    if (!(FORMAT_PARAMETER.equals(propertyName) && GrailsStringUtils.isEmpty(lastGroup))) {
                        params.put(propertyName, lastGroup);
                    }
                }
            }

        }


        for (Object key : parameterValues.keySet()) {
            params.put(key, parameterValues.get(key));
        }

        if (controllerName == null) {
            controllerName = createRuntimeConstraintEvaluator(GrailsControllerClass.CONTROLLER, constraints);
        }

        if (actionName == null) {
            actionName = createRuntimeConstraintEvaluator(GrailsControllerClass.ACTION, constraints);
        }

        if (namespace == null) {
            namespace = createRuntimeConstraintEvaluator(NAMESPACE, constraints);
        }

        if (viewName == null) {
            viewName = createRuntimeConstraintEvaluator(GrailsControllerClass.VIEW, constraints);
        }

        if(redirectInfo == null) {
            redirectInfo = createRuntimeConstraintEvaluator("redirect", constraints);
        }

        DefaultUrlMappingInfo info;
        if (forwardURI != null && controllerName == null) {
            info = new DefaultUrlMappingInfo(forwardURI,getHttpMethod(), urlData, grailsApplication);
        }
        else if (viewName != null && controllerName == null) {
            info = new DefaultUrlMappingInfo(viewName, params, urlData, grailsApplication);
        }
        else {
            info = new DefaultUrlMappingInfo(redirectInfo, controllerName, actionName, namespace, pluginName, getViewName(), getHttpMethod(),getVersion(), params, urlData, grailsApplication);
        }

        if (parseRequest) {
            info.setParsingRequest(parseRequest);
        }

        return info;
    }

    /**
     * This method will look for a constraint for the given name and return a closure that when executed will
     * attempt to evaluate its value from the bound request parameters at runtime.
     *
     * @param name        The name of the constrained property
     * @param constraints The array of current ConstrainedProperty instances
     * @return Either a Closure or null
     */
    private Object createRuntimeConstraintEvaluator(final String name, ConstrainedProperty[] constraints) {
        if (constraints == null) return null;

        for (ConstrainedProperty constraint : constraints) {
            if (constraint.getPropertyName().equals(name)) {
                return new Closure(this) {
                    private static final long serialVersionUID = -2404119898659287216L;

                    @Override
                    public Object call(Object... objects) {
                        GrailsWebRequest webRequest = (GrailsWebRequest) RequestContextHolder.currentRequestAttributes();
                        return webRequest.getParams().get(name);
                    }
                };
            }
        }
        return null;
    }

    public String[] getLogicalMappings() {
        return urlData.getLogicalUrls();
    }

    /**
     * Compares this {@link UrlMapping} instance with the specified {@link UrlMapping} instance and deals with URL mapping precedence rules.
     * <p>URL Mapping Precedence Order
     * <p>Less wildcard tokens:
     * <ol>
     * <li> /foo          &lt;- match
     * <li> /foo/(*) </li>
     * <li> /foo/(*)/bar/  &lt;- match
     * <li> /foo/(*)/(*) </li>
     * </ol>
     * <p>More static tokens:
     * <ol>
     * <li> /foo/(*)/bar   &lt;- match
     * <li> /foo/(*) </li>
     * </ol>
     *
     * @param o the {@link UrlMapping} object to be compared.
     * @return a negative integer, zero, or a positive integer as this {@link UrlMapping} is less than, equal to, or greater than the specified {@link UrlMapping}.
     */
    public int compareTo(Object o) {
        if (!(o instanceof UrlMapping)) {
            throw new IllegalArgumentException("Cannot compare with Object [" + o + "]. It is not an instance of UrlMapping!");
        }

        if (equals(o)) return 0;

        UrlMapping other = (UrlMapping) o;

        // this wild card count
        final int thisStaticTokenCount = getStaticTokenCount(this);
        final int thisSingleWildcardCount = getSingleWildcardCount(this);
        final int thisDoubleWildcardCount = getDoubleWildcardCount(this);

        // the other wild card count
        final int otherStaticTokenCount = getStaticTokenCount(other);
        final int otherSingleWildcardCount = getSingleWildcardCount(other);
        final int otherDoubleWildcardCount = getDoubleWildcardCount(other);

        final boolean hasWildCards = thisDoubleWildcardCount > 0 || thisSingleWildcardCount > 0;
        final boolean otherHasWildCards = otherDoubleWildcardCount > 0 || otherSingleWildcardCount > 0;

        // Always prioritise the / root mapping
        boolean isThisRoot = thisStaticTokenCount == 0 && thisSingleWildcardCount == 0 && thisDoubleWildcardCount == 0;
        boolean isThatRoot = otherStaticTokenCount == 0 && otherDoubleWildcardCount == 0 && otherSingleWildcardCount == 0;

        if(isThisRoot && isThatRoot) {
            return evaluatePluginOrder(other);
        }
        else if(isThisRoot) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Mapping [{}] has a higher precedence than [{}] because it is the root", this.toString(), other.toString());
            }
            return 1;
        }
        else if(isThatRoot) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Mapping [{}] has a lower precedence than [{}] because the latter is the root", this.toString(), other.toString());
            }
            return -1;
        }

        if (otherStaticTokenCount == 0 && thisStaticTokenCount > 0) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Mapping [{}] has a higher precedence than [{}] because it has more path tokens", this.toString(), other.toString());
            }
            return 1;
        }

        if (thisStaticTokenCount == 0 && otherStaticTokenCount > 0) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Mapping [{}] has a lower precedence than [{}] because it has fewer path tokens", this.toString(), other.toString());
            }
            return -1;
        }

        final int thisStaticAndWildcardTokenCount = getStaticAndWildcardTokenCount(this);
        final int otherStaticAndWildcardTokenCount = getStaticAndWildcardTokenCount(other);

        if (otherStaticAndWildcardTokenCount==0 && thisStaticAndWildcardTokenCount>0) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Mapping [{}] has a higher precedence than [{}] because it has more path tokens [{} vs {}]", this.toString(), other.toString(), thisStaticAndWildcardTokenCount, otherStaticAndWildcardTokenCount);
            }
            return 1;
        }
        if (thisStaticAndWildcardTokenCount==0 && otherStaticAndWildcardTokenCount>0) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Mapping [{}] has a higher precedence than [{}] because the latter has more path tokens [{} vs {}]", this.toString(), other.toString(), thisStaticAndWildcardTokenCount, otherStaticAndWildcardTokenCount);
            }
            return -1;
        }

        final int staticDiff = thisStaticTokenCount - otherStaticTokenCount;
        if (staticDiff < 0 && !otherHasWildCards) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Mapping [{}] has a lower precedence than [{}] because the latter has more concrete path tokens [{} vs {}]", this.toString(), other.toString(), thisStaticTokenCount, otherStaticTokenCount);
            }
            return -1;
        }
        else if(staticDiff > 0 && !hasWildCards) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Mapping [{}] has a higher precedence than [{}] because it has more concrete path tokens [{} vs {}]", this.toString(), other.toString(), thisStaticTokenCount, otherStaticTokenCount);
            }
            return 1;
        }

        String[] thisTokens = getUrlData().getTokens();
        String[] otherTokens = other.getUrlData().getTokens();
        final int thisTokensLength = thisTokens.length;
        final int otherTokensLength = otherTokens.length;

        int greaterLength = thisTokensLength > otherTokensLength ? thisTokensLength : otherTokensLength;
        for (int i = 0; i < greaterLength; i++) {
            final boolean thisHasMoreTokens = i < thisTokensLength;
            final boolean otherHasMoreTokens = i < otherTokensLength;

            boolean thisTokenIsWildcard = !thisHasMoreTokens || isSingleWildcard(thisTokens[i]);
            boolean otherTokenIsWildcard = !otherHasMoreTokens || isSingleWildcard(otherTokens[i]);
            if (thisTokenIsWildcard && !otherTokenIsWildcard) {
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Mapping [{}] has a lower precedence than [{}] because the latter contains more concrete tokens", this.toString(), other.toString());
                }
                return -1;
            }
            if (!thisTokenIsWildcard && otherTokenIsWildcard) {
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Mapping [{}] has a higher precedence than [{}] because it contains more concrete tokens", this.toString(), other.toString());
                }
                return 1;
            }
        }

        final int doubleWildcardDiff = otherDoubleWildcardCount - thisDoubleWildcardCount;
        if (doubleWildcardDiff != 0) {
            if(LOG.isDebugEnabled()) {
                if(doubleWildcardDiff > 0) {
                    LOG.debug("Mapping [{}] has a higher precedence than [{}] due containing more double wild cards [{} vs. {}]", this.toString(), other.toString(), thisDoubleWildcardCount, otherDoubleWildcardCount);
                }
                else if(doubleWildcardDiff < 0) {
                    LOG.debug("Mapping [{}] has a lower precedence than [{}] due to the latter containing more double wild cards [{} vs. {}]", this.toString(), other.toString(), thisDoubleWildcardCount, otherDoubleWildcardCount);
                }
            }
            return doubleWildcardDiff;
        }

        final int singleWildcardDiff = otherSingleWildcardCount - thisSingleWildcardCount;
        if (singleWildcardDiff != 0) {
            if(LOG.isDebugEnabled()) {
                if(singleWildcardDiff > 0) {
                    LOG.debug("Mapping [{}] has a higher precedence than [{}] because it contains more single wild card matches [{} vs. {}]", this.toString(), other.toString(), thisSingleWildcardCount, otherSingleWildcardCount);
                }
                else if(singleWildcardDiff < 0) {
                    LOG.debug("Mapping [{}] has a lower precedence than [{}] due to the latter containing more single wild card matches[{} vs. {}]", this.toString(), other.toString(), thisSingleWildcardCount, otherSingleWildcardCount);
                }
            }
            return singleWildcardDiff;
        }

        int thisConstraintCount = getAppliedConstraintsCount(this);
        int thatConstraintCount = getAppliedConstraintsCount(other);
        int constraintDiff = thisConstraintCount - thatConstraintCount;
        if (constraintDiff != 0) {
            if(LOG.isDebugEnabled()) {
                if(constraintDiff > 0) {
                    LOG.debug("Mapping [{}] has a higher precedence than [{}] since it defines more constraints [{} vs. {}]", this.toString(), other.toString(), thisConstraintCount, thatConstraintCount);
                }
                else if(constraintDiff < 0) {
                    LOG.debug("Mapping [{}] has a lower precedence than [{}] since the latter defines more constraints [{} vs. {}]", this.toString(), other.toString(), thisConstraintCount, thatConstraintCount);
                }
            }
            return constraintDiff;
        }

        int allDiff = (thisStaticTokenCount - otherStaticTokenCount) + (thisSingleWildcardCount - otherSingleWildcardCount) + (thisDoubleWildcardCount - otherDoubleWildcardCount);
        if(allDiff != 0) {
            if(LOG.isDebugEnabled()) {
                if(allDiff > 0) {
                    LOG.debug("Mapping [{}] has a higher precedence than [{}] due to the overall diff", this.toString(), other.toString());
                }
                else if(allDiff < 0) {
                    LOG.debug("Mapping [{}] has a lower precedence than [{}] due to the overall diff", this.toString(), other.toString());
                }
            }
            return allDiff;
        }

        String thisVersion = getVersion();
        String thatVersion = other.getVersion();
        if((thisVersion.equals(thatVersion))) {
            return evaluatePluginOrder(other);
        }
        else if(thisVersion.equals(ANY_VERSION) && !thatVersion.equals(ANY_VERSION)) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Mapping [{}] has a lower precedence than [{}] due to version precedence [{} vs {}]", this.toString(), other.toString(), thisVersion, thatVersion);
            }
            return -1;
        }
        else if(!thisVersion.equals(ANY_VERSION) && thatVersion.equals(ANY_VERSION)) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Mapping [{}] has a higher precedence than [{}] due to version precedence [{} vs {}]", this.toString(), other.toString(), thisVersion, thatVersion);
            }
            return 1;
        }
        else {
            int i = new VersionComparator().compare(thisVersion, thatVersion);

            if(i > 0) {
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Mapping [{}] has a higher precedence than [{}] due to version precedence [{} vs. {}]", this.toString(), other.toString(), thisVersion, thatVersion);
                }
                return 1;
            }
            else if(i < 0) {
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Mapping [{}] has a lower precedence than [{}] due to version precedence [{} vs. {}]", this.toString(), other.toString(), thisVersion, thatVersion);
                }
                return -1;
            }
            else {
                return evaluatePluginOrder(other);
            }
        }
    }

    private int evaluatePluginOrder(UrlMapping other) {
        if (isDefinedInPlugin() && !other.isDefinedInPlugin()) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Mapping [{}] has lower precedence than [{}] because the latter has priority over plugins", this.toString(), other.toString());
            }
            return -1;
        } else if (!isDefinedInPlugin() && other.isDefinedInPlugin()) {
            if(LOG.isDebugEnabled()) {
                LOG.debug("Mapping [{}] has higher precedence than [{}] because it has priority over plugins", this.toString(), other.toString());
            }
            return 1;
        } else {
            if (isDefinedInPlugin()) {
                if (pluginIndex > other.getPluginIndex()) {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Mapping [{}] has higher precedence than [{}] because it was loaded after", this.toString(), other.toString());
                    }
                    return 1;
                } else if (pluginIndex < other.getPluginIndex()) {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Mapping [{}] has lower precedence than [{}] because it was loaded before", this.toString(), other.toString());
                    }
                    return -1;
                } else {
                    if(LOG.isDebugEnabled()) {
                        LOG.debug("Mapping [{}] has equal precedence with mapping [{}]", this.toString(), other.toString());
                    }
                    return 0;
                }
            } else {
                if(LOG.isDebugEnabled()) {
                    LOG.debug("Mapping [{}] has equal precedence with mapping [{}]", this.toString(), other.toString());
                }
                return 0;
            }
        }
    }

    private int getAppliedConstraintsCount(UrlMapping mapping) {
        int count = 0;
        for (Constrained prop : mapping.getConstraints()) {
            if(prop instanceof ConstrainedProperty) {
                count += ((ConstrainedProperty)prop).getAppliedConstraints().size();
            }
        }
        return count;
    }

    private int getSingleWildcardCount(UrlMapping mapping) {
        String[] tokens = mapping.getUrlData().getTokens();
        int count = 0;
        for (String token : tokens) {
            if (isSingleWildcard(token)) count++;
        }
        return count;
    }

    private int getDoubleWildcardCount(UrlMapping mapping) {
        String[] tokens = mapping.getUrlData().getTokens();
        int count = 0;
        for (String token : tokens) {
            if (isDoubleWildcard(token)) count++;
        }
        return count;
    }

    private int getStaticTokenCount(UrlMapping mapping) {
        String[] tokens = mapping.getUrlData().getTokens();
        int count = 0;
        for (String token : tokens) {
            if (!isSingleWildcard(token) && !"".equals(token)) count++;
        }
        return count;
    }

    private boolean isSingleWildcard(String token) {
        return token.contains(WILDCARD) || token.contains(CAPTURED_WILDCARD);
    }

    private boolean isDoubleWildcard(String token) {
        return token.contains(DOUBLE_WILDCARD) || token.contains(CAPTURED_DOUBLE_WILDCARD);
    }

    private int getStaticAndWildcardTokenCount(UrlMapping mapping) {
        String[] tokens = mapping.getUrlData().getTokens();
        int count = 0;
        for (String token : tokens) {
            token = token.replace(OPTIONAL_EXTENSION_WILDCARD, "").replace(CAPTURED_DOUBLE_WILDCARD,"").replace(CAPTURED_WILDCARD,"");
            if (!"".equals(token)) count++;
        }
        return count;
    }

    @Override
    public String toString() {
        return urlData.getUrlPattern();
    }
}
