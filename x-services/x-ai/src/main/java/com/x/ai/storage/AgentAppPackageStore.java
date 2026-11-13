package com.x.ai.storage;

import cn.hutool.crypto.digest.DigestUtil;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.minio.property.MinioProperties;
import io.minio.GetObjectArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class AgentAppPackageStore {

    public static final int MAX_PACKAGE_BYTES = 4 * 1024 * 1024;

    private final MinioClient minioClient;

    private final MinioProperties minioProperties;


    /**
     * 写入不可变应用包，对象键由应用服务生成，不接受用户提供的地址。
     */
    public void put(String objectKey, byte[] content) {
        try (ByteArrayInputStream stream = new ByteArrayInputStream(content)) {
            minioClient.putObject(PutObjectArgs.builder().bucket(minioProperties.getBucket()).object(objectKey)
                    .contentType("application/json; charset=utf-8").stream(stream, (long) content.length, -1L).build());
        } catch (Exception error) {
            throw storageError("put", objectKey, error);
        }
    }

    /**
     * 有界读取数据库引用的对象，并按数据库记录校验大小和摘要。
     */
    public byte[] read(String objectKey, long sizeBytes, String sha256) {
        if (sizeBytes < 1 || sizeBytes > MAX_PACKAGE_BYTES) {
            throw new XException(ResponseCodeEnum.AGENT_APP_STORAGE_ERROR);
        }
        try (var stream = minioClient.getObject(GetObjectArgs.builder()
                .bucket(minioProperties.getBucket()).object(objectKey).build())) {
            byte[] content = stream.readNBytes(MAX_PACKAGE_BYTES + 1);
            if (content.length != sizeBytes || !DigestUtil.sha256Hex(content).equals(sha256)) {
                throw new XException(ResponseCodeEnum.AGENT_APP_STORAGE_ERROR);
            }
            return content;
        } catch (XException error) {
            throw error;
        } catch (Exception error) {
            throw storageError("read", objectKey, error);
        }
    }

    /**
     * 删除已废弃的应用包或提交失败的对象。
     */
    public void delete(String objectKey) {
        try {
            minioClient.removeObject(RemoveObjectArgs.builder()
                    .bucket(minioProperties.getBucket()).object(objectKey).build());
        } catch (Exception error) {
            throw storageError("delete", objectKey, error);
        }
    }

    /**
     * 统一映射对象存储故障，日志不包含源码和凭据。
     */
    private XException storageError(String operation, String objectKey, Exception error) {
        log.warn("智能应用对象存储失败，operation={}, objectKey={}", operation, objectKey, error);
        return new XException(ResponseCodeEnum.AGENT_APP_STORAGE_ERROR);
    }
}
