package org.example.repository;

import org.example.entity.SyncPushRequest;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SyncPushRequestRepository extends JpaRepository<SyncPushRequest, String> { }
