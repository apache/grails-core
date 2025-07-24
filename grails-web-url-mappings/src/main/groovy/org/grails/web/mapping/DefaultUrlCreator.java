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

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.springframework.web.context.request.RequestContextHolder;

import grails.core.GrailsControllerClass;
import grails.util.GrailsStringUtils;
import grails.util.GrailsWebUtil;
import grails.web.mapping.UrlCreator;
import org.grails.buffer.FastStringWriter;
import org.grails.web.servlet.mvc.DefaultRequestStateLookupStrategy;
import org.grails.web.servlet.mvc.GrailsRequestStateLookupStrategy;
import org.grails.web.servlet.mvc.GrailsWebRequest;
import org.grails.web.servlet.mvc.exceptions.ControllerExecutionException;

/**
 * The default implementation of the UrlCreator interface that constructs URLs in Grails
 * default pattern of /controllerName/actionName/id.
 *
 * @author Graeme Rocher
 * @since 0.5.5
 */
@SuppressWarnings("rawtypes")
public class DefaultUrlCreator implements UrlCreator {

    private static final char SLASH = '/';
    private final String controllerName;
    private final String actionName;
    public static final String ARGUMENT_ID = "id";
    private static final String ENTITY_AMPERSAND = "&";

    public DefaultUrlCreator(String controller, String action) {
        controllerName = controller;
        actionName = action;
    }

    public String createURL(Map parameterValues, String encoding) {
        if (parameterValues == null) parameterValues = Collections.emptyMap();
        GrailsWebRequest webRequest = (GrailsWebRequest) RequestContextHolder.getRequestAttributes();
        return createURLWithWebRequest(parameterValues, webRequest, true);
    }

    public String createURL(Map parameterValues, String encoding, String fragment) {
        String url = createURL(parameterValues, encoding);
        return createUrlWithFragment(encoding, fragment, url);
    }

    @SuppressWarnings("unchecked")
    private String createURLWithWebRequest(Map parameterValues, GrailsWebRequest webRequest, boolean includeContextPath) {

        GrailsRequestStateLookupStrategy requestStateLookupStrategy = new DefaultRequestStateLookupStrategy(webRequest);

        final String encoding = requestStateLookupStrategy.getCharacterEncoding();

        String id = null;
        if (parameterValues.containsKey(ARGUMENT_ID)) {
            Object o = parameterValues.get(ARGUMENT_ID);
            if (o != null) {
                id = o.toString();
            }
        }

        FastStringWriter actualUriBuf = new FastStringWriter();
        if (includeContextPath) {
            actualUriBuf.append(requestStateLookupStrategy.getContextPath());
        }
        if (actionName != null) {
            if (actionName.indexOf(SLASH) > -1) {
                actualUriBuf.append(actionName);
            }
            else {
                if (controllerName != null) {
                    appendUrlToken(actualUriBuf, controllerName, encoding);
                }
                else {
                    appendUrlToken(actualUriBuf, requestStateLookupStrategy.getControllerName(), encoding);
                }
            }
            appendUrlToken(actualUriBuf, actionName, encoding);
        }
        if (id != null) {
            appendUrlToken(actualUriBuf, id, encoding);
        }
        appendRequestParams(actualUriBuf, parameterValues, encoding);
        return actualUriBuf.toString();
    }

    public String createURL(String controller, String action, Map parameterValues, String encoding) {
        return createURL(controller, action, null, null, parameterValues, encoding);
    }

    public String createURL(String controller, String action, String pluginName, Map parameterValues, String encoding) {
        return createURL(controller, action, null, pluginName, parameterValues, encoding);
    }

    @SuppressWarnings("unchecked")
    public String createURL(String controller, String action, String namespace, String pluginName, Map parameterValues, String encoding) {
        return createURLInternal(controller, action, parameterValues, true);
    }

    private String createURLInternal(String controller, String action, Map<String, String> parameterValues, boolean includeContextPath) {
        GrailsWebRequest webRequest = (GrailsWebRequest) RequestContextHolder.getRequestAttributes();

        if (parameterValues == null) parameterValues = new HashMap<String, String>();
        boolean blankController = GrailsStringUtils.isBlank(controller);
        boolean blankAction = GrailsStringUtils.isBlank(action);

        if (!blankController) {
            parameterValues.put(GrailsControllerClass.CONTROLLER, controller);
        }
        if (!blankAction) {
            parameterValues.put(GrailsControllerClass.ACTION, action);
        }

        try {
            return createURLWithWebRequest(parameterValues, webRequest, includeContextPath);
        }
        finally {
            if (!blankController) {
                parameterValues.remove(GrailsControllerClass.CONTROLLER);
            }
            if (!blankAction) {
                parameterValues.remove(GrailsControllerClass.ACTION);
            }
        }
    }

