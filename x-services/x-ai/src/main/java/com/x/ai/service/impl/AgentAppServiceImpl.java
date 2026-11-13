package com.x.ai.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import com.x.ai.service.AgentAppService;
import com.x.ai.storage.AgentAppPackageStore;
import com.x.ai.common.domain.AgentAppDetailDo;
import com.x.ai.common.domain.AgentAppDo;
import com.x.ai.common.domain.AgentAppPackageDo;
import com.x.ai.common.domain.AgentAppVersionDo;
import com.x.ai.common.dto.AgentAppSaveDto;
import com.x.ai.db.entity.AgentApp;
import com.x.ai.db.entity.AgentAppData;
import com.x.ai.db.entity.AgentAppVersion;
import com.x.ai.db.service.AgentAppDataIService;
import com.x.ai.db.service.AgentAppIService;
import com.x.ai.db.service.AgentAppVersionIService;
import com.x.framework.common.annotation.LogIgnore;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.IdUtil;
import com.x.framework.common.utils.JsonUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import jakarta.validation.Validator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@Slf4j
@Service
public class AgentAppServiceImpl implements AgentAppService {

    private static final Set<String> SUPPORTED_PERMISSIONS = Set.of("model.generate", "agent.run", "storage", "http.request");

    private final AgentAppIService appIService;

    private final AgentAppVersionIService versionIService;

    private final AgentAppDataIService dataIService;

    private final AgentAppPackageStore packageStore;

    private final Validator validator;

    private final TransactionTemplate transaction;


    public AgentAppServiceImpl(AgentAppIService appIService,
                               AgentAppVersionIService versionIService, AgentAppDataIService dataIService,
                               AgentAppPackageStore packageStore, Validator validator,
                               PlatformTransactionManager transactionManager) {
        this.appIService = appIService;
        this.versionIService = versionIService;
        this.dataIService = dataIService;
        this.packageStore = packageStore;
        this.validator = validator;
        this.transaction = new TransactionTemplate(transactionManager);
    }

    @Override
    public List<AgentAppDo> list() {
        return list(UserThreadLocalUtil.getUserId());
    }

    @Override
    public List<AgentAppDo> list(String userId) {
        return appIService.lambdaQuery().eq(AgentApp::getOwnerUserId, userId).eq(AgentApp::getDeleted, false)
                .orderByDesc(AgentApp::getUpdateTime).list().stream().map(this::toDirectory).toList();
    }

    @LogIgnore
    @Override
    public AgentAppDetailDo get(String id) {
        return get(UserThreadLocalUtil.getUserId(), id);
    }

    @LogIgnore
    @Override
    public AgentAppDetailDo get(String userId, String id) {
        AgentApp app = requireOwned(userId, id);
        return toDetail(app, readPackage(requireVersion(id, app.getCurrentVersion())));
    }

    @LogIgnore
    @Override
    public AgentAppDetailDo create(AgentAppSaveDto request) {
        return create(UserThreadLocalUtil.getUserId(), request);
    }

    @LogIgnore
    @Override
    public AgentAppDetailDo create(String userId, AgentAppSaveDto request) {
        return publish(userId, IdUtil.fastUUID(), request, true);
    }

    @LogIgnore
    @Override
    public AgentAppDetailDo update(String id, AgentAppSaveDto request) {
        return update(UserThreadLocalUtil.getUserId(), id, request);
    }

    @LogIgnore
    @Override
    public AgentAppDetailDo update(String userId, String id, AgentAppSaveDto request) {
        AgentApp app = requireOwned(userId, id);
        requireBaseVersion(app, request == null ? null : request.getBaseVersion());
        return publish(userId, id, request, false);
    }

    @Override
    public List<AgentAppVersionDo> versions(String id) {
        return versions(UserThreadLocalUtil.getUserId(), id);
    }

    @Override
    public List<AgentAppVersionDo> versions(String userId, String id) {
        AgentApp app = requireOwned(userId, id);
        return versionIService.lambdaQuery().eq(AgentAppVersion::getAppId, id)
                .orderByDesc(AgentAppVersion::getCreateTime).orderByDesc(AgentAppVersion::getId).list().stream()
                .map(version -> new AgentAppVersionDo().setVersion(version.getId()).setName(version.getName())
                        .setDescription(version.getDescription()).setSizeBytes(version.getSizeBytes())
                        .setSha256(version.getSha256()).setCreateTime(version.getCreateTime())
                        .setCurrent(version.getId().equals(app.getCurrentVersion())))
                .toList();
    }

