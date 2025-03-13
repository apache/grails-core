<%@ page contentType="text/html;charset=UTF-8" %>
<html>
<head>
    <title><g:message code="book.title" default="Books"/></title>
    <meta name="layout" content="main"/>
</head>
<body>
<g:each in="${books}" var="book">
    ${book.title}
</g:each>
</body>
</html>