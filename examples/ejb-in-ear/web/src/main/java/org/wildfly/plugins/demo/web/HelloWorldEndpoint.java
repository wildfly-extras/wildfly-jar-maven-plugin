package org.wildfly.plugins.demo.web;


import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.core.Response;

import org.wildfly.plugins.demo.ejb.GreeterEJB;


@Path("/hello")
@RequestScoped
public class HelloWorldEndpoint {

    @Inject
    private GreeterEJB greeterEJB;

    @GET
    @Path("{name}")
    public Response doGet(@PathParam("name") String name) {
        return Response.ok(greeterEJB.sayHello(name)).build();
    }
}
