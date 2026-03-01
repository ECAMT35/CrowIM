package com.ecamt35.messageservice.service;

import cn.hutool.core.lang.Snowflake;
import com.ecamt35.messageservice.constant.PacketTypeConstant;
import com.ecamt35.messageservice.model.bo.RelationPushBo;
import com.ecamt35.messageservice.model.vo.RelationEventVo;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;

@Service
@RequiredArgsConstructor
public class RelationEventPublisher {

    private static final int EVENT_BATCH_SIZE = 200;

    private final Snowflake snowflake;
    private final RelationDeliveryService relationDeliveryService;
    private final ExecutorService virtualExecutor;

    /**
     * 发送关系域事件：事务内延迟到提交后发送，非事务内立即发送。
     */
    public void emitEvent(Set<Long> userIds, String eventType, Object data) {
        if (userIds == null || userIds.isEmpty()) return;
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            Set<Long> users = new HashSet<>(userIds);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    asyncEmit(users, eventType, data);
                }
            });
            return;
        }
        asyncEmit(userIds, eventType, data);
    }

    /**
     * 将事件投递异步化，避免业务线程被大群广播阻塞。
     */
    private void asyncEmit(Set<Long> userIds, String eventType, Object data) {
        Set<Long> users = new HashSet<>(userIds);
        virtualExecutor.execute(() -> doEmitEvent(users, eventType, data));
    }

    private void doEmitEvent(Set<Long> userIds, String eventType, Object data) {
        List<Long> users = new ArrayList<>(userIds.size());
        for (Long uid : userIds) {
            if (uid != null) {
                users.add(uid);
            }
        }
        if (users.isEmpty()) return;

        RelationEventVo eventVo = new RelationEventVo(eventType, snowflake.nextId(), data);
        for (int i = 0; i < users.size(); i += EVENT_BATCH_SIZE) {
            int end = Math.min(i + EVENT_BATCH_SIZE, users.size());
            for (int idx = i; idx < end; idx++) {
                relationDeliveryService.deliverToUserDevices(
                        new RelationPushBo(users.get(idx), PacketTypeConstant.SERVER_RELATION_EVENT, eventVo, null)
                );
            }
        }
    }
}