    public String createRelativeURL(String controller, String action, Map parameterValues, String encoding) {
        return createRelativeURL(controller, action, null, null, parameterValues, encoding);
    }

    public String createRelativeURL(String controller, String action, String pluginName, Map parameterValues, String encoding) {
        return createRelativeURL(controller, action, null, null, parameterValues, encoding);
    }

    @SuppressWarnings("unchecked")
    public String createRelativeURL(String controller, String action, String namespace, String pluginName, Map parameterValues, String encoding) {
        return createURLInternal(controller, action, parameterValues, false);
    }

    public String createRelativeURL(String controller, String action, Map parameterValues, String encoding, String fragment) {
        return createRelativeURL(controller, action, null, null, parameterValues, encoding, fragment);
    }

    @SuppressWarnings("unchecked")
    public String createRelativeURL(String controller, String action, String namespace, String pluginName, Map parameterValues, String encoding, String fragment) {
        final String url = createURLInternal(controller, action, parameterValues, false);
        return  createUrlWithFragment(encoding, fragment, url);
    }

    public String createURL(String controller, String action, Map parameterValues, String encoding, String fragment) {
        return createURL(controller, action, null, null, parameterValues, encoding, fragment);
    }

    public String createURL(String controller, String action, String namespace, String pluginName, Map parameterValues, String encoding, String fragment) {
        String url = createURL(controller, action, namespace, pluginName, parameterValues, encoding);
        return createUrlWithFragment(encoding, fragment, url);
    }

    private String createUrlWithFragment(String encoding, String fragment, String url) {
        if (fragment != null) {
            try {
                return url + '#' + URLEncoder.encode(fragment, encoding);
            }
            catch (UnsupportedEncodingException ex) {
                throw new ControllerExecutionException("Error creating URL  [" + url +
                     "], problem encoding URL fragment [" + fragment + "]: " + ex.getMessage(),ex);
            }
        }

        return url;
    }

    /*
     * Appends all the request parameters to the URI buffer
     */
    private void appendRequestParams(FastStringWriter actualUriBuf, Map<Object, Object> params, String encoding) {

        boolean querySeparator = false;

        for (Map.Entry<Object, Object> entry : params.entrySet()) {
            Object name = entry.getKey();
            if (name.equals(GrailsControllerClass.CONTROLLER) || name.equals(GrailsControllerClass.ACTION) || name.equals(ARGUMENT_ID)) {
                continue;
            }

            if (!querySeparator) {
                actualUriBuf.append('?');
                querySeparator = true;
            }
            else {
                actualUriBuf.append(ENTITY_AMPERSAND);
            }
            Object value = entry.getValue();
            if (value instanceof Collection) {
                Collection values = (Collection) value;
                Iterator valueIterator = values.iterator();
                while (valueIterator.hasNext()) {
                    Object currentValue = valueIterator.next();
                    appendRequestParam(actualUriBuf, name, currentValue, encoding);
                    if (valueIterator.hasNext()) {
                        actualUriBuf.append(ENTITY_AMPERSAND);
                    }
                }
            }
            else if (value != null && value.getClass().isArray()) {
                Object[] array = (Object[]) value;
                for (int j = 0; j < array.length; j++) {
                    Object currentValue = array[j];
                    appendRequestParam(actualUriBuf, name, currentValue, encoding);
                    if (j < (array.length-1)) {
                        actualUriBuf.append(ENTITY_AMPERSAND);
                    }
                }
            }
            else {
                appendRequestParam(actualUriBuf, name, value, encoding);
            }
        }
    }

    /*
     * Appends a request parameters for the given aname and value
     */
    private void appendRequestParam(FastStringWriter actualUriBuf, Object name,
            Object value, String encoding) {

        if (value == null) {
            value = "";
        }

        actualUriBuf.append(urlEncode(name, encoding))
                    .append('=')
                    .append(urlEncode(value, encoding));
    }

    private String urlEncode(Object obj, String charset) {
        try {
            return URLEncoder.encode(obj.toString(), (charset != null) ? charset : GrailsWebUtil.DEFAULT_ENCODING);
        }
        catch (UnsupportedEncodingException ex) {
            throw new ControllerExecutionException(
                    "Error creating URL, cannot URLEncode to the client's character encoding: " + ex.getMessage(), ex);
        }
    }

    /*
     * Appends a URL token to the buffer
     */
    private void appendUrlToken(FastStringWriter actualUriBuf, Object token, String charset) {
        actualUriBuf.append(SLASH).append(urlEncode(token, charset));
    }
}
