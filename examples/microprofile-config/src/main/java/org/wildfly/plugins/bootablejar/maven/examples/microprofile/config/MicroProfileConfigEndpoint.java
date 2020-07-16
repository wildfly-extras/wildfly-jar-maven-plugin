package org.wildfly.plugins.bootablejar.maven.examples.microprofile.config;


import javax.inject.Inject;
import javax.ws.rs.ApplicationPath;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Application;
import javax.ws.rs.core.Response;

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
