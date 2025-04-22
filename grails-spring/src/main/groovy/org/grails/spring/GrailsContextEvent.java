/*
 * Copyright 2004-2005 the original author or authors.
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
package org.grails.spring;

import org.springframework.context.ApplicationEvent;
import org.springframework.web.context.WebApplicationContext;

/**
 * Signals various events related to the Grails context loading.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class GrailsContextEvent extends ApplicationEvent {

    private static final long serialVersionUID = -2686144042443671643L;

    public static final int DYNAMIC_METHODS_REGISTERED = 0;

    private int eventType;

    public GrailsContextEvent(WebApplicationContext ctx, int eventType) {
        super(ctx);
        this.eventType = eventType;
    }

    public int getEventType() {
        return eventType;
    }
}
