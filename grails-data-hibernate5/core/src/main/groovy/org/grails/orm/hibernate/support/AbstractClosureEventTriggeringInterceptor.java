package org.grails.orm.hibernate.support;

import org.hibernate.event.internal.DefaultSaveOrUpdateEventListener;
import org.hibernate.event.spi.*;
import org.springframework.context.ApplicationContextAware;

/**
 * Abstract class for defining the event triggering interceptor
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public abstract class AbstractClosureEventTriggeringInterceptor extends DefaultSaveOrUpdateEventListener
        implements ApplicationContextAware,
        PreLoadEventListener,
        PostLoadEventListener,
        PostInsertEventListener,
        PostUpdateEventListener,
        PostDeleteEventListener,
        PreDeleteEventListener,
        PreUpdateEventListener,
        PreInsertEventListener {
}
