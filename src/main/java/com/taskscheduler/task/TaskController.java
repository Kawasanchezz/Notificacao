package com.taskscheduler.task;

import com.taskscheduler.exception.Exceptions.ConflictException;
import com.taskscheduler.exception.Exceptions.NotFoundException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Tasks")
@Validated
public class TaskController {

    private static final long MAX_TASKS_PER_USER = 1000;

    public record CreateTaskRequest(
            @NotBlank @Size(max = 200) String title,
            @Size(max = 2000) String description,
            Instant scheduledFor) {}

    public record UpdateStatusRequest(@NotNull Task.Status status) {}

    public record TaskResponse(String id, String title, String description, Task.Status status,
                               Instant scheduledFor, Instant createdAt, Instant updatedAt) {
        static TaskResponse from(Task t) {
            return new TaskResponse(t.getId(), t.getTitle(), t.getDescription(), t.getStatus(),
                    t.getScheduledFor(), t.getCreatedAt(), t.getUpdatedAt());
        }
    }

    public record TaskPage(java.util.List<TaskResponse> items, int page, int size, long totalItems, int totalPages) {}

    private final TaskRepository tasks;

    public TaskController(TaskRepository tasks) { this.tasks = tasks; }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a task")
    public TaskResponse create(@AuthenticationPrincipal Long userId, @Valid @RequestBody CreateTaskRequest req) {
        if (tasks.countByUserId(userId) >= MAX_TASKS_PER_USER) {
            throw new ConflictException("Task limit reached (" + MAX_TASKS_PER_USER + ")");
        }
        Task task = new Task();
        task.setUserId(userId);
        task.setTitle(req.title().trim());
        task.setDescription(req.description());
        task.setScheduledFor(req.scheduledFor());
        task.setCreatedAt(now());
        task.setUpdatedAt(task.getCreatedAt());
        return TaskResponse.from(tasks.save(task));
    }

    @GetMapping
    @Operation(summary = "List my tasks (paged, newest first), optionally filtered by status")
    public TaskPage list(@AuthenticationPrincipal Long userId,
                         @RequestParam(required = false) Task.Status status,
                         @RequestParam(defaultValue = "0") @Min(0) int page,
                         @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        var pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<Task> result = status == null
                ? tasks.findByUserId(userId, pageable)
                : tasks.findByUserIdAndStatus(userId, status, pageable);
        return new TaskPage(result.map(TaskResponse::from).getContent(), page, size,
                result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get task details")
    public TaskResponse get(@AuthenticationPrincipal Long userId, @PathVariable String id) {
        return TaskResponse.from(find(userId, id));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Change task status")
    public TaskResponse updateStatus(@AuthenticationPrincipal Long userId, @PathVariable String id,
                                     @Valid @RequestBody UpdateStatusRequest req) {
        Task task = find(userId, id);
        task.setStatus(req.status());
        task.setUpdatedAt(now());
        return TaskResponse.from(tasks.save(task));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Delete a task")
    public void delete(@AuthenticationPrincipal Long userId, @PathVariable String id) {
        tasks.delete(find(userId, id));
    }

    /** MongoDB stores milliseconds; truncating keeps the API response identical to what is persisted. */
    private static Instant now() { return Instant.now().truncatedTo(ChronoUnit.MILLIS); }

    /** Scoped by owner: another user's task is reported as 404, never leaked. */
    private Task find(Long userId, String id) {
        return tasks.findByIdAndUserId(id, userId).orElseThrow(() -> new NotFoundException("Task not found"));
    }
}
