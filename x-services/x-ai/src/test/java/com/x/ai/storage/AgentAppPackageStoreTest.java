package com.x.ai.storage;

import cn.hutool.crypto.digest.DigestUtil;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.minio.property.MinioProperties;
import io.minio.GetObjectResponse;
import io.minio.MinioClient;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentAppPackageStoreTest {

    @Test
    void readsOnlyTheReferencedBytesAndRejectsTamperedObjects() throws Exception {
        byte[] original = "original".getBytes(StandardCharsets.UTF_8);
        byte[] modified = "modified".getBytes(StandardCharsets.UTF_8);
        MinioClient minio = mock(MinioClient.class);
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(minio.getObject(any())).thenReturn(response);
        when(response.readNBytes(AgentAppPackageStore.MAX_PACKAGE_BYTES + 1)).thenReturn(modified);
        AgentAppPackageStore store = new AgentAppPackageStore(minio, new MinioProperties());

        assertThatThrownBy(() -> store.read("private/object.json", original.length, DigestUtil.sha256Hex(original)))
                .isInstanceOfSatisfying(XException.class, error ->
                        assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_APP_STORAGE_ERROR));
        verify(response).close();
    }

    @Test
    void rejectsAnOversizedDatabaseReferenceBeforeDownloading() {
        MinioClient minio = mock(MinioClient.class);
        AgentAppPackageStore store = new AgentAppPackageStore(minio, new MinioProperties());

        assertThatThrownBy(() -> store.read("private/object.json", AgentAppPackageStore.MAX_PACKAGE_BYTES + 1L, "hash"))
                .isInstanceOf(XException.class);
        verifyNoInteractions(minio);
    }

    @Test
    void acceptsAnObjectOnlyWhenItsSizeAndDigestBothMatch() throws Exception {
        byte[] content = "{}".getBytes(StandardCharsets.UTF_8);
        MinioClient minio = mock(MinioClient.class);
        GetObjectResponse response = mock(GetObjectResponse.class);
        when(minio.getObject(any())).thenReturn(response);
        when(response.readNBytes(AgentAppPackageStore.MAX_PACKAGE_BYTES + 1)).thenReturn(content);
        AgentAppPackageStore store = new AgentAppPackageStore(minio, new MinioProperties());

        assertThat(store.read("private/object.json", content.length, DigestUtil.sha256Hex(content))).isEqualTo(content);
    }
}
