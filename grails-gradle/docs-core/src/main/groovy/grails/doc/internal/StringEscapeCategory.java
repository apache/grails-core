package grails.doc.internal;

import java.net.URI;
import java.net.URISyntaxException;

import org.apache.commons.text.StringEscapeUtils;

public class StringEscapeCategory {
    private StringEscapeCategory() {
    }

    public static String encodeAsUrlPath(String str) {
        try {
            String uri = new URI("http", "localhost", '/' + str, "").toASCIIString();
            return uri.substring(17, uri.length() - 1);
        }
        catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String encodeAsUrlFragment(String str) {
        try {
            String uri = new URI("http", "localhost", "/", str).toASCIIString();
            return uri.substring(18, uri.length());
        }
        catch (URISyntaxException ex) {
            throw new RuntimeException(ex);
        }
    }

    public static String encodeAsHtml(String str) {
        return StringEscapeUtils.escapeHtml4(str);
    }
}
