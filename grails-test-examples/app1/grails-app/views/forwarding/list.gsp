<html>
    <head>
        <meta name="layout" content="main" />
    </head>
    <body>
        <h2>People</h2>
        <ul>
            <g:each var="person" in="${people}">
                <li>${person}</li>
            </g:each>
        </ul>
    </body>
</html>
