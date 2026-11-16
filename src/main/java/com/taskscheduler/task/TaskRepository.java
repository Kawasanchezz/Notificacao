package com.taskscheduler.task;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface TaskRepository extends MongoRepository<Task, String> {
    Page<Task> findByUserId(Long userId, Pageable pageable);
    Page<Task> findByUserIdAndStatus(Long userId, Task.Status status, Pageable pageable);
    long countByUserId(Long userId);
    Optional<Task> findByIdAndUserId(String id, Long userId);
}
