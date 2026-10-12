package org.example.repository;

import org.example.entity.SyncRecordMapping;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SyncRecordMappingRepository extends JpaRepository<SyncRecordMapping, Long> {
    Optional<SyncRecordMapping> findByPeerInstanceIdAndDatasetKeyAndPeerRecordId(
            String peerInstanceId, String datasetKey, Long peerRecordId);
    Optional<SyncRecordMapping> findByPeerInstanceIdAndDatasetKeyAndLocalRecordId(
            String peerInstanceId, String datasetKey, Long localRecordId);
    List<SyncRecordMapping> findByPeerInstanceIdAndDatasetKey(String peerInstanceId, String datasetKey);
}
