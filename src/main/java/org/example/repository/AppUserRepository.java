package org.example.repository;

import org.example.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByUsernameIgnoreCase(String username);

    Optional<AppUser> findByEmailIgnoreCase(String email);

    boolean existsByUsernameIgnoreCase(String username);

    boolean existsByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCaseAndIdNot(String email, Long id);

    boolean existsByEmployeeIdIgnoreCase(String employeeId);

    boolean existsByEmployeeIdIgnoreCaseAndIdNot(String employeeId, Long id);

    @Query("SELECT DISTINCT u.department FROM AppUser u WHERE u.department IS NOT NULL AND TRIM(u.department) <> '' ORDER BY u.department")
    List<String> findDistinctDepartments();

    @Query("SELECT DISTINCT u.area FROM AppUser u WHERE u.area IS NOT NULL AND TRIM(u.area) <> '' ORDER BY u.area")
    List<String> findDistinctAreas();

    @Query("SELECT DISTINCT u.plant FROM AppUser u WHERE u.plant IS NOT NULL AND TRIM(u.plant) <> '' ORDER BY u.plant")
    List<String> findDistinctPlants();

    @Query("SELECT DISTINCT u.designation FROM AppUser u WHERE u.designation IS NOT NULL AND TRIM(u.designation) <> '' ORDER BY u.designation")
    List<String> findDistinctDesignations();
}
