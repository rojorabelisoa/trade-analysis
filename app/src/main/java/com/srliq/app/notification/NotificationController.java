package com.srliq.app.notification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

    private final NotificationRepository notifications;
    private final NotificationService service;

    public NotificationController(NotificationRepository notifications, NotificationService service) {
        this.notifications = notifications;
        this.service = service;
    }

    /** Notifications, de la plus récente à la plus ancienne. */
    @GetMapping
    public NotificationRepository.Page list(@RequestParam(required = false) NotificationKind kind,
                                            @RequestParam(required = false) NotificationStatus status,
                                            @RequestParam(defaultValue = "0") @Min(0) @Max(1_000_000) int page,
                                            @RequestParam(defaultValue = "50") @Min(1) @Max(500) int size) {
        return notifications.search(kind, status, page, size);
    }

    /** Renvoie une notification FAILED (409 sinon). */
    @PostMapping("/{id}/retry")
    public Notification retry(@PathVariable long id) {
        return service.retry(id);
    }
}
