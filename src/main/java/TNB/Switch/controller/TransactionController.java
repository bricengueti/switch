package TNB.Switch.controller;

import TNB.Switch.dto.request.TransactionInitiationRequest;
import TNB.Switch.entity.Transaction;
import TNB.Switch.service.TransactionService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * =====================================================================================
 * ROUTAGE API : CYCLES DE VIE DES FLUX FINANCIERS ET RELANCES SÉCURISÉES
 * =====================================================================================
 * Ce contrôleur expose les points d'accès pour l'orchestration des transactions du Switch.
 * 1. Initiation publique ou applicative d'un ordre de transfert.
 * 2. Point d'action pour la relance humaine/technique des transactions gelées (Failover).
 * =====================================================================================
 */
@RestController
@RequestMapping("/api/v1/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /**
     * WORKFLOW : Initier un nouveau transfert d'argent (Phase 1 : Attente de collecte)
     * POST /api/v1/transactions
     * ---------------------------------------------------------------------------------
     * Consommé par le client final ou le middleware pour enregistrer l'intention de virement.
     */
    @PostMapping
    public ResponseEntity<Transaction> initiateTransaction(
            @Valid @RequestBody TransactionInitiationRequest request) {

        Transaction transaction = transactionService.createAndInitiateTransaction(
                request.senderPhone(),
                request.recipientPhone(),
                request.amount(),
                request.serviceType(),
                request.idempotencyKey()
        );

        return new ResponseEntity<>(transaction, HttpStatus.CREATED);
    }

    /**
     * WORKFLOW : Relancer manuellement une transaction bloquée ou gelée (Failover manuel)
     * POST /api/v1/transactions/{transactionId}/retry
     * ---------------------------------------------------------------------------------
     * Consommé par le Dashboard Admin (Angular) lorsqu'un opérateur ou une SIM est de nouveau
     * approvisionné afin de renvoyer l'ordre de transfert dans la boucle du Load Balancer.
     */
    @PostMapping("/{transactionId}/retry")
    public ResponseEntity<Void> retryTransaction(@PathVariable UUID transactionId) {
        transactionService.retrySuspendedTransaction(transactionId);
        return ResponseEntity.noContent().build();
    }
}