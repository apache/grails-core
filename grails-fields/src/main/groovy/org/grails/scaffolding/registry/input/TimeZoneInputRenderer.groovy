package org.grails.scaffolding.registry.input

import org.grails.scaffolding.model.property.DomainProperty
import org.grails.scaffolding.registry.DomainInputRenderer
import groovy.transform.CompileStatic

/**
 * The default renderer for rendering {@link TimeZone} properties
 *
 * @author James Kleeh
 */
@CompileStatic
class TimeZoneInputRenderer implements MapToSelectInputRenderer<TimeZone> {

    String getOptionValue(TimeZone timeZone) {
        Date date = new Date()
        String shortName = timeZone.getDisplayName(timeZone.inDaylightTime(date), TimeZone.SHORT)
        String longName = timeZone.getDisplayName(timeZone.inDaylightTime(date), TimeZone.LONG)

        int offset = timeZone.rawOffset
        BigDecimal hour = offset / (60 * 60 * 1000)
        BigDecimal minute = offset / (60 * 1000)
        double min = Math.abs(minute.toDouble()) % 60

        "${shortName}, ${longName} ${hour}:${min} [${timeZone.ID}]"
    }

    String getOptionKey(TimeZone timeZone) {
        timeZone.ID
    }

    Map<String, String> getOptions() {
        TimeZone.availableIDs.collectEntries {
            TimeZone timeZone = TimeZone.getTimeZone(it)
            [(getOptionKey(timeZone)): getOptionValue(timeZone)]
        }
    }

    TimeZone getDefaultOption() {
        TimeZone.default
    }

    @Override
    boolean supports(DomainProperty property) {
        property.type in TimeZone
    }

}
