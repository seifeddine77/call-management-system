package com.pfe.callmgmt.analytics.repository;

import com.pfe.callmgmt.analytics.entity.DailyStat;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DailyStatRepository extends JpaRepository<DailyStat, UUID> {

    Optional<DailyStat> findByDate(LocalDate date);

    List<DailyStat> findByDateBetweenOrderByDateDesc(LocalDate from, LocalDate to);
}
