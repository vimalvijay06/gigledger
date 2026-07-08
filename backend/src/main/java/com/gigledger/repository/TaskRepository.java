package com.gigledger.repository;

import com.gigledger.entity.Task;
import com.gigledger.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Repository for Task.
 * findByUser returns all tasks belonging to the authenticated user,
 * ordered newest first so the frontend table shows recent work at the top.
 */
public interface TaskRepository extends JpaRepository<Task, UUID> {

    List<Task> findByUserOrderByAcceptedAtDesc(User user);
}
