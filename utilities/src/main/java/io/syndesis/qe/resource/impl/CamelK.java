package io.syndesis.qe.resource.impl;

import static org.assertj.core.api.Assertions.fail;
import static org.assertj.core.api.Assumptions.assumeThat;

import io.syndesis.qe.Image;
import io.syndesis.qe.TestConfiguration;
import io.syndesis.qe.endpoints.TestSupport;
import io.syndesis.qe.resource.Resource;
import io.syndesis.qe.resource.ResourceFactory;
import io.syndesis.qe.test.InfraFail;
import io.syndesis.qe.utils.OpenShiftUtils;
import io.syndesis.qe.utils.TestUtils;
import io.syndesis.qe.wait.OpenShiftWaitUtils;

import org.apache.commons.compress.archivers.ArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.io.FileUtils;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import cz.xtf.core.openshift.OpenShift;
import io.fabric8.kubernetes.api.model.LocalObjectReference;
import io.fabric8.kubernetes.api.model.ServiceAccount;
import io.fabric8.kubernetes.api.model.apiextensions.CustomResourceDefinition;
import io.fabric8.kubernetes.client.dsl.base.CustomResourceDefinitionContext;
import io.fabric8.openshift.api.model.ImageStream;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class CamelK implements Resource {
    private static final String CAMEL_K_ARCHIVE_PATH = String.format(
        "https://github.com/apache/camel-k/releases/download/%s/camel-k-client-%s-%s-64bit.tar.gz",
        TestConfiguration.camelKVersion(),
        TestConfiguration.camelKVersion(),
        System.getProperty("os.name").toLowerCase().contains("mac") ? "mac" : "linux"
    );
    private static final String LOCAL_ARCHIVE_PATH = "/tmp/camelk.tar.gz";
    private static final String LOCAL_ARCHIVE_EXTRACT_DIRECTORY = "/tmp/camelk";

    @Override
    public void deploy() {
        if (TestUtils.isProdBuild()) {
            assumeThat(TestConfiguration.image(Image.CAMELK)).as("No Camel-K image was specified when running productized build").isNotNull();
        }
        downloadArchive();
        extractArchive();
        installViaBinary();
    }

    @Override
    public void undeploy() {
        // The server pod will not be present if the camel-k wasn't deployed
        if (OpenShiftUtils.getAnyPod("camel.apache.org/component", "operator").isPresent()) {
            // It is needed to reset-db first, otherwise server would create the integrations again
            TestSupport.getInstance().resetDB();
            resetState();
            OpenShift oc = OpenShiftUtils.getInstance();
            oc.apps().deployments().withName("camel-k-operator").delete();
            oc.getLabeledPods("camel.apache.org/component", "operator").forEach(oc::deletePod);
            ResourceFactory.get(Syndesis.class).changeRuntime("springboot");
        }
    }

    @Override
    public boolean isReady() {
        return OpenShiftWaitUtils.isPodReady(OpenShiftUtils.getAnyPod("name", "camel-k-operator"));
    }

    private void downloadArchive() {
        try {
            FileUtils.copyURLToFile(new URL(CAMEL_K_ARCHIVE_PATH), new File(LOCAL_ARCHIVE_PATH));
        } catch (IOException e) {
            fail("Unable to download file " + CAMEL_K_ARCHIVE_PATH, e);
        }
    }

    private void extractArchive() {
        Path input = Paths.get(LOCAL_ARCHIVE_PATH);
        Path output = Paths.get(LOCAL_ARCHIVE_EXTRACT_DIRECTORY);

        try {
            FileUtils.deleteDirectory(new File(LOCAL_ARCHIVE_EXTRACT_DIRECTORY));
        } catch (IOException e) {
            e.printStackTrace();
        }
        output.toFile().mkdirs();

        try (TarArchiveInputStream tais = new TarArchiveInputStream(
            new GzipCompressorInputStream(new BufferedInputStream(Files.newInputStream(input))))) {
            ArchiveEntry archiveentry = null;
            while ((archiveentry = tais.getNextEntry()) != null) {
                Path pathEntryOutput = output.resolve(archiveentry.getName());
                if (archiveentry.isDirectory()) {
                    if (!Files.exists(pathEntryOutput)) {
                        Files.createDirectory(pathEntryOutput);
                    }
                } else {
                    Files.copy(tais, pathEntryOutput);
                }
            }
        } catch (IOException e) {
            fail("Unable to extract " + LOCAL_ARCHIVE_PATH, e);
        }
    }

    private CustomResourceDefinitionContext getCamelKCRD() {
        CustomResourceDefinition crd = OpenShiftUtils.getInstance().customResourceDefinitions().withName("integrations.camel.apache.org").get();
        CustomResourceDefinitionContext.Builder builder = new CustomResourceDefinitionContext.Builder()
            .withGroup(crd.getSpec().getGroup())
            .withPlural(crd.getSpec().getNames().getPlural())
            .withScope(crd.getSpec().getScope())
            .withVersion(crd.getSpec().getVersion());
        return builder.build();
    }

    public void waitForContextToBuild(String integrationName) {
        final String resourceName = "i-" + integrationName.toLowerCase()
            .replaceAll(" ", "-")
            .replaceAll("/", "-")
            .replaceAll("_", "-");
        TestUtils.waitFor(() -> {
            Map<String, Object>
                integration = OpenShiftUtils.getInstance().customResource(getCamelKCRD()).get(TestConfiguration.openShiftNamespace(), resourceName);
            String phase = (String) ((Map<String, Object>) integration.get("status")).get("phase");
            return "running".equalsIgnoreCase(phase);
        }, 20, 10 * 60, "Context was not build in 10 minutes for integration " + integrationName);
    }

    private void installViaBinary() {
        try {
            new File(LOCAL_ARCHIVE_EXTRACT_DIRECTORY + "/kamel").setExecutable(true);
            StringBuilder arguments = new StringBuilder();
            if (TestUtils.isProdBuild()) {
                arguments.append(" --operator-image ").append(TestConfiguration.image(Image.CAMELK));
            }
            arguments.append(" -n ").append(TestConfiguration.openShiftNamespace());

            final String command = LOCAL_ARCHIVE_EXTRACT_DIRECTORY + "/kamel install" + arguments.toString();
            log.info("Invoking " + command);
            Runtime.getRuntime().exec(command).waitFor();
        } catch (Exception e) {
            fail("Unable to invoke kamel binary", e);
        }

        // We need to link syndesis-pull-secret to camel-k-operator SA
        try {
            OpenShiftWaitUtils.waitFor(() -> OpenShiftUtils.getInstance().getServiceAccount("camel-k-operator") != null);
        } catch (Exception e) {
            InfraFail.fail("Unable to get camel-k-operator service account", e);
        }
        ServiceAccount sa = OpenShiftUtils.getInstance().getServiceAccount("camel-k-operator");
        sa.getImagePullSecrets().add(new LocalObjectReference(TestConfiguration.syndesisPullSecretName()));
        OpenShiftUtils.getInstance().serviceAccounts().createOrReplace(sa);
        // It is very likely that the operator pod already tried to spawn and failed because of the missing secret
        OpenShiftUtils.getInstance().deletePods("name", "camel-k-operator");
    }

    public void resetState() {
        try {
            new File(LOCAL_ARCHIVE_EXTRACT_DIRECTORY + "/kamel").setExecutable(true);
            Runtime.getRuntime().exec(LOCAL_ARCHIVE_EXTRACT_DIRECTORY + "/kamel reset").waitFor();
            List<ImageStream> iss = OpenShiftUtils
                .getInstance()
                .imageStreams()
                .inNamespace(TestConfiguration.openShiftNamespace())
                .list()
                .getItems()
                .stream()
                .filter(imageStream -> imageStream.getMetadata().getName().contains("camel-k-ctx"))
                .collect(Collectors.toList());
            OpenShiftUtils.getInstance().pods().delete(
                OpenShiftUtils.getInstance().pods().list().getItems().stream()
                    .filter(pod -> pod.getMetadata().getName().startsWith("camel-k-ctx"))
                    .collect(Collectors.toList())
            );
            OpenShiftUtils.getInstance().imageStreams().delete(iss);
        } catch (Exception e) {
            fail("Unable to invoke kamel binary", e);
        }
    }
}
