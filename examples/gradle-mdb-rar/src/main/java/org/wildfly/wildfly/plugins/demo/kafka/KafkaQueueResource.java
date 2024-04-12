/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2022 Red Hat, Inc., and individual contributors
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
package org.wildfly.wildfly.plugins.demo.kafka;

import fish.payara.cloud.connectors.kafka.api.KafkaConnection;
import fish.payara.cloud.connectors.kafka.api.KafkaConnectionFactory;
import jakarta.annotation.Resource;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.core.Response;
import org.apache.kafka.clients.producer.ProducerRecord;

/**
 *
 * @author Emmanuel Hugonnet (c) 2022 Red Hat, Inc.
 */
@Path("/kafka")

@ApplicationScoped
public class KafkaQueueResource {

    @Resource(lookup = "java:/kafka/ConnectionFactory")
    KafkaConnectionFactory factory;

    public KafkaQueueResource() {
    }

    @GET
    public Response hello() {

        try (KafkaConnection conn = factory.createConnection()) {
            conn.send(new ProducerRecord("my-topic", "Hello world!"));
            return Response
                    .status(Response.Status.OK)
                    .entity("Message sent!")
                    .build();
        } catch (Exception ex) {
            ex.printStackTrace();
            return Response.serverError().entity(ex.getMessage()).build();
        }

    }
}
