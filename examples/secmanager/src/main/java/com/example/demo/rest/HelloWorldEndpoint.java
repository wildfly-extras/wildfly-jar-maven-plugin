package com.example.demo.rest;


import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;

@Path("/hello")
public class HelloWorldEndpoint {

    @GET
    @Path("/read")
    @Produces("text/plain")
    public Response read() {
        System.getProperty("FOO");
        return Response.ok("Successfully read system property \"FOO\"").build();
    }

    @GET
    @Path("/write")
    @Produces("text/plain")
    public Response write() {
        System.setProperty("FOO", "VALUE");
        return Response.ok("Successfully write system property \"FOO\"").build();
    }
}
