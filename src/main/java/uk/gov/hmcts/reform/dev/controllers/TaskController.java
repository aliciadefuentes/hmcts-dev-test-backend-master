package uk.gov.hmcts.reform.dev.controllers;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.hmcts.reform.dev.exceptions.TaskNotFoundException;
import uk.gov.hmcts.reform.dev.models.Task;
import uk.gov.hmcts.reform.dev.services.TaskService;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/tasks")
@CrossOrigin(origins = "*")
@Tag(name = "Task Management", description = "API for managing caseworker tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;

    @Operation(summary = "Create a new task", description = "Creates a new task with the provided details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "201", description = "Task created successfully",
            content = @Content(schema = @Schema(implementation = Task.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "500", description = "Internal server error")
    })
    @PostMapping(consumes = "application/json", produces = "application/json")
    public ResponseEntity<Task> createTask(@Valid @RequestBody CreateTaskRequest request) {
        Task newTask = taskService.createTask(
            request.getTitle(),
            request.getDescription(),
            request.getStatus(),
            request.getDueDate()
        );
        return ResponseEntity.status(HttpStatus.CREATED).body(newTask);
    }

    @Operation(summary = "Get all tasks", description = "Retrieves all tasks with pagination, optionally filtered by "
        + "search term and status")
    @ApiResponse(responseCode = "200", description = "Tasks retrieved successfully")
    @GetMapping(produces = "application/json")
    public ResponseEntity<TaskPageResponse> getAllTasks(
        @Parameter(description = "Search term to filter tasks by case number, title, or description")
        @RequestParam(required = false) String search,

        @Parameter(description = "Filter tasks by status")
        @RequestParam(required = false) String status,

        @Parameter(description = "Page number (1-based)", example = "1")
        @RequestParam(defaultValue = "1") int page,

        @Parameter(description = "Number of items per page", example = "10")
        @RequestParam(defaultValue = "10") int pageSize) {

        if (page < 1) {
            page = 1;
        }
        if (pageSize < 1 || pageSize > 100) {
            pageSize = 10;
        }

        int offset = (page - 1) * pageSize;

        List<Task> tasks;
        long totalTasks;

        tasks = taskService.searchTasks(search, status, offset, pageSize);
        totalTasks = taskService.countFilteredTasks(search, status);

        int totalPages = (int) Math.ceil((double) totalTasks / pageSize);

        TaskPageResponse response = new TaskPageResponse();
        response.setTasks(tasks);
        response.setTotalTasks(totalTasks);
        response.setTotalPages(totalPages);
        response.setCurrentPage(page);
        response.setPageSize(pageSize);

        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Get task by ID", description = "Retrieves a specific task by its ID")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Task found",
            content = @Content(schema = @Schema(implementation = Task.class))),
        @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @GetMapping(value = "/{id}", produces = "application/json")
    public ResponseEntity<Task> getTaskById(
        @Parameter(description = "Task ID") @PathVariable int id) {
        Task task = taskService.getTaskById(id);
        return ResponseEntity.ok(task);
    }

    @Operation(summary = "Update task status", description = "Updates the status of a specific task")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Task status updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid status"),
        @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PutMapping(value = "/{id}/status", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Task> updateTaskStatus(
        @Parameter(description = "Task ID") @PathVariable int id,
        @Valid @RequestBody UpdateStatusRequest request) throws TaskNotFoundException{
        Task updatedTask = taskService.updateTaskStatus(id, request.getStatus());
        return ResponseEntity.ok(updatedTask);
    }

    @Operation(summary = "Update task", description = "Updates task details")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Task updated successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid request data"),
        @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @PutMapping(value = "/{id}", consumes = "application/json", produces = "application/json")
    public ResponseEntity<Task> updateTask(
        @Parameter(description = "Task ID") @PathVariable int id,
        @Valid @RequestBody UpdateTaskRequest request) throws TaskNotFoundException {
        Task updatedTask = taskService.updateTask(
            id,
            request.getTitle(),
            request.getDescription(),
            request.getStatus(),
            request.getDueDate()
        );
        return ResponseEntity.ok(updatedTask);
    }

    @Operation(summary = "Delete task", description = "Deletes a specific task")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "204", description = "Task deleted successfully"),
        @ApiResponse(responseCode = "404", description = "Task not found")
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteTask(
        @Parameter(description = "Task ID") @PathVariable int id) {
        taskService.deleteTask(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get tasks by status", description = "Retrieves tasks filtered by status")
    @ApiResponse(responseCode = "200", description = "Tasks retrieved successfully")
    @GetMapping(value = "/status/{status}", produces = "application/json")
    public ResponseEntity<List<Task>> getTasksByStatus(
        @Parameter(description = "Task status") @PathVariable String status) {
        List<Task> tasks = taskService.getTasksByStatus(status);
        return ResponseEntity.ok(tasks);
    }

    @Operation(summary = "Get overdue tasks", description = "Retrieves all overdue tasks")
    @ApiResponse(responseCode = "200", description = "Overdue tasks retrieved successfully")
    @GetMapping(value = "/overdue", produces = "application/json")
    public ResponseEntity<List<Task>> getOverdueTasks() {
        List<Task> tasks = taskService.getOverdueTasks();
        return ResponseEntity.ok(tasks);
    }

    @Operation(summary = "Get task statistics", description = "Retrieves task statistics including counts by status")
    @ApiResponse(responseCode = "200", description = "Statistics retrieved successfully")
    @GetMapping(value = "/statistics", produces = "application/json")
    public ResponseEntity<Map<String, Long>> getTaskStatistics() {
        Map<String, Long> stats = taskService.getTaskStatistics();
        return ResponseEntity.ok(stats);
    }

    @Operation(summary = "Get valid statuses", description = "Retrieves list of valid task statuses")
    @ApiResponse(responseCode = "200", description = "Valid statuses retrieved successfully")
    @GetMapping(value = "/statuses", produces = "application/json")
    public ResponseEntity<List<String>> getValidStatuses() {
        List<String> statuses = TaskService.Status.getValidStatuses();
        return ResponseEntity.ok(statuses);
    }

    @Getter
    @Setter
    public static class CreateTaskRequest {
        @NotBlank(message = "Title is required")
        @Size(max = 255, message = "Title must not exceed 255 characters")
        private String title;

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        private String description;

        private String status;

        @NotNull(message = "Due date is required")
        private LocalDateTime dueDate;

        // Constructors
        public CreateTaskRequest() {
        }

    }

    @Setter
    @Getter
    public static class UpdateStatusRequest {
        @NotBlank(message = "Status is required")
        private String status;

        public UpdateStatusRequest() {
        }

    }

    @Setter
    @Getter
    public static class UpdateTaskRequest {
        @Size(max = 255, message = "Title must not exceed 255 characters")
        private String title;

        @Size(max = 1000, message = "Description must not exceed 1000 characters")
        private String description;

        private String status;
        private LocalDateTime dueDate;

        public UpdateTaskRequest() {
        }

    }

    @Getter
    @Setter
    public static class TaskPageResponse {
        private List<Task> tasks;
        private long totalTasks;
        private int totalPages;
        private int currentPage;
        private int pageSize;

    }

    @ExceptionHandler(TaskNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<String> handleTaskNotFound(TaskNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ex.getMessage());
    }

}
