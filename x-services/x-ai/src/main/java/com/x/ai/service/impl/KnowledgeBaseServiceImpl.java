package com.x.ai.service.impl;

import com.x.ai.common.domain.KnowledgeBaseDo;
import com.x.ai.common.dto.KnowledgeBaseDto;
import com.x.ai.db.entity.KnowledgeBase;
import com.x.ai.db.service.KnowledgeBaseIService;
import com.x.ai.service.KnowledgeBaseService;
import com.x.framework.common.enums.ResponseCodeEnum;
import com.x.framework.common.exception.XException;
import com.x.framework.common.utils.IdUtil;
import com.x.framework.common.utils.UserThreadLocalUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.function.Supplier;

@Slf4j
@Service
@RequiredArgsConstructor
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private final KnowledgeBaseIService bases;

    @Override
    public List<KnowledgeBaseDo> list() {
        String userId = UserThreadLocalUtil.getUserId();
        return storage(() -> {
            bases.ensureDefault(userId);
            return bases.list(userId).stream().map(this::toDomain).toList();
        });
    }

    @Override
    public KnowledgeBaseDo create(KnowledgeBaseDto request) {
        validate(request);
        String userId = UserThreadLocalUtil.getUserId();
        return storage(() -> {
            bases.ensureDefault(userId);
            return toDomain(bases.create(new KnowledgeBase().setOwnerId(userId).setId(IdUtil.fastUUID())
                    .setName(request.getName().strip()).setDescription(description(request))));
        });
    }

    @Override
    public KnowledgeBaseDo update(String id, KnowledgeBaseDto request) {
        validate(request);
        return storage(() -> {
            KnowledgeBase updated = bases.update(UserThreadLocalUtil.getUserId(), id,
                    request.getName().strip(), description(request));
            if (updated == null) throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_BASE_NOT_FOUND);
            return toDomain(updated);
        });
    }

    @Override
    public boolean delete(String id) {
        String userId = UserThreadLocalUtil.getUserId();
        KnowledgeBaseDo base = requireOwned(userId, List.of(id)).getFirst();
        if (base.isDefault()) throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_BASE_DEFAULT_REQUIRED);
        return storage(() -> bases.delete(userId, id));
    }

    @Override
    public List<KnowledgeBaseDo> requireOwned(String userId, List<String> ids) {
        if (ids == null || ids.stream().anyMatch(id -> id == null || id.isBlank() || id.length() > 160)) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_BASE_NOT_FOUND);
        }
        List<String> distinctIds = ids.stream().distinct().toList();
        if (distinctIds.isEmpty()) return List.of();
        return storage(() -> {
            List<KnowledgeBase> owned = bases.findOwned(userId, distinctIds);
            if (owned.size() != distinctIds.size()) {
                throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_BASE_NOT_FOUND);
            }
            return owned.stream().map(this::toDomain).toList();
        });
    }

    @Override
    public List<String> searchableBaseIds(String userId, List<String> selectedIds) {
        return storage(() -> {
            bases.ensureDefault(userId);
            LinkedHashSet<String> ids = new LinkedHashSet<>();
            ids.add("default-" + userId);
            if (selectedIds != null && !selectedIds.isEmpty()) {
                bases.findOwned(userId, selectedIds).stream().map(KnowledgeBase::getId).forEach(ids::add);
            }
            return List.copyOf(ids);
        });
    }

    /**
     * 防止工具或内部调用绕过 Web 输入约束。
     */
    private void validate(KnowledgeBaseDto request) {
        if (request == null || request.getName() == null || request.getName().isBlank()
                || request.getName().length() > 100
                || (request.getDescription() != null && request.getDescription().length() > 500)) {
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_BASE_INVALID);
        }
    }

    /**
     * 统一省略说明的存储语义。
     */
    private String description(KnowledgeBaseDto request) {
        return request.getDescription() == null ? "" : request.getDescription().strip();
    }

    /**
     * 隐藏目录的所有者存储字段。
     */
    private KnowledgeBaseDo toDomain(KnowledgeBase base) {
        return new KnowledgeBaseDo().setId(base.getId()).setName(base.getName())
                .setDescription(base.getDescription()).setDefault(base.isDefault());
    }

    /**
     * 将独立向量库故障转换为统一业务错误。
     */
    private <T> T storage(Supplier<T> operation) {
        try {
            return operation.get();
        } catch (DataAccessException error) {
            log.error("Knowledge base database operation failed", error);
            throw new XException(ResponseCodeEnum.AGENT_KNOWLEDGE_STORAGE_ERROR);
        }
    }
}
