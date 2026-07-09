package TNB.Switch.dto.response;

import TNB.Switch.entity.TransactionStatus;

import java.util.UUID;

public record ClientNotificationResponse(
        UUID transactionId,
        TransactionStatus status,      // INITIATED, COLLECT_PROCESSING, etc.
        String message      // Message textuel dynamique pour l'utilisateur
) {}