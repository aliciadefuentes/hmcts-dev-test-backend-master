package uk.gov.hmcts.reform.dev.services;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.hmcts.reform.dev.exceptions.TaskNotFoundException;
import uk.gov.hmcts.reform.dev.models.Task;
import uk.gov.hmcts.reform.dev.repositories.TaskRepository;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

@Service
@Transactional
public class TaskService {

    private final TaskRepository taskRepository;
    private final AtomicLong caseNumberCounter = new AtomicLong(1);

    @Autowired
    public TaskService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    public Task createTask(String title, String description, String status, LocalDateTime dueDate) {
        validateCreateTaskRequest(title, status, dueDate);

        String caseNumber = generateUniqueCaseNumber();

        Task newTask = new Task(
            caseNumber,
            title.trim(),
            description != null ? description.trim() : null,
            status != null ? status.toUpperCase() : Status.PENDING.name(),
            dueDate != null ? dueDate : LocalDateTime.now().plusDays(7)
        );

        return taskRepository.save(newTask);
    }

    @Transactional(readOnly = true)
    public List<Task> getAllTasks() {
        return taskRepository.findAllByOrderByDueDateDesc();
    }

    public List<Task> searchTasks(String search, String status, int offset, int pageSize) {

        String normalizedSearch = (search != null && !search.trim().isEmpty()) ? search.trim() : null;
        String normalizedStatus = (status != null && !status.trim().isEmpty()) ? status.trim() : null;

        int page = offset / pageSize;
        Pageable pageable = PageRequest.of(page, pageSize, Sort.by(Sort.Direction.DESC, "dueDate"));

        return taskRepository.searchTasksWithPagination(normalizedSearch, normalizedStatus, offset,
                                                        pageable.getPageSize());
    }

    @Transactional(readOnly = true)
    public List<Task> searchTasks(String search) {
        if (search == null || search.trim().isEmpty()) {
            return getAllTasks();
        }
        return taskRepository.searchTasksWithPagination(search, null, 0, Integer.MAX_VALUE);
    }

    public long countAllTasks() {
        return taskRepository.count();
    }

    public long countFilteredTasks(String search, String status) {

        if ((search == null || search.trim().isEmpty())
            && (status == null || status.trim().isEmpty())) {
            return taskRepository.count();
        }

        return taskRepository.countFilteredTasks(search, status);
    }

    @Transactional(readOnly = true)
    public Task getTaskById(int id) {
        return taskRepository.findById(id)
            .orElseThrow(() -> new TaskNotFoundException("Task with ID " + id + " not found"));
    }

    @Transactional(readOnly = true)
    public Optional<Task> findTaskById(int id) {
        return taskRepository.findById(id);
    }

    public Task updateTaskStatus(int id, String status) {
        validateStatus(status);

        Task task = getTaskById(id);
        task.setStatus(status.toUpperCase());
        return taskRepository.save(task);
    }

    public Task updateTask(int id, String title, String description, String status, LocalDateTime dueDate) {
        Task task = getTaskById(id);

        if (title != null && !title.trim().isEmpty()) {
            task.setTitle(title.trim());
        }
        if (description != null) {
            task.setDescription(description.trim());
        }
        if (status != null && !status.trim().isEmpty()) {
            validateStatus(status);
            task.setStatus(status.toUpperCase());
        }
        if (dueDate != null) {
            task.setDueDate(dueDate);
        }

        return taskRepository.save(task);
    }

    public void deleteTask(int id) {
        if (!taskRepository.existsById(id)) {
            throw new TaskNotFoundException("Task with ID " + id + " not found");
        }
        taskRepository.deleteById(id);
    }

    @Transactional(readOnly = true)
    public List<Task> getTasksByStatus(String status) {
        return taskRepository.findByStatusIgnoreCaseOrderByCreatedDateDesc(status);
    }

    @Transactional(readOnly = true)
    public List<Task> getTasksDueBefore(LocalDateTime date) {
        return taskRepository.findByDueDateBeforeOrderByDueDateAsc(date);
    }

    @Transactional(readOnly = true)
    public List<Task> getOverdueTasks() {
        return taskRepository.findOverdueTasks(LocalDateTime.now());
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getTaskStatistics() {
        Map<String, Long> stats = new HashMap<>();

        long totalCount = taskRepository.count();
        stats.put("total", totalCount);

        List<Object[]> statusCounts = taskRepository.getTaskCountByStatus();
        for (Object[] result : statusCounts) {
            String status = (String) result[0];
            Long count = (Long) result[1];
            stats.put(status.toLowerCase(), count);
        }

        long overdueCount = taskRepository.findOverdueTasks(LocalDateTime.now()).size();
        stats.put("overdue", overdueCount);

        return stats;
    }

    @Transactional(readOnly = true)
    public Task getTaskByCaseNumber(String caseNumber) {
        return taskRepository.findByCaseNumber(caseNumber)
            .orElseThrow(() -> new TaskNotFoundException("Task with case number " + caseNumber + " not found"));
    }

    private void validateCreateTaskRequest(String title, String status, LocalDateTime dueDate) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title is required");
        }
        if (title.trim().length() > 255) {
            throw new IllegalArgumentException("Title must not exceed 255 characters");
        }
        if (status != null) {
            validateStatus(status);
        }
        if (dueDate != null && dueDate.isBefore(LocalDateTime.now())) {
            throw new IllegalArgumentException("Due date cannot be in the past");
        }
    }

    private void validateStatus(String status) {
        if (status == null || status.trim().isEmpty()) {
            throw new IllegalArgumentException("Status cannot be empty");
        }
        if (!Status.isValid(status)) {
            throw new IllegalArgumentException("Invalid status: " + status + ". Valid statuses are: " + String.join(
                ", ", Status.getValidStatuses()
            ));
        }
    }

    private String generateUniqueCaseNumber() {
        String caseNumber;
        do {
            caseNumber = "TASK" + String.format("%06d", caseNumberCounter.getAndIncrement());
        } while (taskRepository.existsByCaseNumber(caseNumber));
        return caseNumber;
    }

    public enum Status {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        CANCELLED,
        ON_HOLD;

        public static boolean isValid(String status) {
            if (status == null) {
                return false;
            }
            try {
                Status.valueOf(status.toUpperCase());
                return true;
            } catch (IllegalArgumentException e) {
                return false;
            }
        }

        public static List<String> getValidStatuses() {
            return Arrays.stream(values())
                .map(Enum::name)
                .collect(Collectors.toList());
        }
    }
}
