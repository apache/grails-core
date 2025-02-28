package org.grails.events.bus

import grails.events.Event
import grails.events.subscriber.Subscription
import grails.events.trigger.EventTrigger
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.springframework.core.task.SyncTaskExecutor

import java.util.concurrent.Callable
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService

/**
 * An event bus that uses an {@link Executor}
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
class ExecutorEventBus extends AbstractEventBus {

    final Executor executor

    ExecutorEventBus(Executor executor = new SyncTaskExecutor()) {
        this.executor = executor
    }

    @Override
    protected Callable buildNotificationCallable(Event event, Collection<Subscription> eventSubscriptions, Closure reply) {
        Executor executor = this.executor
        if(executor instanceof ExecutorService) {
            ExecutorService executorService = (ExecutorService)this.executor
            return {
                executorService.submit {
                    for (Subscription subscription in eventSubscriptions) {
                        EventTrigger trigger = subscription.buildTrigger(event, reply)
                        trigger.proceed()
                    }
                }
            }
        }
        else {
            return {
                executor.execute {
                    for (Subscription subscription in eventSubscriptions) {
                        EventTrigger trigger = subscription.buildTrigger(event, reply)
                        trigger.proceed()
                    }
                }
            }
        }
    }
}
