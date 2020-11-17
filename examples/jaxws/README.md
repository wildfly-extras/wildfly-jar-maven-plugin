# JAX-WS WildFly bootable jar example

Build a bootable JAR containing a JAX-WS resource.

Build and run
=============

* To build: `mvn package`
* To run: `mvn wildfly-jar:run`
* Access the WSDL from any SOAP Client: `http://localhost:8080/jaxws-pojo-endpoint/JSEBean`

* Send a SOAP Request. Ex.:

`<soapenv:Envelope xmlns:soapenv="http://schemas.xmlsoap.org/soap/envelope/" xmlns:jax="http://jaxws.demo.plugins.wildfly.org/">
   <soapenv:Header/>
   <soapenv:Body>
      <jax:echo>
         <arg0>John</arg0>
      </jax:echo>
   </soapenv:Body>
</soapenv:Envelope>`

