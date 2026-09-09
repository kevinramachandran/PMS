package org.example.repository;

import org.example.entity.CarlexProcessConfirmation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CarlexProcessConfirmationRepository extends JpaRepository<CarlexProcessConfirmation, Long> {
	List<CarlexProcessConfirmation> findAllByOrderByDateOfGwProcessConfirmationConductedDescIdDesc();
}
