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

import fish.payara.cloud.connectors.kafka.api.KafkaListener;
import fish.payara.cloud.connectors.kafka.api.OnRecord;
import jakarta.ejb.ActivationConfigProperty;
import jakarta.ejb.MessageDriven;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.jboss.ejb3.annotation.ResourceAdapter;

@MessageDriven(activationConfig = {
    @ActivationConfigProperty(propertyName = "clientId", propertyValue = "KafkaJCAClient"),
    @ActivationConfigProperty(propertyName = "groupIdConfig", propertyValue = "myGroup"),
    @ActivationConfigProperty(propertyName = "topics", propertyValue = "my-topic"),
    @ActivationConfigProperty(propertyName = "bootstrapServersConfig", propertyValue = "localhost:9092"),
    @ActivationConfigProperty(propertyName = "retryBackoff", propertyValue = "1000"),
    @ActivationConfigProperty(propertyName = "autoCommitInterval", propertyValue = "100"),
    @ActivationConfigProperty(propertyName = "keyDeserializer", propertyValue = "org.apache.kafka.common.serialization.StringDeserializer"),
    @ActivationConfigProperty(propertyName = "valueDeserializer", propertyValue = "org.apache.kafka.common.serialization.StringDeserializer"),
    @ActivationConfigProperty(propertyName = "pollInterval", propertyValue = "3000"),
    @ActivationConfigProperty(propertyName = "commitEachPoll", propertyValue = "true"),
    @ActivationConfigProperty(propertyName = "useSynchMode", propertyValue = "true")
})
@ResourceAdapter(value = "kafka")
public class SimpleMDB implements KafkaListener {

    public SimpleMDB() {
        System.out.println("Bean instance created");
    }

    @OnRecord(topics = {"my-topic"})
    public void getMessageTest(ConsumerRecord record) {
        System.out.println("> Got record on topic test " + record);
    }

}
