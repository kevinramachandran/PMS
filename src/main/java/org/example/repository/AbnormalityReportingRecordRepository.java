package org.example.repository;

import org.example.entity.AbnormalityReportingRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AbnormalityReportingRecordRepository extends JpaRepository<AbnormalityReportingRecord, Long> {

    List<AbnormalityReportingRecord> findAllByOrderByDateRaisedDescIdDesc();
}
