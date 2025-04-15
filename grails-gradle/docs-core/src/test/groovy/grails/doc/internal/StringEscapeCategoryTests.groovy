package grails.doc.internal

import org.junit.jupiter.api.Test

class StringEscapeCategoryTests {

    @Test
    void testEncodeAsUrl() {
        assert StringEscapeCategory.encodeAsUrlPath("test") == "test"
        assert StringEscapeCategory.encodeAsUrlPath("test space") == "test%20space"
        assert StringEscapeCategory.encodeAsUrlPath("multi-byte⁄ space") == "multi-byte%E2%81%84%20space"
        assert StringEscapeCategory.encodeAsUrlPath("test&amp;") == "test&amp;"
        assert StringEscapeCategory.encodeAsUrlPath("<test%20hey>") == "%3Ctest%2520hey%3E"
    }

    @Test
    void testEncodeAsUrlFragment() {
        assert StringEscapeCategory.encodeAsUrlFragment("test") == "test"
        assert StringEscapeCategory.encodeAsUrlFragment("test space") == "test%20space"
        assert StringEscapeCategory.encodeAsUrlFragment("multi-byte⁄ space") == "multi-byte%E2%81%84%20space"
        assert StringEscapeCategory.encodeAsUrlFragment("test&amp;") == "test&amp;"
        assert StringEscapeCategory.encodeAsUrlFragment("<test%20hey>") == "%3Ctest%2520hey%3E"
    }

    @Test
    void testEncodeAsHtml() {
        assert StringEscapeCategory.encodeAsHtml("test") == "test"
        assert StringEscapeCategory.encodeAsHtml("test space") == "test space"
        assert StringEscapeCategory.encodeAsHtml("multi-byte⁄ space") == "multi-byte&frasl; space"
        assert StringEscapeCategory.encodeAsHtml("test&amp;") == "test&amp;amp;"
        assert StringEscapeCategory.encodeAsHtml("<test%20hey>") == "&lt;test%20hey&gt;"
    }
}
