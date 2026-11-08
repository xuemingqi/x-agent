package com.x.ai.config;

import io.agentscope.harness.agent.sandbox.snapshot.RemoteSnapshotClient;
import io.minio.BucketExistsArgs;
import io.minio.GetObjectArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.errors.ErrorResponseException;

import java.io.InputStream;

public class MinioRemoteSnapshotClient implements RemoteSnapshotClient {

    private static final long MULTIPART_SIZE = 10L * 1024 * 1024;

    private final MinioClient minioClient;

    private final String bucket;

    private final String objectPrefix;

    private final boolean createBucketIfAbsent;

    public MinioRemoteSnapshotClient(
            MinioClient minioClient,
            String bucket,
            String objectPrefix,
            boolean createBucketIfAbsent) {
        this.minioClient = minioClient;
        this.bucket = bucket;
        this.objectPrefix = normalizePrefix(objectPrefix);
        this.createBucketIfAbsent = createBucketIfAbsent;
    }

    public void initialize() throws Exception {
        boolean exists = minioClient.bucketExists(BucketExistsArgs.builder().bucket(bucket).build());
        if (exists) {
            return;
        }
        if (!createBucketIfAbsent) {
            throw new IllegalStateException("MinIO bucket does not exist: " + bucket);
        }
        minioClient.makeBucket(MakeBucketArgs.builder().bucket(bucket).build());
    }

    @Override
    public void upload(String id, InputStream data) throws Exception {
        minioClient.putObject(PutObjectArgs.builder()
                .bucket(bucket)
                .object(objectName(id))
                .stream(data, -1L, MULTIPART_SIZE)
                .contentType("application/x-tar")
                .build());
    }

    @Override
    public InputStream download(String id) throws Exception {
        return minioClient.getObject(GetObjectArgs.builder()
                .bucket(bucket)
                .object(objectName(id))
                .build());
    }

    @Override
    public boolean exists(String id) throws Exception {
        try {
            minioClient.statObject(StatObjectArgs.builder()
                    .bucket(bucket)
                    .object(objectName(id))
                    .build());
            return true;
        } catch (ErrorResponseException error) {
            String code = error.errorResponse().code();
            if ("NoSuchKey".equals(code) || "NoSuchObject".equals(code)) {
                return false;
            }
            throw error;
        }
    }

    private String objectName(String id) {
        return objectPrefix.isEmpty() ? id : objectPrefix + "/" + id;
    }

    private static String normalizePrefix(String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return "";
        }
        return prefix.replaceAll("^/+|/+$", "");
    }
}
