package org.grails.gradle.plugin.exploded

import org.gradle.api.attributes.AttributeCompatibilityRule
import org.gradle.api.attributes.CompatibilityCheckDetails

/**
 * Compatible if:
 * 1. attribute not defined
 * 2. attribute matches desired value
 */
class ExplodedCompatibilityRule implements AttributeCompatibilityRule<Boolean> {
    @Override
    void execute(CompatibilityCheckDetails<Boolean> details) {
        if (details.getConsumerValue() == null) {
            details.compatible()
        } else if (details.getProducerValue() == details.getConsumerValue()) {
            details.compatible()
        }
    }
}