    @LogIgnore
    @Override
    public AgentAppDetailDo activate(String id, String version, String baseVersion) {
        return activate(UserThreadLocalUtil.getUserId(), id, version, baseVersion);
    }

    @LogIgnore
    @Override
    public AgentAppDetailDo activate(String userId, String id, String version, String baseVersion) {
        requireBaseVersion(requireOwned(userId, id), baseVersion);
        AgentAppVersion target = requireVersion(id, version);
        AgentAppPackageDo content = readPackage(target);
        return transaction.execute(status -> {
            AgentApp app = requireLocked(userId, id, false);
            requireBaseVersion(app, baseVersion);
            app.setName(target.getName()).setDescription(target.getDescription()).setCurrentVersion(version);
            if (!appIService.updateById(app)) {
                throw new XException(ResponseCodeEnum.AGENT_APP_VERSION_CONFLICT);
            }
            return toDetail(app, content);
        });
    }

    @Override
    public boolean delete(String id) {
        return delete(UserThreadLocalUtil.getUserId(), id);
    }

    @Override
    public boolean delete(String userId, String id) {
        List<AgentAppVersion> versions = transaction.execute(status -> {
            AgentApp app = requireLocked(userId, id, true);
            if (!Boolean.TRUE.equals(app.getDeleted())) {
                app.setDeleted(true);
                if (!appIService.updateById(app)) {
                    throw new XException(ResponseCodeEnum.AGENT_APP_VERSION_CONFLICT);
                }
            }
            dataIService.lambdaUpdate().eq(AgentAppData::getAppId, id)
                    .eq(AgentAppData::getOwnerUserId, userId).remove();
            return versionIService.lambdaQuery().eq(AgentAppVersion::getAppId, id).list();
        });
        // 保留隐藏目录及版本引用直至全部对象删除成功，允许对象存储故障后重试原应用 ID。
        versions.forEach(version -> packageStore.delete(version.getObjectKey()));
        return Boolean.TRUE.equals(transaction.execute(status -> {
            requireLocked(userId, id, true);
            return appIService.lambdaUpdate().eq(AgentApp::getId, id).eq(AgentApp::getOwnerUserId, userId)
                    .eq(AgentApp::getDeleted, true).remove();
        }));
    }

    /**
     * 先写不可变对象，再在短事务内发布；明确回滚时清理，事务结果不明时保留对象供恢复。
     */
    private AgentAppDetailDo publish(String userId, String id, AgentAppSaveDto request, boolean creating) {
        AgentAppPackageDo content = validatePackage(request);
        byte[] bytes = JsonUtil.OBJECT_MAPPER.writeValueAsBytes(content);
        if (bytes.length > AgentAppPackageStore.MAX_PACKAGE_BYTES) {
            throw new XException(ResponseCodeEnum.AGENT_APP_PACKAGE_INVALID);
        }
        String versionId = IdUtil.fastUUID();
        String objectKey = "agent-apps/" + userId + "/" + id + "/" + versionId + ".json";
        AgentAppVersion version = new AgentAppVersion().setId(versionId).setAppId(id)
                .setName(request.getName().strip()).setDescription(request.getDescription().strip())
                .setObjectKey(objectKey).setSizeBytes((long) bytes.length).setSha256(DigestUtil.sha256Hex(bytes));
        try {
            packageStore.put(objectKey, bytes);
            return transaction.execute(status -> {
                AgentApp app = creating ? new AgentApp().setId(id).setOwnerUserId(userId).setDeleted(false)
                        : requireLocked(userId, id, false);
                if (!creating) {
                    requireBaseVersion(app, request.getBaseVersion());
                }
                app.setName(version.getName()).setDescription(version.getDescription()).setCurrentVersion(versionId);
                boolean saved = creating ? appIService.save(app) : appIService.updateById(app);
                if (!saved || !versionIService.save(version)) {
                    throw new XException(ResponseCodeEnum.AGENT_APP_VERSION_CONFLICT);
                }
                return toDetail(app, content);
            });
        } catch (TransactionException error) {
            // 连接在提交确认前断开时，事务可能已提交；保留对象，避免破坏已发布版本。
            log.error("智能应用事务结束异常，保留对象供核查，appId={}, version={}, objectKey={}, errorType={}",
                    id, versionId, objectKey, error.getClass().getSimpleName());
            throw error;
        } catch (RuntimeException error) {
            try {
                packageStore.delete(objectKey);
            } catch (RuntimeException cleanupError) {
                error.addSuppressed(cleanupError);
                log.warn("智能应用新版本提交失败且对象清理失败，appId={}, version={}", id, versionId, cleanupError);
            }
            throw error;
        }
    }

