package com.taskscheduler.task;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document("tasks")
@CompoundIndex(def = "{'userId': 1, 'status': 1}")
@Getter
@Setter
@NoArgsConstructor
public class Task {

    public enum Status { PENDING, IN_PROGRESS, DONE, CANCELED }

    @Id
    private String id;
    private Long userId;
    private String title;
    private String description;
    private Status status = Status.PENDING;
    private Instant scheduledFor;
    private Instant createdAt;
    private Instant updatedAt;
}
