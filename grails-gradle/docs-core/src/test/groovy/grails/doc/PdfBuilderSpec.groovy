package grails.doc

import spock.lang.Specification

class PdfBuilderSpec extends Specification {

    void "remove CssLinks"() {
        given:
        String html = """
<head>
        <title>The Grails Framework 3.2.11</title>
        <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
        <link rel="stylesheet" href="../css/main.css" type="text/css" media="screen, print" title="Style" charset="utf-8" />
        <link rel="stylesheet" href="../css/pdf.css" type="text/css" media="print" title="PDF" charset="utf-8" />
    <script type="text/javascript">
function addJsClass(el) {
    var classes = document.body.className.split(" ");
    classes.push("js");
    document.body.className = classes.join(" ");
}
    </script>
    </head>
    """
        String expected = """
<head>
        <title>The Grails Framework 3.2.11</title>
        <meta http-equiv="Content-Type" content="text/html; charset=utf-8" />
        
        
    <script type="text/javascript">
function addJsClass(el) {
    var classes = document.body.className.split(" ");
    classes.push("js");
    document.body.className = classes.join(" ");
}
    </script>
    </head>
    """
        when:
        String output = PdfBuilder.removeCssLinks(html)

        then:
        output == expected

    }
}
