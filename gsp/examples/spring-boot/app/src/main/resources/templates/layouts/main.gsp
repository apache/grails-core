<%@ page import="org.springframework.boot.SpringBootVersion; org.springframework.core.SpringVersion" %>
<html>
    <head>
        <title>Decorated <g:layoutTitle /></title>
        <g:layoutHead />
    </head>
    <body>
        <h1>${viewType}</h1>
        <h3>Spring: ${SpringVersion.getVersion()} Boot: ${SpringBootVersion.getVersion()}</h3>
        <g:layoutBody />
        <footer><a href="/${viewType=='JSP'?'gsp':'jsp'}">Try ${viewType=='JSP'?'gsp':'jsp'}</a></footer>
    </body>
</html>