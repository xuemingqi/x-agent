package com.x.ai.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.x.ai.storage.AgentAppPackageStore;
import com.x.ai.common.domain.AgentAppDetailDo;
import com.x.ai.common.dto.AgentAppSaveDto;
import com.x.ai.db.entity.AgentApp;
import com.x.ai.db.entity.AgentAppData;
import com.x.ai.db.entity.AgentAppVersion;
import com.x.ai.db.mapper.AgentAppMapper;
import com.x.ai.db.mapper.AgentAppDataMapper;
import com.x.ai.db.mapper.AgentAppVersionMapper;
import com.x.ai.db.service.impl.AgentAppDataIServiceImpl;
import com.x.ai.db.service.impl.AgentAppIServiceImpl;
import com.x.ai.db.service.impl.AgentAppVersionIServiceImpl;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.JsonUtil;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionSystemException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AgentAppServiceImplTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private AgentAppMapper appMapper;

    private AgentAppVersionMapper versionMapper;

    private AgentAppPackageStore objects;

    private AgentAppServiceImpl service;

    private PlatformTransactionManager transactionManager;

    @BeforeAll
    static void initializeEntityMapping() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "app-test"), AgentApp.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "app-data-test"), AgentAppData.class);
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), "version-test"),
                AgentAppVersion.class);
    }

    @BeforeEach
    void createService() {
        appMapper = mock(AgentAppMapper.class);
        versionMapper = mock(AgentAppVersionMapper.class);
        objects = mock(AgentAppPackageStore.class);
        AgentAppIServiceImpl apps = new AgentAppIServiceImpl();
        ReflectionTestUtils.setField(apps, "baseMapper", appMapper);
        ReflectionTestUtils.setField(apps, "entityClass", AgentApp.class);
        AgentAppVersionIServiceImpl versions = new AgentAppVersionIServiceImpl();
        ReflectionTestUtils.setField(versions, "baseMapper", versionMapper);
        ReflectionTestUtils.setField(versions, "entityClass", AgentAppVersion.class);
        AgentAppDataIServiceImpl data = new AgentAppDataIServiceImpl();
        ReflectionTestUtils.setField(data, "baseMapper", mock(AgentAppDataMapper.class));
        ReflectionTestUtils.setField(data, "entityClass", AgentAppData.class);
        transactionManager = mock(PlatformTransactionManager.class);
        service = new AgentAppServiceImpl(apps, versions, data, objects, VALIDATOR, transactionManager);
        when(appMapper.insert(any(AgentApp.class))).thenReturn(1);
        when(appMapper.updateById(any(AgentApp.class))).thenReturn(1);
        when(versionMapper.insert(any(AgentAppVersion.class))).thenReturn(1);
    }

    @Test
    void applicationReadChecksOwnerAndActiveStateBeforeAccessingObjectStorage() {
        assertThatThrownBy(() -> service.get("caller", "another-users-app"))
                .isInstanceOfSatisfying(XException.class, error ->
                        assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_APP_NOT_FOUND));

        ArgumentCaptor<LambdaQueryWrapper<AgentApp>> query = ArgumentCaptor.captor();
        verify(appMapper).selectOne(query.capture());
        assertThat(query.getValue().getSqlSegment()).contains("owner_user_id =", "id =", "deleted =");
        assertThat(query.getValue().getParamNameValuePairs().values()).contains("caller", "another-users-app", false);
        verifyNoInteractions(objects, versionMapper);
    }

    @Test
    void publishPersistsOnlyObjectReferenceAndIntegrityMetadataInTheDatabase() {
        AgentAppDetailDo result = service.create("caller", request());

        ArgumentCaptor<AgentApp> app = ArgumentCaptor.captor();
        ArgumentCaptor<AgentAppVersion> version = ArgumentCaptor.captor();
        ArgumentCaptor<byte[]> bytes = ArgumentCaptor.captor();
        ArgumentCaptor<String> key = ArgumentCaptor.captor();
        verify(objects).put(key.capture(), bytes.capture());
        verify(appMapper).insert(app.capture());
        verify(versionMapper).insert(version.capture());
        assertThat(app.getValue().getOwnerUserId()).isEqualTo("caller");
        assertThat(app.getValue().getCurrentVersion()).isEqualTo(result.getCurrentVersion());
        assertThat(version.getValue().getObjectKey()).isEqualTo(key.getValue());
        assertThat(key.getValue()).startsWith("agent-apps/caller/" + result.getId() + "/");
        assertThat(version.getValue().getSizeBytes()).isEqualTo(bytes.getValue().length);
        assertThat(version.getValue().getSha256()).isEqualTo(DigestUtil.sha256Hex(bytes.getValue()));
        assertThat(JsonUtil.OBJECT_MAPPER.readTree(bytes.getValue()).get("source").asText()).contains("textContent");
        assertThat(result.getContent().getSdkVersion()).isEqualTo(1);
    }

    @Test
    void aFailedDatabaseWriteDeletesTheUploadedPackage() {
        when(versionMapper.insert(any(AgentAppVersion.class))).thenReturn(0);

        assertThatThrownBy(() -> service.create("caller", request())).isInstanceOf(XException.class);

        ArgumentCaptor<String> uploaded = ArgumentCaptor.captor();
        verify(objects).put(uploaded.capture(), any(byte[].class));
        verify(objects).delete(uploaded.getValue());
    }

    @Test
    void anUnknownCommitOutcomeRetainsThePackageThatTheDatabaseMayAlreadyReference() {
        TransactionSystemException commitError = new TransactionSystemException("Commit acknowledgement lost");
        doThrow(commitError).when(transactionManager).commit(any());

        assertThatThrownBy(() -> service.create("caller", request())).isSameAs(commitError);

        verify(objects).put(anyString(), any(byte[].class));
        verify(objects, never()).delete(anyString());
        verify(appMapper).insert(any(AgentApp.class));
        verify(versionMapper).insert(any(AgentAppVersion.class));
    }

    @Test
    void anUnknownRollbackOutcomeAlsoRetainsThePackageForRecovery() {
        when(versionMapper.insert(any(AgentAppVersion.class))).thenReturn(0);
        TransactionSystemException rollbackError = new TransactionSystemException("Rollback acknowledgement lost");
        doThrow(rollbackError).when(transactionManager).rollback(any());

        assertThatThrownBy(() -> service.create("caller", request())).isSameAs(rollbackError);

        verify(objects).put(anyString(), any(byte[].class));
        verify(objects, never()).delete(anyString());
    }

    @Test
    void aConcurrentPublishCannotOverwriteTheVersionReadBeforeUpload() {
        when(appMapper.selectOne(any())).thenReturn(app("old"), app("someone-else-published"));

        assertThatThrownBy(() -> service.update("caller", "application", request().setBaseVersion("old")))
                .isInstanceOfSatisfying(XException.class, error ->
                        assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_APP_VERSION_CONFLICT));

        ArgumentCaptor<String> uploaded = ArgumentCaptor.captor();
        verify(objects).put(uploaded.capture(), any(byte[].class));
        verify(objects).delete(uploaded.getValue());
        verify(appMapper, never()).updateById(any(AgentApp.class));
        verify(versionMapper, never()).insert(any(AgentAppVersion.class));
    }

    @Test
    void undeclaredPlatformCapabilitiesAreRejectedBeforeUploading() {
        assertThatThrownBy(() -> service.create("caller", request().setPermissions(List.of("admin.execute"))))
                .isInstanceOfSatisfying(XException.class, error ->
                        assertThat(error.getResponseCodeEnum()).isEqualTo(ResponseCodeEnum.AGENT_APP_PACKAGE_INVALID));
        verifyNoInteractions(objects);
        verify(appMapper, never()).insert(any(AgentApp.class));
    }

    @Test
    void failedObjectDeletionRetainsTheHiddenDirectoryAndVersionReferenceForRetry() {
        AgentApp app = app("current");
        when(appMapper.selectOne(any())).thenReturn(app);
        when(versionMapper.selectList(any())).thenReturn(List.of(new AgentAppVersion()
                .setId("current").setAppId("application").setObjectKey("private/package.json")));
        doThrow(new XException(ResponseCodeEnum.AGENT_APP_STORAGE_ERROR)).when(objects).delete(anyString());

        assertThatThrownBy(() -> service.delete("caller", "application")).isInstanceOf(XException.class);

        assertThat(app.getDeleted()).isTrue();
        verify(appMapper).updateById(app);
        verify(appMapper, never()).delete(any());
        verify(versionMapper, never()).delete(any());
    }

    @Test
    void aMissingHistoricalObjectCannotBecomeThePublishedVersion() {
        when(appMapper.selectOne(any())).thenReturn(app("current"));
        when(versionMapper.selectOne(any())).thenReturn(new AgentAppVersion().setId("history")
                .setAppId("application").setObjectKey("private/history.json").setSizeBytes(100L).setSha256("hash"));
        when(objects.read("private/history.json", 100L, "hash"))
                .thenThrow(new XException(ResponseCodeEnum.AGENT_APP_STORAGE_ERROR));

        assertThatThrownBy(() -> service.activate("caller", "application", "history", "current"))
                .isInstanceOf(XException.class);
        verify(appMapper, never()).updateById(any(AgentApp.class));
    }

    private AgentApp app(String version) {
        return new AgentApp().setId("application").setOwnerUserId("caller").setName("示例")
                .setDescription("示例应用").setCurrentVersion(version).setDeleted(false);
    }

    private AgentAppSaveDto request() {
        return new AgentAppSaveDto().setName("示例").setDescription("示例应用").setHtml("<main id='root'></main>")
                .setCss("main { padding: 8px; }").setSource("document.querySelector('main')!.textContent = '完成';")
                .setPermissions(List.of("storage"));
    }
}
