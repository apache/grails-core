/*
 * Copyright 2014 the original author or authors.
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
package org.grails.encoder;

import grails.core.GrailsApplication;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;

public class CodecLookupHelper {
    private static final Logger log = LoggerFactory.getLogger(CodecLookupHelper.class);

    private CodecLookupHelper() {
    }

    /**
     * Lookup encoder.
     *
     * @param grailsApplication the grailsApplication instance
     * @param codecName the codec name
     * @return the encoder instance
     */
    public static Encoder lookupEncoder(GrailsApplication grailsApplication, String codecName) {
        ApplicationContext ctx = grailsApplication != null ? grailsApplication.getMainContext() : null;
        if(ctx != null) {
            try {
                CodecLookup codecLookup = ctx.getBean("codecLookup", CodecLookup.class);
                return codecLookup.lookupEncoder(codecName);
            } catch (NoSuchBeanDefinitionException e) {
                // ignore missing codecLookup bean in tests
                log.debug("codecLookup bean is missing from test context.", e);
            }
        }
        return null;
    }
}
