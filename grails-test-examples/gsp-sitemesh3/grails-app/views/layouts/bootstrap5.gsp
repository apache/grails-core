<%@ page contentType="text/html;charset=UTF-8" %>
<!doctype html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <title><g:layoutTitle default="BootStrap Demo"/></title>
    <link href="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/css/bootstrap.min.css" rel="stylesheet" integrity="sha384-9ndCyUaIbzAi2FUVXJi0CjmCapSmO7SnpJef0486qhLnuZ2cdeRhO02iuK6FUUVM" crossorigin="anonymous">
    <link rel="stylesheet" href="https://cdn.jsdelivr.net/npm/bootstrap-icons@1.10.5/font/bootstrap-icons.css">

    <script type="text/javascript">
        function setTheme(theme) {
            const items = document.querySelectorAll(".theme .dropdown-menu .dropdown-item")
            for (let i = 0; i < items.length; i++) {
                if (items[i].text.trim().toLowerCase() === theme) {
                    document.querySelector('.theme .dropdown-toggle i').setAttribute('class',
                        items[i].getElementsByTagName('i')[0].getAttribute('class'))
                    break
                }
            }
            document.getElementsByTagName('html')[0].setAttribute('data-bs-theme', theme != 'auto'? theme :
                (window.matchMedia('(prefers-color-scheme: dark)').matches? 'dark' : 'light'))
        }
    </script>

    <g:layoutHead/>
</head>
<body>
<header class="navbar navbar-expand-lg bd-navbar sticky-top bg-primary-subtle">
    <div class="container-fluid">
        <div class="col-md-3 mb-2 mb-md-0">
            <a href="/" class="d-inline-flex link-body-emphasis text-decoration-none">SiteMesh</a>
        </div>

        <ul class="nav col-12 col-md-auto mb-2 justify-content-center mb-md-0">
            <li><a href="/demo/index" class="nav-link px-2${actionName == 'index'?" link-secondary":""}">Forced Layout</a></li>
            <li><a href="/demo/chaining" class="nav-link px-2${actionName == 'chaining'?" link-secondary":""}">Decorator Chaining</a></li>
            <li><a href="/demo/jsp" class="nav-link px-2${actionName == 'jsp'?" link-secondary":""}">JSP Demo</a></li>
            <li><a href="/demo/renderText" class="nav-link px-2" target="_blank">Text</a></li>
            <li><a href="/demo/exception" class="nav-link px-2${actionName == 'exception'?" link-secondary":""}">Controller 500 Example</a></li>
            <li><a href="/demo/viewException" class="nav-link px-2${actionName == 'viewException'?" link-secondary":""}">View 500 Example</a></li>
            <li><a href="/demo/404" class="nav-link px-2${response.status == 404?" link-secondary":""}">404 Error</a></li>
        </ul>

        <ul class="navbar-nav flex-row flex-wrap ms-md-auto">
            <div class="dropdown nav-item text-end me-2">
                <a href="#" class="d-block link-body-emphasis text-decoration-none dropdown-toggle" data-bs-toggle="dropdown" aria-expanded="false">
                    <i class="bi-info-circle-fill"></i>
                </a>
                <ul class="dropdown-menu text-small dropdown-menu-end">
                    <li class="dropdown-item">Server: ${request.servletContext.serverInfo}</li>
                    <li class="dropdown-item">Host: ${java.net.InetAddress.getLocalHost()}/li>
                    <li role="separator" class="dropdown-divider"></li>
                    <li class="dropdown-item">JVM version: ${System.getProperty("java.version")}</li>
                </ul>
            </div>

            <div class="theme dropdown nav-item text-end ml-auto">
                <a href="#" class="d-block link-body-emphasis text-decoration-none dropdown-toggle" data-bs-toggle="dropdown" aria-expanded="false">
                    <i class="bi-sun-fill"></i>
                </a>
                <ul class="dropdown-menu text-small dropdown-menu-end">
                    <li><a class="dropdown-item" href="#"><i class="bi-sun-fill"></i> Light</a></li>
                    <li><a class="dropdown-item" href="#"><i class="bi-moon-stars-fill"></i> Dark</a></li>
                    <li><a class="dropdown-item" href="#"><i class="bi-circle-half"></i> Auto</a></li>
                </ul>
            </div>
        </ul>
    </div>
</header>
<div class="container">
    <h1>SiteMesh Example Site: <g:layoutTitle default="SiteMesh"/></h1>
    <g:layoutBody/>
    <footer>Note: this demo requires an internet connection for BootStrap to show up.</footer>
</div>

<script src="https://cdn.jsdelivr.net/npm/bootstrap@5.3.0/dist/js/bootstrap.bundle.min.js" integrity="sha384-geWF76RCwLtnZ8qwWowPQNguL3RmwHVBC9FhGdlKrxdiJJigb/j/68SIy3Te4Bkz" crossorigin="anonymous"></script>
<script type="text/javascript">
    setTheme('auto')
    const items = document.querySelectorAll(".theme .dropdown-menu .dropdown-item")
    for (let i = 0; i < items.length; i++) {
        items[i].addEventListener('click', function() {
            setTheme(this.text.trim().toLowerCase())
        })
    }
</script>
</body>
</html>