package org.wildfly.plugins.demo.jaxrs;


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import javax.ws.rs.GET;
import javax.ws.rs.Produces;


@Path("/hello")
public class HelloWorldEndpoint {
    @GET
    @Produces("text/plain")
    public Response doGet() throws IOException {
        Properties props;
        try (InputStream inputStream = HelloWorldEndpoint.class.getResourceAsStream("/myresources.properties")) {
            props = new Properties();
            props.load(inputStream);
        }
        InputStream inputStream2 = HelloWorldEndpoint.class.getResourceAsStream("/myresources2.properties");
        Properties props2 = null;
        if (inputStream2 != null) {
            try {
                props2 = new Properties();
                props2.load(inputStream2);
            } finally {
                inputStream2.close();
            }
        }

        //return Response.ok("Hello from " + "XXXWildFly bootable jar!").build();
        return Response.ok("Hello from " + props.getProperty("msg") + (props2 == null ? "" : " " + props2.getProperty("msg"))).build();
    }
}
