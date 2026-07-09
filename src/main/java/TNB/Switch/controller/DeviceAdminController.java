package TNB.Switch.controller;

import TNB.Switch.dto.request.DeviceRegistrationRequest;
import TNB.Switch.dto.response.DeviceRegistrationResponse;
import TNB.Switch.entity.Operator;
import TNB.Switch.service.DeviceAdminService;
import TNB.Switch.service.DeviceQueueService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * =====================================================================================
 * ROUTAGE API : PROVISIONNING ET AJUSTEMENTS MANUELS D'INFRASTRUCTURE
 * =====================================================================================
 */
@RestController
@RequestMapping("/api/v1/admin/devices")
public class DeviceAdminController {

    private final DeviceAdminService deviceAdminService;
    private final DeviceQueueService deviceQueueService;

    public DeviceAdminController(DeviceAdminService deviceAdminService, DeviceQueueService deviceQueueService) {
        this.deviceAdminService = deviceAdminService;
        this.deviceQueueService = deviceQueueService;
    }

    /**
     * WORKFLOW : Enregistrer une nouvelle passerelle physique (Génère QR Code Data)
     * POST /api/v1/admin/devices
     */
    @PostMapping
    public ResponseEntity<DeviceRegistrationResponse> registerDevice(
            @Valid @RequestBody DeviceRegistrationRequest request) {
        DeviceRegistrationResponse response = deviceAdminService.provisionNewDevice(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    /**
     * WORKFLOW : Forcer manuellement le solde d'un portefeuille Mobile Money (MTN / ORANGE)
     * PUT /api/v1/admin/devices/{deviceId}/balance/momo?operator=MTN&amount=500000
     */
    @PutMapping("/{deviceId}/balance/momo")
    public ResponseEntity<Void> forceMomoBalance(
            @PathVariable UUID deviceId,
            @RequestParam Operator operator,
            @RequestParam BigDecimal amount) {
        deviceQueueService.forceExactMomoBalance(deviceId, operator, amount);
        return ResponseEntity.noContent().build();
    }

    /**
     * WORKFLOW : Forcer manuellement le solde d'une ligne de crédit Airtime (MTN / ORANGE / CAMTEL)
     * PUT /api/v1/admin/devices/{deviceId}/balance/airtime?operator=CAMTEL&amount=75000
     */
    @PutMapping("/{deviceId}/balance/airtime")
    public ResponseEntity<Void> forceAirtimeBalance(
            @PathVariable UUID deviceId,
            @RequestParam Operator operator,
            @RequestParam BigDecimal amount) {
        deviceQueueService.forceExactAirtimeBalance(deviceId, operator, amount);
        return ResponseEntity.noContent().build();
    }
}