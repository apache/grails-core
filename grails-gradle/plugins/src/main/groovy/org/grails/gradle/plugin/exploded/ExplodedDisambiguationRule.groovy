package org.grails.gradle.plugin.exploded

import org.gradle.api.attributes.AttributeDisambiguationRule
import org.gradle.api.attributes.MultipleCandidatesDetails

/**
 * If true, then it's the closest match, otherwise not the closest. Prioritizes the exploded variant
 */
class ExplodedDisambiguationRule implements AttributeDisambiguationRule<Boolean> {
    @Override
    void execute(MultipleCandidatesDetails<Boolean> details) {
        if (details.candidateValues.contains(Boolean.TRUE)) {
            details.closestMatch(Boolean.TRUE)
        } else {
            details.closestMatch(Boolean.FALSE)
        }
    }
}
