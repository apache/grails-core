<!doctype html>
<html>
    <head>
        <title>Page Not Found</title>
        <meta name="layout" content="main">
        <g:if env="development"><asset:stylesheet src="errors.css"/></g:if>
    </head>
    <body>
        <div id="content" role="main">
            <div class="container">
                <section class="row">
                    <div class="alert alert-danger" role="alert">
                        <h1>Error: Page Not Found (404)</h1>
                        <div><i class="bi-exclamation-circle"></i> Path: ${request.forwardURI}</div>
                    </div>
                </section>
            </div>
        </div>
    </body>
</html>
