package org.wildfly.plugins.demo.microprofile.config;


import jakarta.inject.Inject;
import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.Application;
import jakarta.ws.rs.core.Response;

import org.eclipse.microprofile.config.inject.ConfigProperty;


@Path("/")
public class MicroProfileConfigEndpoint {


    @Inject
    @ConfigProperty(name = "config1", defaultValue = "Default value for config1 comes from my code")
    String config1;

    @Inject
    @ConfigProperty(name = "config2", defaultValue = "Default value for config2 comes from my code")
    String config2;

    @Inject
    @ConfigProperty(name = "config3", defaultValue = "Default value for config3 comes from my code")
    String config3;

    @GET
    @Produces("text/plain")
    public Response doGet() {

        StringBuilder out = new StringBuilder("config1 = " + config1);
        out.append("\nconfig2 = " + config2);
        out.append("\nconfig3 = " + config3);

        return Response.ok(out).build();
    }

    @ApplicationPath("/")
    public static class RestApplication extends Application {
    }
}
