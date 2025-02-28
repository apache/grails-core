package grails.events.bus

import groovy.transform.AutoFinal
import groovy.transform.CompileStatic
import org.springframework.beans.factory.annotation.Autowired

/**
 * Trait for classes aware of the event bus
 *
 * @author Graeme Rocher
 * @since 3.3
 */
@AutoFinal
@CompileStatic
trait EventBusAware {

    private EventBus eventBus

    /**
     * Sets the target event bus to use
     *
     * @param eventBus The event bus
     */
    @Autowired
    void setTargetEventBus(EventBus eventBus) {
        this.eventBus = eventBus
    }

    /**
     * @return Retrieves the event bus
     */
    EventBus getEventBus() {
        if(this.eventBus == null) {
            this.eventBus = new EventBusBuilder().build()
        }
        return this.eventBus
    }
}