package org.wildfly.plugins.demo.web;


import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Response;

@Path("/")
public class HelloWorldEndpoint {

    @GET
    @Produces("text/plain")
    public Response doGet() {
        return Response.ok("Hello from WildFly, war1!").build();
    }
}
