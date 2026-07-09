package TNB.Switch.controller;

import TNB.Switch.service.ConnectedDeviceSession;
import TNB.Switch.service.DeviceQueueService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * =====================================================================================
 * ROUTAGE API : TOKENS DE ROUTAGE, LOAD BALANCING ET CONTRÔLE SUR LA MEMOIRE VIVE
 * =====================================================================================
 */
@RestController
@RequestMapping("/api/v1/admin/fleet-queue")
public class DeviceQueueController {

    private final DeviceQueueService deviceQueueService;

    public DeviceQueueController(DeviceQueueService deviceQueueService) {
        this.deviceQueueService = deviceQueueService;
    }

    /**
     * WORKFLOW : Obtenir la cartographie complète de l'état des sessions de la flotte en ligne
     * GET /api/v1/admin/fleet-queue/live
     */
    @GetMapping("/live")
    public ResponseEntity<Map<UUID, ConnectedDeviceSession>> getLiveSessions() {
        Map<UUID, ConnectedDeviceSession> sessions = deviceQueueService.getLiveSessions();
        return ResponseEntity.ok(sessions);
    }

    /**
     * WORKFLOW : Isoler gracieusement un smartphone actif (Mise en pause)
     * POST /api/v1/admin/fleet-queue/{deviceId}/pause
     */
    @PostMapping("/{deviceId}/pause")
    public ResponseEntity<Void> pauseDevice(@PathVariable UUID deviceId) {
        deviceQueueService.requestPauseDevice(deviceId);
        return ResponseEntity.noContent().build();
    }

    /**
     * WORKFLOW : Réintégrer un smartphone suspendu dans la distribution automatique (Reprise)
     * POST /api/v1/admin/fleet-queue/{deviceId}/resume
     */
    @PostMapping("/{deviceId}/resume")
    public ResponseEntity<Void> resumeDevice(@PathVariable UUID deviceId) {
        deviceQueueService.resumeDevice(deviceId);
        return ResponseEntity.noContent().build();
    }
}