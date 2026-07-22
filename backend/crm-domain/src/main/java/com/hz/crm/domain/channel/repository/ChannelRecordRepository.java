package com.hz.crm.domain.channel.repository;

import com.hz.crm.domain.channel.ChannelRecordEntity;
import com.hz.crm.domain.channel.ChannelStatus;
import com.hz.crm.domain.channel.ChannelType;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChannelRecordRepository extends JpaRepository<ChannelRecordEntity, Long> {

    @Query("select c from ChannelRecordEntity c "
            + "where c.tenantId = :tenantId "
            + "and c.deleted = false "
            + "and (:ownerId is null or c.ownerId = :ownerId) "
            + "and (:status is null or c.status = :status) "
            + "and (:channelType is null or c.channelType = :channelType) "
            + "and (:keyword is null "
            + "or lower(coalesce(c.title, '')) like :keyword "
            + "or lower(coalesce(c.source, '')) like :keyword "
            + "or lower(coalesce(c.contactName, '')) like :keyword "
            + "or lower(coalesce(c.companyName, '')) like :keyword "
            + "or lower(coalesce(c.phone, '')) like :keyword "
            + "or lower(coalesce(c.email, '')) like :keyword)")
    Page<ChannelRecordEntity> search(
            @Param("tenantId") String tenantId,
            @Param("ownerId") Long ownerId,
            @Param("keyword") String keyword,
            @Param("status") ChannelStatus status,
            @Param("channelType") ChannelType channelType,
            Pageable pageable);

    Optional<ChannelRecordEntity> findByIdAndTenantIdAndDeletedFalse(Long id, String tenantId);

    long countByTenantIdAndDeletedFalse(String tenantId);

    long countByTenantIdAndOwnerIdAndDeletedFalse(String tenantId, Long ownerId);

    long countByTenantIdAndStatusAndDeletedFalse(String tenantId, ChannelStatus status);

    long countByTenantIdAndOwnerIdAndStatusAndDeletedFalse(String tenantId, Long ownerId, ChannelStatus status);
}
