package org.grails.events.rxjava2

import grails.events.Event
import grails.events.subscriber.Subscriber
import grails.events.trigger.EventTrigger
import grails.events.subscriber.Subscription
import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import groovy.util.logging.Slf4j
import io.reactivex.Scheduler
import io.reactivex.disposables.Disposable
import io.reactivex.functions.Consumer
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import io.reactivex.subjects.Subject
import org.grails.events.bus.AbstractEventBus
import org.grails.events.registry.ClosureSubscription
import org.grails.events.registry.EventSubscriberSubscription

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
    protected Callable buildNotificationCallable(Event event, Collection<Subscription> eventSubscriptions, Closure reply) {
        return {
            PublishSubject sub = subjects.get(event.id)
            if(sub.hasObservers() && !sub.hasComplete()) {
                if(reply != null) {
                    sub.onNext(new EventWithReply(event, reply))
                }
                else {
                    sub.onNext(event)
                }
            }
        }
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

    private static class RxClosureSubscription extends ClosureSubscription {

        final Disposable subscription

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
            }  as Consumer, { Throwable t ->
                log.error("Error occurred triggering event listener for event [$eventId]: ${t.message}", t)
            } as Consumer<Throwable>)
        }

        @Override
        Subscription cancel() {
            if(!subscription.disposed) {
                subscription.dispose()
            }
            return super.cancel()
        }

        @Override
        boolean isCancelled() {
            return subscription.disposed
        }
    }

    private static class RxEventSubscriberSubscription extends EventSubscriberSubscription {

        final Disposable subscription

        RxEventSubscriberSubscription(CharSequence eventId, Map<CharSequence, Collection<Subscription>> subscriptions, Subscriber subscriber, Subject subject, Scheduler scheduler) {
            super(eventId, subscriptions, subscriber)
            this.subscription = subject.observeOn(scheduler).subscribe( { event ->
                EventTrigger trigger = buildTrigger(event as Event)
                trigger.proceed()
            }  as Consumer, { Throwable t ->
                log.error("Error occurred triggering event listener for event [$eventId]: ${t.message}", t)
            } as Consumer<Throwable>)
        }

        @Override
        Subscription cancel() {
            if(!subscription.disposed) {
                subscription.dispose()
            }
            return super.cancel()
        }

        @Override
        boolean isCancelled() {
            return subscription.disposed
        }
    }
}
