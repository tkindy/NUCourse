package com.tylerkindy.nucourse.config;

import com.google.common.io.Resources;
import com.google.inject.Inject;
import io.dropwizard.configuration.ConfigurationSourceProvider;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.sync.StreamingResponseHandler;

public class S3ConfigurationProvider implements ConfigurationSourceProvider {

  private static final Logger LOG = LoggerFactory.getLogger(S3ConfigurationProvider.class);
  private static final String CONFIG_S3_BUCKET = "nucourse";
  private static final int MAX_BYTES = 10000;

  private final S3Client s3Client;

  @Inject
  public S3ConfigurationProvider(S3Client s3Client) {
    this.s3Client = s3Client;
  }

  @Override
  public InputStream open(String path) throws IOException {
    DeployEnvironment env = DeployEnvironment.getCurrentEnv();

    if (env == DeployEnvironment.DEV) {
      return openLocalConfig(env);
    }

    return openRemoteConfig(env);
  }

  private InputStream openLocalConfig(DeployEnvironment env) throws IOException {
    LOG.warn("Loading local config...");
    URL configUrl = Resources.getResource(env.getConfigFilename());
    InputStream stream = configUrl.openStream();

    LOG.warn("Loaded local config!");
    return stream;
  }

  private InputStream openRemoteConfig(DeployEnvironment env) throws IOException {
    String configS3Key = "config/" + env.getConfigFilename();

    LOG.warn("Fetching {}...", configS3Key);
    GetObjectRequest request = GetObjectRequest.builder()
        .bucket(CONFIG_S3_BUCKET)
        .key(configS3Key)
        .build();

    ByteArrayOutputStream outputStream = new ByteArrayOutputStream(MAX_BYTES);
    s3Client.getObject(request, StreamingResponseHandler.toOutputStream(outputStream));
    LOG.warn("Fetched {}!", configS3Key);

    return new ByteArrayInputStream(outputStream.toByteArray());

  }
}
