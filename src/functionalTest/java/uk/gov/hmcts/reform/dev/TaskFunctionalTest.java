package uk.gov.hmcts.reform.dev;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;
import uk.gov.hmcts.reform.dev.controllers.TaskController;
import uk.gov.hmcts.reform.dev.models.Task;
import uk.gov.hmcts.reform.dev.repositories.TaskRepository;
import uk.gov.hmcts.reform.dev.services.TaskService;

import java.time.LocalDateTime;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.ANY)
@ActiveProfiles("test")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@Transactional
public class TaskFunctionalTest {

    public static final String API_TASKS_V1_PATH = "/api/v1/tasks";
    @Autowired
    private WebApplicationContext webApplicationContext;

    @Autowired
    private TaskRepository taskRepository;

    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        taskRepository.deleteAll();
    }

    @Test
    @Order(1)
    void testCreateTask_Success() throws Exception {
        TaskController.CreateTaskRequest request = new TaskController.CreateTaskRequest();
        request.setTitle("Test Task");
        request.setDescription("Test Description");
        request.setStatus(TaskService.Status.PENDING.name());
        request.setDueDate(LocalDateTime.now().plusDays(7));

        mockMvc.perform(post(API_TASKS_V1_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.title").value("Test Task"))
            .andExpect(jsonPath("$.description").value("Test Description"))
            .andExpect(jsonPath("$.status").value(TaskService.Status.PENDING.name()))
            .andExpect(jsonPath("$.caseNumber").exists())
            .andExpect(jsonPath("$.id").exists());
    }

    @Test
    @Order(2)
    void testCreateTask_ValidationFailure() throws Exception {
        TaskController.CreateTaskRequest request = new TaskController.CreateTaskRequest();
        request.setTitle("");
        request.setDueDate(LocalDateTime.now().plusDays(7));

        mockMvc.perform(post(API_TASKS_V1_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.validationErrors.title").exists());
    }

    @Test
    @Order(3)
    void testGetAllTasks_EmptyList() throws Exception {
        mockMvc.perform(get(API_TASKS_V1_PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.tasks").isEmpty())
            .andExpect(jsonPath("$.totalTasks").value(0))
            .andExpect(jsonPath("$.totalPages").value(0))
            .andExpect(jsonPath("$.currentPage").value(1))
            .andExpect(jsonPath("$.pageSize").value(10));
    }

    @Test
    @Order(4)
    void testGetAllTasks_WithData() throws Exception {
        Task task1 = new Task(
            "TASK000001",
            "Task 1",
            "Description 1",
            TaskService.Status.PENDING.name(),
            LocalDateTime.now().plusDays(1)
        );
        Task task2 = new Task(
            "TASK000002",
            "Task 2",
            "Description 2",
            TaskService.Status.IN_PROGRESS.name(),
            LocalDateTime.now().plusDays(2)
        );
        taskRepository.save(task1);
        taskRepository.save(task2);

        mockMvc.perform(get(API_TASKS_V1_PATH))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.tasks.length()").value(2))
            .andExpect(jsonPath("$.tasks[*].title", containsInAnyOrder("Task 1", "Task 2")));
    }

    @Test
    @Order(5)
    void testGetTaskById_Success() throws Exception {
        Task task = new Task(
            "TASK000001",
            "Test Task",
            "Test Description",
            TaskService.Status.PENDING.name(),
            LocalDateTime.now().plusDays(1)
        );
        Task savedTask = taskRepository.save(task);

        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/" + savedTask.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(savedTask.getId()))
            .andExpect(jsonPath("$.title").value("Test Task"))
            .andExpect(jsonPath("$.caseNumber").value("TASK000001"));
    }

    @Test
    @Order(6)
    void testGetTaskById_NotFound() throws Exception {
        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/999"))
            .andExpect(status().isNotFound());
    }

    @Test
    @Order(7)
    void testUpdateTaskStatus_Success() throws Exception {
        Task task = new Task(
            "TASK000001",
            "Test Task",
            "Test Description",
            TaskService.Status.PENDING.name(),
            LocalDateTime.now().plusDays(1)
        );
        Task savedTask = taskRepository.save(task);

        TaskController.UpdateStatusRequest request = new TaskController.UpdateStatusRequest();
        request.setStatus(TaskService.Status.IN_PROGRESS.name());

        mockMvc.perform(put(API_TASKS_V1_PATH +
                                "/" + savedTask.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(TaskService.Status.IN_PROGRESS.name()));
    }

    @Test
    @Order(8)
    void testUpdateTaskStatus_InvalidStatus() throws Exception {
        Task task = new Task(
            "TASK000001",
            "Test Task",
            "Test Description",
            TaskService.Status.PENDING.name(),
            LocalDateTime.now().plusDays(1)
        );
        Task savedTask = taskRepository.save(task);

        TaskController.UpdateStatusRequest request = new TaskController.UpdateStatusRequest();
        request.setStatus("INVALID_STATUS");

        mockMvc.perform(put(API_TASKS_V1_PATH +
                                "/" + savedTask.getId() + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value(containsString("Invalid status")));
    }

    @Test
    @Order(9)
    void testUpdateTask_Success() throws Exception {
        Task task = new Task(
            "TASK000001",
            "Original Title",
            "Original Description",
            TaskService.Status.PENDING.name(),
            LocalDateTime.now().plusDays(1)
        );

        TaskController.UpdateTaskRequest request = new TaskController.UpdateTaskRequest();
        request.setTitle("Updated Title");
        request.setDescription("Updated Description");
        request.setStatus(TaskService.Status.IN_PROGRESS.name());

        Task savedTask = taskRepository.save(task);

        mockMvc.perform(put(API_TASKS_V1_PATH +
                                "/" + savedTask.getId())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated Title"))
            .andExpect(jsonPath("$.description").value("Updated Description"))
            .andExpect(jsonPath("$.status").value(TaskService.Status.IN_PROGRESS.name()));
    }

    @Test
    @Order(10)
    void testDeleteTask_Success() throws Exception {
        Task task = new Task(
            "TASK000001",
            "Test Task",
            "Test Description",
            TaskService.Status.PENDING.name(),
            LocalDateTime.now().plusDays(1)
        );
        Task savedTask = taskRepository.save(task);

        mockMvc.perform(delete(API_TASKS_V1_PATH +
                                   "/" + savedTask.getId()))
            .andExpect(status().isNoContent());

        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/" + savedTask.getId()))
            .andExpect(status().isNotFound());
    }

    @Test
    @Order(11)
    void testDeleteTask_NotFound() throws Exception {
        mockMvc.perform(delete(API_TASKS_V1_PATH +
                                   "/999"))
            .andExpect(status().isNotFound());
    }

    @Test
    @Order(12)
    void testGetTasksByStatus_Success() throws Exception {
        Task task1 = new Task(
            "TASK000001",
            "Task 1",
            "Description 1",
            TaskService.Status.PENDING.name(),
            LocalDateTime.now().plusDays(1)
        );
        Task task2 = new Task(
            "TASK000002",
            "Task 2",
            "Description 2",
            TaskService.Status.PENDING.name(),
            LocalDateTime.now().plusDays(2)
        );
        Task task3 = new Task(
            "TASK000003",
            "Task 3",
            "Description 3",
            TaskService.Status.COMPLETED.name(),
            LocalDateTime.now().plusDays(3)
        );

        taskRepository.save(task1);
        taskRepository.save(task2);
        taskRepository.save(task3);

        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/status/PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[*].status").value(everyItem(is(TaskService.Status.PENDING.name()))));
    }

    @Test
    @Order(13)
    void testGetOverdueTasks() throws Exception {
        LocalDateTime pastDate = LocalDateTime.now().minusDays(1);
        Task overdueTask = new Task(
            "TASK000001", "Overdue Task", "Description",
            "PENDING", pastDate
        );
        Task futureTask = new Task(
            "TASK000002",
            "Future Task",
            "Description",
            TaskService.Status.PENDING.name(),
            LocalDateTime.now().plusDays(1)
        );

        taskRepository.save(overdueTask);
        taskRepository.save(futureTask);

        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/overdue"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].title").value("Overdue Task"));
    }

    @Test
    @Order(14)
    void testGetTaskStatistics() throws Exception {
        Task task1 = new Task(
            "TASK000001",
            "Task 1",
            "Description 1",
            TaskService.Status.PENDING.name(),
            LocalDateTime.now().plusDays(1)
        );
        Task task2 = new Task(
            "TASK000002",
            "Task 2",
            "Description 2",
            TaskService.Status.IN_PROGRESS.name(),
            LocalDateTime.now().plusDays(2)
        );
        Task task3 = new Task(
            "TASK000003",
            "Task 3",
            "Description 3",
            TaskService.Status.COMPLETED.name(),
            LocalDateTime.now().plusDays(3)
        );

        taskRepository.save(task1);
        taskRepository.save(task2);
        taskRepository.save(task3);

        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/statistics"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.total").value(3))
            .andExpect(jsonPath("$.pending").value(1))
            .andExpect(jsonPath("$.in_progress").value(1))
            .andExpect(jsonPath("$.completed").value(1));
    }

    @Test
    @Order(15)
    void testGetValidStatuses() throws Exception {
        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/statuses"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$").isArray())
            .andExpect(jsonPath("$").value(containsInAnyOrder(
                TaskService.Status.PENDING.name(),
                TaskService.Status.IN_PROGRESS.name(),
                TaskService.Status.COMPLETED.name(),
                TaskService.Status.CANCELLED.name(),
                TaskService.Status.ON_HOLD.name()
            )));
    }

    @Test
    @Order(16)
    void testSearchTasks() throws Exception {
        Task task1 = new Task(
            "TASK000001",
            "Java Development",
            "Spring Boot project",
            TaskService.Status.PENDING.name(),
            LocalDateTime.now().plusDays(1)
        );
        Task task2 = new Task(
            "TASK000002",
            "Testing",
            "Unit tests for Java",
            TaskService.Status.IN_PROGRESS.name(),
            LocalDateTime.now().plusDays(2)
        );
        Task task3 = new Task(
            "TASK000003",
            "Documentation",
            "API documentation",
            TaskService.Status.COMPLETED.name(),
            LocalDateTime.now().plusDays(3)
        );

        taskRepository.save(task1);
        taskRepository.save(task2);
        taskRepository.save(task3);

        mockMvc.perform(get(API_TASKS_V1_PATH).param("search", "Java"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.tasks.length()").value(2));
    }

    @Test
    @Order(17)
    void testCompleteWorkflow() throws Exception {
        TaskController.CreateTaskRequest createRequest = new TaskController.CreateTaskRequest();
        createRequest.setTitle("Workflow Test Task");
        createRequest.setDescription("Testing complete workflow");
        createRequest.setStatus(TaskService.Status.PENDING.name());
        createRequest.setDueDate(LocalDateTime.now().plusDays(7));

        String createResponse = mockMvc.perform(post(API_TASKS_V1_PATH)
                                                    .contentType(MediaType.APPLICATION_JSON)
                                                    .content(objectMapper.writeValueAsString(createRequest)))
            .andExpect(status().isCreated())
            .andReturn().getResponse().getContentAsString();

        Task createdTask = objectMapper.readValue(createResponse, Task.class);
        int taskId = createdTask.getId();

        TaskController.UpdateStatusRequest statusRequest = new TaskController.UpdateStatusRequest();
        statusRequest.setStatus(TaskService.Status.IN_PROGRESS.name());

        mockMvc.perform(put(API_TASKS_V1_PATH +
                                "/" + taskId + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(TaskService.Status.IN_PROGRESS.name()));

        TaskController.UpdateTaskRequest updateRequest = new TaskController.UpdateTaskRequest();
        updateRequest.setTitle("Updated Workflow Test Task");
        updateRequest.setDescription("Updated description");

        mockMvc.perform(put(API_TASKS_V1_PATH +
                                "/" + taskId)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(updateRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.title").value("Updated Workflow Test Task"));

        statusRequest.setStatus("COMPLETED");
        mockMvc.perform(put(API_TASKS_V1_PATH +
                                "/" + taskId + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(statusRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value(TaskService.Status.COMPLETED.name()));

        mockMvc.perform(get(API_TASKS_V1_PATH +
                                "/overdue"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[?(@.id == " + taskId + ")]").doesNotExist());

        mockMvc.perform(delete(API_TASKS_V1_PATH +
                                   "/" + taskId))
            .andExpect(status().isNoContent());
    }

    @Test
    @Order(18)
    void testGetTasksWithPagination() throws Exception {
        for (int i = 1; i <= 10; i++) {
            Task task = new Task(
                "TASK" + String.format("%06d", i), "Task " + i, "Description " + i,
                TaskService.Status.PENDING.name(), LocalDateTime.now().plusDays(i)
            );
            taskRepository.save(task);
        }

        taskRepository.flush();


        mockMvc.perform(get(API_TASKS_V1_PATH).param("page", "1").param("pageSize", "5"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.tasks.length()").value(5))
            .andExpect(jsonPath("$.totalTasks").value(10))
            .andExpect(jsonPath("$.totalPages").value(2));
    }

    @Test
    @Order(19)
    void testSearchTasksWithStatusFilter() throws Exception {
        Task task1 = new Task(
            "TASK000001", "Java Development", "Spring Boot project",
            TaskService.Status.PENDING.name(), LocalDateTime.now().plusDays(1)
        );
        Task task2 = new Task(
            "TASK000002", "Java Testing", "Unit tests for Java",
            TaskService.Status.COMPLETED.name(), LocalDateTime.now().plusDays(2)
        );
        taskRepository.save(task1);
        taskRepository.save(task2);

        mockMvc.perform(get(API_TASKS_V1_PATH).param("search", "Java").param("status", "PENDING"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.tasks.length()").value(1))
            .andExpect(jsonPath("$.tasks[0].status").value(TaskService.Status.PENDING.name()));
    }

    @Test
    @Order(20)
    void testCreateTaskWithPastDueDate() throws Exception {
        TaskController.CreateTaskRequest request = new TaskController.CreateTaskRequest();
        request.setTitle("Past Due Task");
        request.setDescription("Task with past due date");
        request.setStatus(TaskService.Status.PENDING.name());
        request.setDueDate(LocalDateTime.now().minusDays(1));

        mockMvc.perform(post(API_TASKS_V1_PATH)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
            .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.message").value("Due date cannot be in the past"));
    }

    @Test
    @Order(21)
    void testSearchTasksWithEmptyQuery() throws Exception {
        mockMvc.perform(get(API_TASKS_V1_PATH).param("search", ""))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks").isArray());
    }

    @Test
    @Order(22)
    void testSearchTasksWithSpecialCharacters() throws Exception {
        mockMvc.perform(get(API_TASKS_V1_PATH).param("search", "!@#$%^&*()"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tasks").isArray())
            .andExpect(jsonPath("$.tasks").isEmpty());
    }
}