    /**
     * 工具入口也进行完整校验，避免绕过 MVC 的 Bean Validation。
     */
    private AgentAppPackageDo validatePackage(AgentAppSaveDto request) {
        if (request == null || !validator.validate(request).isEmpty()
                || request.getPermissions().stream().anyMatch(permission -> !SUPPORTED_PERMISSIONS.contains(permission))) {
            throw new XException(ResponseCodeEnum.AGENT_APP_PACKAGE_INVALID);
        }
        return new AgentAppPackageDo().setSdkVersion(1).setHtml(request.getHtml()).setCss(request.getCss())
                .setSource(request.getSource()).setPermissions(request.getPermissions().stream().distinct().sorted().toList());
    }

    /**
     * 按用户与有效状态查询，统一隐藏不存在和非本人的应用。
     */
    private AgentApp requireOwned(String userId, String id) {
        AgentApp app = appIService.lambdaQuery().eq(AgentApp::getOwnerUserId, userId)
                .eq(AgentApp::getId, id).eq(AgentApp::getDeleted, false).one();
        if (app == null) {
            throw new XException(ResponseCodeEnum.AGENT_APP_NOT_FOUND);
        }
        return app;
    }

    /**
     * 对修改和删除串行加锁，删除重试允许读取已经隐藏的目录。
     */
    private AgentApp requireLocked(String userId, String id, boolean includeDeleted) {
        AgentApp app = appIService.findForUpdate(userId, id);
        if (app == null || (!includeDeleted && Boolean.TRUE.equals(app.getDeleted()))) {
            throw new XException(ResponseCodeEnum.AGENT_APP_NOT_FOUND);
        }
        return app;
    }

    /**
     * 版本必须属于当前已授权应用，不能按对象键或其他应用的版本读取源码。
     */
    private AgentAppVersion requireVersion(String id, String version) {
        AgentAppVersion result = versionIService.lambdaQuery().eq(AgentAppVersion::getAppId, id)
                .eq(AgentAppVersion::getId, version).one();
        if (result == null) {
            throw new XException(ResponseCodeEnum.AGENT_APP_NOT_FOUND);
        }
        return result;
    }

    /**
     * 所有版本切换都校验调用方读取的基线，防止静默覆盖并发修改。
     */
    private void requireBaseVersion(AgentApp app, String baseVersion) {
        if (baseVersion == null || !Objects.equals(app.getCurrentVersion(), baseVersion)) {
            throw new XException(ResponseCodeEnum.AGENT_APP_VERSION_CONFLICT);
        }
    }

    /**
     * 只读取数据库引用的私有对象，并验证存储包仍遵循当前宿主协议。
     */
    private AgentAppPackageDo readPackage(AgentAppVersion version) {
        byte[] bytes = packageStore.read(version.getObjectKey(), version.getSizeBytes(), version.getSha256());
        try {
            AgentAppPackageDo content = JsonUtil.OBJECT_MAPPER.readValue(bytes, AgentAppPackageDo.class);
            if (content == null || content.getSdkVersion() != 1) {
                throw new XException(ResponseCodeEnum.AGENT_APP_STORAGE_ERROR);
            }
            validatePackage(new AgentAppSaveDto().setName(version.getName()).setDescription(version.getDescription())
                    .setHtml(content.getHtml()).setCss(content.getCss()).setSource(content.getSource())
                    .setPermissions(content.getPermissions()));
            return content;
        } catch (RuntimeException error) {
            log.warn("智能应用包无法读取，version={}", version.getId(), error);
            throw new XException(ResponseCodeEnum.AGENT_APP_STORAGE_ERROR);
        }
    }

    /**
     * 目录仅暴露应用元信息，不暴露对象键和源码。
     */
    private AgentAppDo toDirectory(AgentApp app) {
        return new AgentAppDo().setId(app.getId()).setName(app.getName()).setDescription(app.getDescription())
                .setCurrentVersion(app.getCurrentVersion());
    }

    /**
     * 组合应用元信息和已经完整性校验的包内容。
     */
    private AgentAppDetailDo toDetail(AgentApp app, AgentAppPackageDo content) {
        AgentAppDetailDo detail = new AgentAppDetailDo().setContent(content);
        detail.setId(app.getId()).setName(app.getName()).setDescription(app.getDescription())
                .setCurrentVersion(app.getCurrentVersion());
        return detail;
    }
}
