package org.example.repository;

import org.example.entity.HsDailyCross;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface HsDailyCrossRepository extends JpaRepository<HsDailyCross, Long> {
    List<HsDailyCross> findByYearAndMonth(int year, int month);
    Optional<HsDailyCross> findByYearAndMonthAndDay(int year, int month, int day);
}
