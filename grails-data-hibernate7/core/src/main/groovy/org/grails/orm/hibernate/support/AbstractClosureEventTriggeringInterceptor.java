package org.grails.orm.hibernate.support;

import org.hibernate.event.spi.*;
import org.hibernate.jpa.event.spi.CallbackRegistryConsumer;

import org.springframework.context.ApplicationContextAware;

/**
 * Abstract class for defining the event triggering interceptor
 *
 * @author Graeme Rocher
 * @since 6.0
 */
public abstract class AbstractClosureEventTriggeringInterceptor
        implements ApplicationContextAware,
        PreLoadEventListener,
        PostLoadEventListener,
        PostInsertEventListener,
        PostUpdateEventListener,
        PostDeleteEventListener,
        PreDeleteEventListener,
        PreUpdateEventListener,
        PreInsertEventListener,
        MergeEventListener,
        PersistEventListener,
        CallbackRegistryConsumer {
}
