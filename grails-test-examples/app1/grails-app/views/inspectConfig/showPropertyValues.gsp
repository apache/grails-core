<html>
<body>
    <h1> Nested </h1>
    <div>${prop1}</div>
    <div>${prop2}</div>
    <div>${prop3}</div>
    <h1> Flat </h1>
    <div>${prop1Flat}</div>
    <div>${prop2Flat}</div>
    <div>${prop3Flat}</div>
    <h1> Environment </h1>
    <div>${env.getProperty('grails11951.prop1')}</div>
    <div>${env.getProperty('grails11951.prop2')}</div>
    <div>${env.getProperty('grails11951.prop3')}</div>

</body>
</html>
