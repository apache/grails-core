/*
 * Copyright 2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package grails.rest.render.errors

import grails.rest.render.ContainerRenderer
import grails.util.Environment
import grails.util.GrailsMessageSourceUtils
import grails.util.GrailsNameUtils
import grails.util.GrailsWebUtil
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import grails.web.mapping.LinkGenerator
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.context.MessageSource
import org.springframework.validation.Errors
import org.springframework.validation.ObjectError

/**
 * Abstract super type for Vnd.Error renderers
 *
 * @author Graeme Rocher
 * @since 2.3
 */
@CompileStatic
abstract class AbstractVndErrorRenderer  implements ContainerRenderer<Errors, Object> {
    public static final String LOGREF_ATTRIBUTE = 'logref'
    public static final String MESSAGE_ATTRIBUTE = "message"
    public static final String PATH_ATTRIBUTE = "path"
    public static final String RESOURCE_ATTRIBUTE = "resource"
    public static final String HREF_ATTRIBUTE = "href"


    String encoding = GrailsWebUtil.DEFAULT_ENCODING
    boolean absoluteLinks = true
    boolean prettyPrint = Environment.isDevelopmentMode()

    MessageSource messageSource

    @Autowired
    setMessageSource(List<MessageSource> messageSources) {
        setMessageSource(GrailsMessageSourceUtils.findPreferredMessageSource(messageSources))
    }

    void setMessageSource(MessageSource messageSource) {
        this.messageSource = messageSource
    }

    @Autowired
    LinkGenerator linkGenerator


    @Override
    Class<Errors> getTargetType() {
        Errors
    }

    /**
     * Resolve the 'logref' attribute for the error
     * @param target The target object that caused the error
     * @param oe The ObjectError instance
     * @return The log reference
     */
    protected String resolveLogRef(target, ObjectError oe) {
        final objectId = getObjectId(target)
        final name = GrailsNameUtils.getPropertyName(target.class)
        final code = oe.code
        def logref = "${name}.${code}${objectId ? '.' + objectId: ''}".toString()
        logref
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    protected Object getObjectId(target) {
        target.id
    }


    @Override
    Class<Object> getComponentType() {
        Object
    }
}
