package org.grails.events.bus

import grails.events.Event
import grails.events.bus.EventBus
import grails.events.emitter.EventEmitter
import grails.events.subscriber.Subjects
import grails.events.subscriber.Subscriber
import grails.events.subscriber.Subscription
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.grails.events.registry.ClosureSubscription
import org.grails.events.registry.EventSubscriberSubscription
import org.springframework.transaction.event.TransactionPhase
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Abstract event bus impl
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
abstract class AbstractEventBus implements EventBus {

    protected final Map<CharSequence, Collection<Subscription>> subscriptions = new ConcurrentHashMap<CharSequence, Collection<Subscription>>().withDefault {
        new ConcurrentLinkedQueue<ClosureSubscription>() as Collection<Subscription>
    }

    @Override
    boolean isActive() {
        true
    }

    @Override
    final EventEmitter notify(CharSequence eventId, Object... data) {
        notify(new Event(eventId.toString(), data.length == 1 ? data[0] : data))
    }

    @Override
    final EventEmitter publish(CharSequence eventId, Object... data) {
        notify(eventId, data)
    }

    @Override
    final EventEmitter publish(Event event) {
        notify(event)
    }

    @Override
    final EventEmitter sendAndReceive(CharSequence eventId, Object data, Closure reply) {
        sendAndReceive(new Event(eventId.toString(), data), reply)
    }

    @Override
    final Subscription subscribe(CharSequence event, Closure subscriber) {
        on(event, subscriber)
    }

    @Override
    EventEmitter publish(Event event, TransactionPhase transactionPhase) {
        notify(event, transactionPhase)
    }

    @Override
    Subscription on(CharSequence event, Closure subscriber) {
        buildClosureSubscription(event, subscriber)
    }

    @Override
    Subscription subscribe(CharSequence event, Subscriber subscriber) {
        buildSubscriberSubscription(event, subscriber)
    }

    @Override
    Subjects unsubscribeAll(CharSequence event) {
        def subs = subscriptions.get(event.toString())
        for(sub in subs) {
            if(!sub.isCancelled()) {
                sub.cancel()
            }
        }
        subs.clear()
        this
    }

    EventEmitter notify(Event event) {
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            notify(event, TransactionPhase.AFTER_COMMIT)
        }
        else {
            String eventId = event.id
            if(subscriptions.containsKey(eventId))  {
                Collection<Subscription> eventSubscriptions = subscriptions.get(event.id)
                if(!eventSubscriptions.isEmpty()) {
                    buildNotificationTrigger(event, eventSubscriptions)
                            .run()
                }
            }
        }
        this
    }

    @Override
    EventEmitter sendAndReceive(Event event, Closure reply) {
        if(event == null) throw new IllegalArgumentException("Argument [event] cannot be null")
        if(event == null) throw new IllegalArgumentException("Argument [reply] cannot be null")
        String eventId = event.id
        if(subscriptions.containsKey(eventId))  {
            Collection<Subscription> eventSubscriptions = subscriptions.get(eventId)
            if(!eventSubscriptions.isEmpty()) {
                buildNotificationTrigger(event, eventSubscriptions, reply)
                    .run()
            }
        }
        this
    }

    @Override
    EventEmitter notify(Event event, TransactionPhase transactionPhase) {
        if(TransactionSynchronizationManager.isSynchronizationActive()) {
            String eventId = event.getId()
            Collection<Subscription> eventSubscriptions = subscriptions.get(eventId)
            if(!eventSubscriptions.isEmpty()) {
                TransactionSynchronizationManager.registerSynchronization(
                    new EventTriggerTransactionSynchronization(buildNotificationTrigger(event, eventSubscriptions), transactionPhase)
                )
            }
            return this
        }
        else {
            return notify(event)
        }
    }

    /**
     * Build a new trigger to set off notifications
     *
     * @param event The event
     * @param eventSubscriptions The subscriptions
     * @param reply
     * @return
     */
    protected final NotificationTrigger buildNotificationTrigger(Event event, Collection<Subscription> eventSubscriptions, Closure reply = null) {

        final Callable callable = buildNotificationCallable(event, eventSubscriptions, reply)

        new NotificationTrigger(event, eventSubscriptions, reply) {
            @Override
            void run() {
                callable.call()
            }
        }
    }

    /**
     * Build a new trigger to set off notifications
     *
     * @param event The event
     * @param eventSubscriptions The subscriptions
     * @param reply
     * @return
     */
    protected abstract Callable buildNotificationCallable(Event event, Collection<Subscription> eventSubscriptions, Closure reply = null)

    protected static abstract class NotificationTrigger implements Runnable {

        final Event event
        final Collection<Subscription> subscriptions
        final Closure reply

        NotificationTrigger(Event event, Collection<Subscription> subscriptions, Closure reply = null) {
            this.event = event
            this.subscriptions = subscriptions
            this.reply = reply
        }
    }

    protected static class EventTriggerTransactionSynchronization implements TransactionSynchronization {

        final NotificationTrigger notificationTrigger
        final TransactionPhase transactionPhase

        EventTriggerTransactionSynchronization(NotificationTrigger notificationTrigger, TransactionPhase transactionPhase) {
            this.notificationTrigger = notificationTrigger
            this.transactionPhase = transactionPhase
        }

        @Override
        void beforeCommit(boolean readOnly) {
            if(transactionPhase == TransactionPhase.BEFORE_COMMIT && !readOnly) {
                notificationTrigger.run()
            }
        }

        @Override
        void afterCommit() {
            if(transactionPhase == TransactionPhase.AFTER_COMMIT) {
                notificationTrigger.run()
            }
        }

        @Override
        void afterCompletion(int status) {
            if(transactionPhase == TransactionPhase.AFTER_COMPLETION && status == STATUS_COMMITTED) {
                notificationTrigger.run()
            }
            else if(transactionPhase == TransactionPhase.AFTER_ROLLBACK && status == STATUS_ROLLED_BACK) {
                notificationTrigger.run()
            }
        }
    }


    protected EventSubscriberSubscription buildSubscriberSubscription(CharSequence eventId, Subscriber subscriber) {
        new EventSubscriberSubscription(eventId, subscriptions, subscriber)
    }

    protected ClosureSubscription buildClosureSubscription(CharSequence eventId, Closure subscriber) {
        new ClosureSubscription(eventId, subscriptions, subscriber)
    }
}
