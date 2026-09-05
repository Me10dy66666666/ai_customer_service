package com.example.backend.infrastructure.persistence;

import com.example.backend.domain.workorder.model.WorkOrder;
import com.example.backend.domain.workorder.repository.WorkOrderRepository;
import com.example.backend.infrastructure.persistence.mapper.WorkOrderMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;
import java.util.ConcurrentModificationException;

@Repository
@RequiredArgsConstructor
public class WorkOrderRepositoryImpl implements WorkOrderRepository {
    private final WorkOrderMapper mapper;

    private static final String STATUS_PENDING = "pending";
    private static final String STATUS_PROCESSING = "processing";

    @Override
    public WorkOrder save(WorkOrder workOrder) {
        com.example.backend.infrastructure.persistence.entity.WorkOrder po = toEntity(workOrder);
        if (workOrder.getId() == null) {
            mapper.insert(po);
        } else if (mapper.update(po) == 0) {
            throw new ConcurrentModificationException(
                    "Work order was modified concurrently: " + workOrder.getId());
        }
        return toDomain(mapper.selectById(po.getId()));
    }

    @Override public Optional<WorkOrder> findById(Long id) {
        return Optional.ofNullable(mapper.selectById(id)).map(this::toDomain);
    }
    @Override public List<WorkOrder> findByUserId(Long userId) {
        return mapper.findByUserId(userId).stream().map(this::toDomain).toList();
    }
    @Override public List<WorkOrder> findByStatus(String status) {
        return mapper.findByStatus(status).stream().map(this::toDomain).toList();
    }
    @Override public List<WorkOrder> findAll() {
        return mapper.selectAll().stream().map(this::toDomain).toList();
    }
    @Override
    public List<WorkOrder> findUnassigned() {
        return mapper.findUnassigned(STATUS_PENDING).stream().map(this::toDomain).toList();
    }
    @Override
    public List<WorkOrder> findPaginated(int offset, int limit) {
        return mapper.selectAllPaginated(offset, limit).stream().map(this::toDomain).toList();
    }
    @Override
    public int countAll() {
        return mapper.countAll();
    }
    @Override
    public int countActiveByHandlerId(Long handlerId) {
        return mapper.countActiveByHandlerId(handlerId, STATUS_PENDING, STATUS_PROCESSING);
    }
    @Override
    public List<WorkOrder> findByHandlerOrUnassigned(Long handlerId, int offset, int limit) {
        return mapper.selectByHandlerOrUnassigned(handlerId, STATUS_PENDING, offset, limit).stream().map(this::toDomain).toList();
    }

    @Override
    public int countByHandlerOrUnassigned(Long handlerId) {
        return mapper.countByHandlerOrUnassigned(handlerId, STATUS_PENDING);
    }

    @Override
    public boolean claimWorkOrder(Long id, Long handlerId) {
        return mapper.claimWorkOrder(id, handlerId, STATUS_PENDING, STATUS_PROCESSING) > 0;
    }

    @Override
    public List<WorkOrder> findBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return List.of();
        return mapper.findBySessionId(sessionId).stream().map(this::toDomain).toList();
    }

    @Override
    public int countActiveBySessionId(String sessionId) {
        if (sessionId == null || sessionId.isEmpty()) return 0;
        return mapper.countActiveBySessionId(sessionId, STATUS_PENDING, STATUS_PROCESSING);
    }

    private WorkOrder toDomain(com.example.backend.infrastructure.persistence.entity.WorkOrder po) {
        WorkOrder wo = new WorkOrder();
        wo.setId(po.getId()); wo.setUserId(po.getUserId()); wo.setTitle(po.getTitle());
        wo.setDescription(po.getDescription()); wo.setType(po.getType()); wo.setPriority(po.getPriority());
        wo.setStatus(po.getStatus()); wo.setHandlerId(po.getHandlerId()); wo.setResult(po.getResult());
        wo.setTags(po.getTags()); wo.setSummary(po.getSummary()); wo.setSessionId(po.getSessionId());
        wo.setMatchingSkill(po.getMatchingSkill()); wo.setDispatchConfidence(po.getDispatchConfidence());
        wo.setBizTag(po.getBizTag()); wo.setEmotionLevel(po.getEmotionLevel());
        wo.setSlaDeadline(po.getSlaDeadline()); wo.setResponseDeadline(po.getResponseDeadline());
        wo.setRespondedAt(po.getRespondedAt());
        wo.setUserPhone(po.getUserPhone()); wo.setUserNickname(po.getUserNickname());
        wo.setSlaPaused(po.getSlaPaused() != null && po.getSlaPaused() == 1);
        wo.setEffectiveResponseSeconds(po.getEffectiveResponseSeconds());
        wo.setEffectiveResolutionSeconds(po.getEffectiveResolutionSeconds());
        wo.setFirstResponderId(po.getFirstResponderId());
        wo.setResolverId(po.getResolverId());
        wo.setExcludeFromSla(po.getExcludeFromSla() != null && po.getExcludeFromSla() == 1);
        wo.setCreateTime(po.getCreateTime()); wo.setUpdateTime(po.getUpdateTime());
        wo.setLockVersion(po.getLockVersion());
        return wo;
    }

    private com.example.backend.infrastructure.persistence.entity.WorkOrder toEntity(WorkOrder wo) {
        com.example.backend.infrastructure.persistence.entity.WorkOrder po = new com.example.backend.infrastructure.persistence.entity.WorkOrder();
        po.setId(wo.getId()); po.setUserId(wo.getUserId()); po.setTitle(wo.getTitle());
        po.setDescription(wo.getDescription()); po.setType(wo.getType()); po.setPriority(wo.getPriority());
        po.setStatus(wo.getStatus()); po.setHandlerId(wo.getHandlerId()); po.setResult(wo.getResult());
        po.setTags(wo.getTags()); po.setSummary(wo.getSummary()); po.setSessionId(wo.getSessionId());
        po.setMatchingSkill(wo.getMatchingSkill()); po.setDispatchConfidence(wo.getDispatchConfidence());
        po.setBizTag(wo.getBizTag()); po.setEmotionLevel(wo.getEmotionLevel());
        po.setSlaDeadline(wo.getSlaDeadline()); po.setResponseDeadline(wo.getResponseDeadline());
        po.setRespondedAt(wo.getRespondedAt());
        po.setUserPhone(wo.getUserPhone()); po.setUserNickname(wo.getUserNickname());
        po.setSlaPaused(wo.isSlaPaused() ? 1 : 0);
        po.setEffectiveResponseSeconds(wo.getEffectiveResponseSeconds());
        po.setEffectiveResolutionSeconds(wo.getEffectiveResolutionSeconds());
        po.setFirstResponderId(wo.getFirstResponderId());
        po.setResolverId(wo.getResolverId());
        po.setExcludeFromSla(wo.isExcludeFromSla() ? 1 : 0);
        po.setLockVersion(wo.getLockVersion());
        return po;
    }
}
