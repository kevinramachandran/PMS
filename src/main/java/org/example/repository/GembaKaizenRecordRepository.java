package org.example.repository;

import org.example.entity.GembaKaizenRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface GembaKaizenRecordRepository extends JpaRepository<GembaKaizenRecord, Long> {

    List<GembaKaizenRecord> findAllByOrderByGembaKaizenGenerationDateDescIdDesc();
}
