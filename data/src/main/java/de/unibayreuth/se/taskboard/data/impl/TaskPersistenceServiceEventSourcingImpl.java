package de.unibayreuth.se.taskboard.data.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.unibayreuth.se.taskboard.business.domain.Task;
import de.unibayreuth.se.taskboard.business.domain.TaskStatus;
import de.unibayreuth.se.taskboard.business.exceptions.TaskNotFoundException;
import de.unibayreuth.se.taskboard.business.ports.TaskPersistenceService;
import de.unibayreuth.se.taskboard.data.mapper.TaskEntityMapper;
import de.unibayreuth.se.taskboard.data.persistence.EventEntity;
import de.unibayreuth.se.taskboard.data.persistence.EventRepository;
import de.unibayreuth.se.taskboard.data.persistence.TaskEntity;
import de.unibayreuth.se.taskboard.data.persistence.TaskRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Primary;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Event-sourcing-based implementation of the task persistence service that the business layer provides as a port.
 */
@Service
@RequiredArgsConstructor
@Primary
public class TaskPersistenceServiceEventSourcingImpl implements TaskPersistenceService {
    private final TaskRepository taskRepository;
    private final TaskEntityMapper taskEntityMapper;
    private final EventRepository eventRepository;
    private final ObjectMapper objectMapper;

    @Override
    public void clear() {
        taskRepository.findAll()
                .forEach(taskEntity -> eventRepository.saveAndFlush(
                        EventEntity.deleteEventOf(taskEntityMapper.fromEntity(taskEntity), null))
                );
        if (taskRepository.count() != 0) {
            throw new IllegalStateException("Tasks not successfully deleted.");
        }
    }

    @NonNull
    @Override
    public List<Task> getAll() {
        return taskRepository.findAll().stream()
                .map(taskEntityMapper::fromEntity)
                .toList();
    }

    @NonNull
    @Override
    public Optional<Task> getById(@NonNull UUID id) {
        return taskRepository.findById(id)
                .map(taskEntityMapper::fromEntity);
    }

    @NonNull
    @Override
    public List<Task> getByStatus(@NonNull TaskStatus status) {
        return taskRepository.findByStatus(status).stream()
                .map(taskEntityMapper::fromEntity)
                .toList();
    }

    @NonNull
    @Override
    public List<Task> getByAssignee(@NonNull UUID userId) {
        return taskRepository.findByAssigneeId(userId).stream()
                .map(taskEntityMapper::fromEntity)
                .toList();
    }

    @NonNull
    @Override
    public Task upsert(@NonNull Task task) throws TaskNotFoundException {
        /*
        The upsert method in the TaskPersistenceServiceEventSourcingImpl class handles both the creation and updating of tasks.
        If the task ID is null, it creates a new task by generating a new UUID, saving an insert event, and returning the newly created task.
        If the task ID is not null, it updates the existing task by finding it in the repository, updating its fields, saving an update event, and returning the updated task.
        In both cases, it uses the EventRepository to log the changes and the TaskRepository to persist the task data.
        */

        if (task.getId() == null) {
            // create a new task
            task.setId(UUID.randomUUID());
            eventRepository.saveAndFlush(EventEntity.insertEventOf(task, task.getId(), objectMapper));
            return task;
        } else {
            // update existing task
            TaskEntity taskEntity = taskRepository.findById(task.getId()).orElseThrow(
                    () -> new TaskNotFoundException("Task with id " + task.getId() + " does not exist")
            );

            Task updatedTask = taskEntityMapper.fromEntity(taskEntity);

            updatedTask.setUpdatedAt(task.getUpdatedAt());
            updatedTask.setTitle(task.getTitle());
            updatedTask.setDescription(task.getDescription());
            updatedTask.setStatus(task.getStatus());
            updatedTask.setAssigneeId(task.getAssigneeId());

            eventRepository.saveAndFlush(EventEntity.updateEventOf(updatedTask, task.getId(), objectMapper));

            return updatedTask;
        }
    }

    @Override
    public void delete(@NonNull UUID id) throws TaskNotFoundException {
        /*
        The delete method in the TaskPersistenceServiceEventSourcingImpl class performs the following actions:
        Attempts to find a Task by its ID in the taskRepository.
        If the task is not found, it throws a TaskNotFoundException.
        If the task is found, it logs a delete event using the eventRepository.
        Checks if the task still exists in the taskRepository.
        If the task still exists, it throws an IllegalStateException indicating the task was not successfully deleted.
        */

        TaskEntity taskEntity = taskRepository.findById(id).orElseThrow(
                () -> new TaskNotFoundException("Task with ID " + id + " does not exist.")
        );

        Task deletedTask = taskEntityMapper.fromEntity(taskEntity);

        eventRepository.saveAndFlush(EventEntity.deleteEventOf(deletedTask, id));

        // check successful deletion
        if (taskRepository.existsById(id)) {
            throw new IllegalStateException("Deletion of task with ID " + id + " failed.");
        }
    }
}
