package org.wildfly.plugins.demo.jaxrs;

import javax.ws.rs.ApplicationPath;
import javax.ws.rs.core.Application;

@ApplicationPath("/rest/")
public class RestApplication extends Application {
}
