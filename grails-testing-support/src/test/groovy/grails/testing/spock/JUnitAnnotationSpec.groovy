package grails.testing.spock

import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Stepwise

@Stepwise
class JUnitAnnotationSpec extends Specification {
    @Shared
    static List<String> methodOrder = []

    void setupSpec() {
        methodOrder << "setupSpec"
    }

    void setup() {
        methodOrder << "setup"
    }

    void cleanup() {
        methodOrder << "cleanup"
    }

    void cleanupSpec() {
        methodOrder << "cleanupSpec"
    }

    @BeforeEach
    void beforeEach() {
        methodOrder << "beforeEach"
    }

    @AfterEach
    void afterEach() {
        methodOrder << "afterEach"
    }

    @BeforeAll
    static void beforeAll() {
        methodOrder << "beforeAll"
    }

    @AfterAll
    static void afterAll() {
        methodOrder << "afterAll"
        assert methodOrder == ["beforeAll", "setupSpec", "beforeEach", "setup", "cleanup", "afterEach", "cleanupSpec", "afterAll"]
    }

    void 'junit 5 annotated methods are called in correct order prior to this test'() {
        expect:
        methodOrder == ["beforeAll", "setupSpec", "beforeEach", "setup"]
    }
}
