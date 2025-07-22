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
package grails.util

import groovy.transform.CompileStatic
import org.springframework.context.MessageSource


/**
 * A simple class that selects a single {@link  org.springframework.context.MessageSource  MessageSource}
 * when two or more are present in the ApplicationContext.
 * It defaults to the Grails or Spring {@link  org.springframework.context.MessageSource  MessageSource}, if present.
 *
 * @author James Fredley
 * @since 7.0
 */

@CompileStatic
class GrailsMessageSourceUtils {
    static MessageSource findPreferredMessageSource(List<MessageSource> messageSources){
        if(!messageSources) {
            return null
        }

        if(messageSources.size() == 1) {
            return messageSources.get(0)
        }

        MessageSource firstGrailsSpring = messageSources.find {messageSource ->
            String className = messageSource.class.name
            // use the first Grails or Spring MessageSource
            className.startsWith("org.grails") || className.startsWith("grails") || className.startsWith("org.springframework")
        }

        // return the first Grails or Spring MessageSource
        if(firstGrailsSpring) {
            return firstGrailsSpring
        }

        // return the first MessageSource from the list
        return messageSources.get(0)
    }
}
