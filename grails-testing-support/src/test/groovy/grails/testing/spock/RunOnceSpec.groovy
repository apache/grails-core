package grails.testing.spock

import org.junit.jupiter.api.BeforeEach
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
class RunOnceSpec extends Specification {

    @Shared
    int setupSpecCounter = 0

    @Shared
    int setupCounter = 0

    @Shared
    int onceBeforeCounter = 0

    @Shared
    int anotherOnceBeforeCounter = 0

    void setupSpec() {
        setupSpecCounter++
    }

    void setup() {
        setupCounter++
    }

    @BeforeEach
    @RunOnce
    void someOnceBeforeMethod() {
        onceBeforeCounter++
    }

    @BeforeEach
    @RunOnce
    void someOtherOnceBeforeMethod() {
        anotherOnceBeforeCounter++
    }

    void 'first test'() {
        expect:
        setupSpecCounter == 1
        setupCounter == 1
        onceBeforeCounter == 1
        anotherOnceBeforeCounter == 1
    }

    void 'second test'() {
        expect:
        setupSpecCounter == 1
        setupCounter == 2
        onceBeforeCounter == 1
        anotherOnceBeforeCounter == 1
    }

    void 'third test'() {
        expect:
        setupSpecCounter == 1
        setupCounter == 3
        onceBeforeCounter == 1
        anotherOnceBeforeCounter == 1
    }
}
