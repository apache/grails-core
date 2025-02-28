package org.grails.events.rxjava

import grails.events.Event
import grails.events.subscriber.Subscriber
import grails.events.trigger.EventTrigger
import grails.events.subscriber.Subscription
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import org.grails.events.bus.AbstractEventBus
import org.grails.events.registry.ClosureSubscription
import org.grails.events.registry.EventSubscriberSubscription
import rx.Scheduler
import rx.functions.Action1
import rx.schedulers.Schedulers
import rx.subjects.PublishSubject
import rx.subjects.Subject

import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap

/**
 * An EventBus implementation that uses RxJava
 *
 * @author Graeme Rocher
 * @since 3.3
 *
 */
@Slf4j
@AutoFinal
@CompileStatic
class RxEventBus extends AbstractEventBus {

    protected final Map<CharSequence, PublishSubject> subjects = new ConcurrentHashMap<CharSequence, PublishSubject>().withDefault {
        PublishSubject.create()
    }

    final Scheduler scheduler

    RxEventBus(Scheduler scheduler = Schedulers.io()) {
        this.scheduler = scheduler
    }

    @Override
    protected EventSubscriberSubscription buildSubscriberSubscription(CharSequence eventId, Subscriber subscriber) {

        String eventKey = eventId.toString()
        Subject subject = subjects.get(eventKey)

        return new RxEventSubscriberSubscription(eventId, subscriptions, subscriber, subject, scheduler)
    }

    @Override
    protected ClosureSubscription buildClosureSubscription(CharSequence eventId, Closure subscriber) {

        String eventKey = eventId.toString()
        Subject subject = subjects.get(eventKey)

        return new RxClosureSubscription(eventId, subscriptions, subscriber, subject, scheduler)
    }

    @Override
    protected Callable buildNotificationCallable(Event event, Collection<Subscription> eventSubscriptions, Closure reply) {
        return {
            PublishSubject sub = subjects.get(event.id)
            if(sub.hasObservers() && !sub.hasCompleted()) {
                if(reply != null) {
                    sub.onNext(new EventWithReply(event, reply))
                }
                else {
                    sub.onNext(event)
                }
            }
        }
    }

    private static class RxClosureSubscription extends ClosureSubscription {

        final rx.Subscription subscription

        RxClosureSubscription(CharSequence eventId, Map<CharSequence, Collection<Subscription>> subscriptions, Closure subscriber, Subject subject, Scheduler scheduler) {
            super(eventId, subscriptions, subscriber)
            this.subscription = subject.observeOn(scheduler).subscribe( { eventObject ->
                Event event
                Closure reply = null
                if(eventObject  instanceof EventWithReply) {
                    def eventWithReply = (EventWithReply) eventObject
                    event = eventWithReply.event
                    reply = eventWithReply.reply
                }
                else {
                    event = (Event) eventObject
                }

                EventTrigger trigger = buildTrigger(event as Event, reply)
                trigger.proceed()
            }  as Action1, { Throwable t ->
                log.error("Error occurred triggering event listener for event [$eventId]: ${t.message}", t)
            } as Action1<Throwable>)
        }

        @Override
        Subscription cancel() {
            if(!subscription.unsubscribed) {
                subscription.unsubscribe()
            }
            super.cancel()
        }

        @Override
        boolean isCancelled() {
            subscription.unsubscribed
        }
    }

    private static class RxEventSubscriberSubscription extends EventSubscriberSubscription {

        final rx.Subscription subscription

        RxEventSubscriberSubscription(CharSequence eventId, Map<CharSequence, Collection<Subscription>> subscriptions, Subscriber subscriber, Subject subject, Scheduler scheduler) {
            super(eventId, subscriptions, subscriber)
            this.subscription = subject.observeOn(scheduler).subscribe({ event ->
                EventTrigger trigger = buildTrigger(event as Event)
                trigger.proceed()
            } as Action1, { Throwable t ->
                log.error("Error occurred triggering event listener for event [$eventId]: ${t.message}", t)
            } as Action1<Throwable>)
        }

        @Override
        Subscription cancel() {
            if(!subscription.unsubscribed) {
                subscription.unsubscribe()
            }
            super.cancel()
        }

        @Override
        boolean isCancelled() {
            subscription.unsubscribed
        }
    }
}
