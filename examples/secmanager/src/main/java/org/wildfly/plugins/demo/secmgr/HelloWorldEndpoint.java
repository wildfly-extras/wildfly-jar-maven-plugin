package org.wildfly.plugins.demo.secmgr;


import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;

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
