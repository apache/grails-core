package grails.testing.spock

import groovy.transform.AnnotationCollector
import org.junit.jupiter.api.BeforeEach

/**
 * This annotation may be applied to fixture methods in a Spock Spec that should
 * be run once and only once before any test methods are run.  Methods marked
 * with this interface will automatically be marked with @org.junit.Before and
 * {@link grails.testing.spock.RunOnce}.
 *
 * @see org.junit.jupiter.api.BeforeEach
 * @see grails.testing.spock.RunOnce
 * @deprecated Use Spock native setup/cleanup/…​ fixture methods instead.
 */
@Deprecated
@AnnotationCollector([BeforeEach, RunOnce])
@interface OnceBefore {
}
