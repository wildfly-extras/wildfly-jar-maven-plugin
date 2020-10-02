package org.wildfly.plugins.demo.web;


import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Response;

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
