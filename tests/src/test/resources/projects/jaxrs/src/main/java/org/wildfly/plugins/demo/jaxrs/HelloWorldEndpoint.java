package org.wildfly.plugins.demo.jaxrs;


import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Produces;


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
        System.out.println("CLASSLOADER " + HelloWorldEndpoint.class.getClassLoader());
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
        for(String k : props.stringPropertyNames()) {
            System.out.println("KEY " + k + "=" + props.getProperty(k));
        }
        //return Response.ok("Hello from " + "XXXWildFly bootable jar!").build();
        return Response.ok("Hello from " + props.getProperty("msg") + (props2 == null ? "" : " " + props2.getProperty("msg"))).build();
    }
}
