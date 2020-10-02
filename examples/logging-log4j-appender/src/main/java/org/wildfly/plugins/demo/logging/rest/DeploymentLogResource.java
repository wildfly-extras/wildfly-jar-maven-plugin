/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2020 Red Hat, Inc., and individual contributors
 * as indicated by the @author tags.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wildfly.plugins.demo.logging.rest;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.StringReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import javax.inject.Inject;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.wildfly.plugins.demo.logging.model.LogMessage;
import org.wildfly.plugins.demo.logging.service.ScheduledLogger;

/**
 * @author <a href="mailto:jperkins@redhat.com">James R. Perkins</a>
 */
@Path("/log")
public class DeploymentLogResource {

    @Inject
    private ScheduledLogger logService;

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public Response read() throws IOException {
        final java.nio.file.Path logFile = getLogFile();
        if (Files.notExists(logFile)) {
            return Response.serverError()
                    .entity("Could not find the log file deployment.log")
                    .build();
        }
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        readLogFile(logFile).forEach(builder::add);
        return Response.ok(builder.build()).build();
    }

    @POST
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.APPLICATION_JSON)
    public Response createLog(final LogMessage logMessage) {
        logService.log(logMessage);
        return Response.ok(logMessage).build();
    }

    @GET
    @Path("/active")
    @Produces(MediaType.APPLICATION_JSON)
    public Response listActiveJobs() {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        logService.runningJobs().forEach(id -> {
            final JsonObjectBuilder object = Json.createObjectBuilder();
            object.add("id", id);
            builder.add(object);
        });
        return Response.ok(builder.build()).build();
    }

    @POST
    @Path("/start")
    @Produces(MediaType.APPLICATION_JSON)
    public Response start() {
        final JsonObject json = Json.createObjectBuilder()
                .add("id", logService.start())
                .build();
        return Response.ok(json).build();
    }

    @POST
    @Path("/start/{seconds}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response start(@PathParam("seconds") final int seconds) {
        final JsonObject json = Json.createObjectBuilder()
                .add("id", logService.start(seconds))
                .build();
        return Response.ok(json).build();
    }

    @POST
    @Path("/stop")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stop() {
        final JsonArrayBuilder builder = Json.createArrayBuilder();
        logService.stopAll().forEach((id, cancelled) -> {
            final JsonObjectBuilder objectBuilder = Json.createObjectBuilder();
            objectBuilder.add("id", id);
            objectBuilder.add("cancelled", cancelled);
            builder.add(objectBuilder);
        });
        return Response.ok(builder.build()).build();
    }

    @POST
    @Path("/stop/{id}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response stop(@PathParam("id") final String id) {
        if (logService.stop(id)) {
            final JsonObject json = Json.createObjectBuilder()
                    .add(id, true)
                    .build();
            return Response.ok(json).build();
        }
        return Response.status(Response.Status.NOT_FOUND)
                .entity(Json.createObjectBuilder().add("error", "logger " + id + " not found").build())
                .build();
    }

    private static List<JsonObject> readLogFile(final java.nio.file.Path logFile) throws IOException {
        final List<JsonObject> lines = new ArrayList<>();
        try (BufferedReader reader = Files.newBufferedReader(logFile, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                try (JsonReader jsonReader = Json.createReader(new StringReader(line))) {
                    lines.add(jsonReader.readObject());
                }
            }
        }
        return lines;
    }

    private static java.nio.file.Path getLogFile() {
        final String logDir = System.getProperty("jboss.server.log.dir");
        return Paths.get(logDir, "log4j.log");
    }
}
