package org.example.repository;

import org.example.entity.Priorities;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface PrioritiesRepository extends JpaRepository<Priorities, Long> {

    List<Priorities> findByDate(LocalDate date);

    List<Priorities> findByTypeAndDate(String type, LocalDate date);

    List<Priorities> findByType(String type);

    void deleteByTypeAndDate(String type, LocalDate date);

}
