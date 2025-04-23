package org.grails.cli.profile.steps

import org.grails.cli.profile.commands.ClosureExecutingCommand
import spock.lang.Specification

/**
 * @author graemerocher
 */
class StepRegistrySpec extends Specification {

    void "Test the step registry finds registered steps"() {
        expect:"The step registry to find steps"
            StepRegistry.getStep("render", new ClosureExecutingCommand("test", {}), [foo:true]) instanceof RenderStep
    }
}
