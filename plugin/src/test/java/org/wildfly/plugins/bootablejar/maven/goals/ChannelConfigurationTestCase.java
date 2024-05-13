package org.wildfly.plugins.bootablejar.maven.goals;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.channel.Channel;

import java.util.Collections;

public class ChannelConfigurationTestCase {

    @Test
    public void testFileUrlFromString() throws Exception {
        {
            // Make sure that the notation "file:relative/path" is handled like a file URL, rather than a Maven G:A.

            String url = "file:path/to/manifest.yaml";
            ChannelConfiguration configuration = new ChannelConfiguration();
            configuration.set(url);
            Channel channel = configuration.toChannel(Collections.emptyList());

            Assert.assertNotNull(channel.getManifestCoordinate().getUrl());
            Assert.assertEquals(url, channel.getManifestCoordinate().getUrl().toExternalForm());
        }

        {
            // The notation "file://relative/path" should still be handled like a file URL too.

            String url = "file://path/to/manifest.yaml";
            ChannelConfiguration configuration = new ChannelConfiguration();
            configuration.set(url);
            Channel channel = configuration.toChannel(Collections.emptyList());

            Assert.assertNotNull(channel.getManifestCoordinate().getUrl());
            Assert.assertEquals(url, channel.getManifestCoordinate().getUrl().toExternalForm());
        }
    }

    @Test
    public void testHttpUrlFromString() throws Exception {
        String url = "http://wildfly.org/path/to/manifest";
        ChannelConfiguration configuration = new ChannelConfiguration();
        configuration.set(url);
        Channel channel = configuration.toChannel(Collections.emptyList());

        Assert.assertNotNull(channel.getManifestCoordinate().getUrl());
        Assert.assertEquals(url, channel.getManifestCoordinate().getUrl().toExternalForm());
    }

    @Test
    public void testMavenGavFromString() throws Exception {
        {
            String gav = "g:a:v";
            ChannelConfiguration configuration = new ChannelConfiguration();
            configuration.set(gav);
            Channel channel = configuration.toChannel(Collections.emptyList());

            Assert.assertNotNull(channel.getManifestCoordinate().getMaven());
            Assert.assertEquals("g", channel.getManifestCoordinate().getMaven().getGroupId());
            Assert.assertEquals("a", channel.getManifestCoordinate().getMaven().getArtifactId());
            Assert.assertEquals("v", channel.getManifestCoordinate().getMaven().getVersion());
        }

        {
            String gav = "g:a";
            ChannelConfiguration configuration = new ChannelConfiguration();
            configuration.set(gav);
            Channel channel = configuration.toChannel(Collections.emptyList());

            Assert.assertNotNull(channel.getManifestCoordinate().getMaven());
            Assert.assertEquals("g", channel.getManifestCoordinate().getMaven().getGroupId());
            Assert.assertEquals("a", channel.getManifestCoordinate().getMaven().getArtifactId());
            Assert.assertNull(channel.getManifestCoordinate().getMaven().getVersion());
        }
    }
}
