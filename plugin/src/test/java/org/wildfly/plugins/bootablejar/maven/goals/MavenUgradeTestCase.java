/*
 * JBoss, Home of Professional Open Source.
 *
 * Copyright 2021 Red Hat, Inc., and individual contributors
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
package org.wildfly.plugins.bootablejar.maven.goals;

import java.util.ArrayList;
import java.util.List;
import org.apache.maven.plugin.MojoExecutionException;
import org.jboss.galleon.ProvisioningException;

import org.junit.Assert;
import org.junit.Test;
import org.wildfly.plugins.bootablejar.maven.common.FeaturePack;
import org.wildfly.plugins.bootablejar.maven.common.OverriddenArtifact;

public class MavenUgradeTestCase {

    @Test
    public void testFeaturePack() throws Exception {
        try {
            MavenUpgrade.getFeaturePack("");
            throw new Exception("Should have failed");
        } catch (MojoExecutionException ex) {
            // XXX OK expected
        }
        try {
            MavenUpgrade.getFeaturePack("foo");
            throw new Exception("Should have failed");
        } catch (MojoExecutionException ex) {
            // XXX OK expected
        }
        try {
            MavenUpgrade.getFeaturePack("grp:art:class:ext:vers:foo");
            throw new Exception("Should have failed");
        } catch (MojoExecutionException ex) {
            // XXX OK expected
        }
        try {
            MavenUpgrade.getFeaturePack(null);
            throw new Exception("Should have failed");
        } catch (MojoExecutionException ex) {
            // XXX OK expected
        }
        String grpId = "grp";
        String artId = "art";
        String gac = grpId + ":" + artId;
        FeaturePack fp = MavenUpgrade.getFeaturePack(gac);
        Assert.assertEquals(grpId, fp.getGroupId());
        Assert.assertEquals(artId, fp.getArtifactId());
        Assert.assertEquals(gac, fp.getGAC());
        Assert.assertEquals(gac + "::zip", fp.getMavenCoords());

        String version = "vers";
        String gav = gac + ":" + version;
        fp = MavenUpgrade.getFeaturePack(gav);
        Assert.assertEquals(grpId, fp.getGroupId());
        Assert.assertEquals(artId, fp.getArtifactId());
        Assert.assertEquals(version, fp.getVersion());
        Assert.assertEquals(gac, fp.getGAC());
        Assert.assertEquals(gac + "::zip:" + version, fp.getMavenCoords());

        String classifier = "class";
        String extension = "ext";
        gac = gac + ":" + classifier;
        String coords = gac + ":" + extension;
        fp = MavenUpgrade.getFeaturePack(coords);
        Assert.assertEquals(grpId, fp.getGroupId());
        Assert.assertEquals(artId, fp.getArtifactId());
        Assert.assertEquals(classifier, fp.getClassifier());
        Assert.assertEquals(extension, fp.getExtension());
        Assert.assertEquals(gac, fp.getGAC());
        Assert.assertEquals(coords, fp.getMavenCoords());

        coords = coords + ":" + version;
        fp = MavenUpgrade.getFeaturePack(coords);
        Assert.assertEquals(grpId, fp.getGroupId());
        Assert.assertEquals(artId, fp.getArtifactId());
        Assert.assertEquals(classifier, fp.getClassifier());
        Assert.assertEquals(extension, fp.getExtension());
        Assert.assertEquals(version, fp.getVersion());
        Assert.assertEquals(gac, fp.getGAC());
        Assert.assertEquals(coords, fp.getMavenCoords());
    }

    @Test
    public void testOptionValue() throws Exception {
        List<OverriddenArtifact> lst = new ArrayList<>();
        OverriddenArtifact art1 = new OverriddenArtifact();
        lst.add(art1);
        try {
            MavenUpgrade.toOptionValue(lst);
            throw new Exception("Should have failed");
        } catch (ProvisioningException ex) {
            // XXX ok expected
        }
        art1.setGroupId("foo");
        try {
            MavenUpgrade.toOptionValue(lst);
            throw new Exception("Should have failed");
        } catch (ProvisioningException ex) {
            // XXX ok expected
        }
        art1.setArtifactId("bar");
        try {
            MavenUpgrade.toOptionValue(lst);
            throw new Exception("Should have failed");
        } catch (ProvisioningException ex) {
            // XXX ok expected
        }

        String gac = "foo:bar";
        Assert.assertEquals(art1.getGAC(), gac);

        art1.setVersion("vers");
        Assert.assertEquals(art1.getGAC(), gac);

        String expected = "foo:bar:vers::jar";
        String option = MavenUpgrade.toOptionValue(lst);
        Assert.assertEquals(expected, option);

        art1.setType(null);
        try {
            MavenUpgrade.toOptionValue(lst);
            throw new Exception("Should have failed");
        } catch (ProvisioningException ex) {
            // XXX ok expected
        }

        art1.setType("type");
        Assert.assertEquals(art1.getGAC(), gac);
        expected = "foo:bar:vers::type";
        option = MavenUpgrade.toOptionValue(lst);
        Assert.assertEquals(expected, option);

        art1.setClassifier("class");
        Assert.assertEquals(art1.getGAC(), gac + ":class");
        expected = "foo:bar:vers:class:type";

        option = MavenUpgrade.toOptionValue(lst);
        Assert.assertEquals(expected, option);

        OverriddenArtifact art2 = new OverriddenArtifact();
        art2.setGroupId("foo2");
        art2.setArtifactId("bar2");
        art2.setClassifier("class2");
        art2.setVersion("vers2");
        art2.setType("type2");

        expected = expected + "|foo2:bar2:vers2:class2:type2";
        lst.add(art2);

        option = MavenUpgrade.toOptionValue(lst);
        Assert.assertEquals(expected, option);

    }
}
