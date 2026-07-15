package TNB.Switch.repository;

import TNB.Switch.entity.Transaction;
import TNB.Switch.entity.TransactionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    // 1. Sécurité : Vérification de la clé d'idempotence pour bloquer les doublons clients
    Optional<Transaction> findByIdempotencyKey(String idempotencyKey);

    // 2. Performance : Recherche ciblée indexée pour la réconciliation de la collecte (Phase 1)
    Optional<Transaction> findByStatusAndSenderPhoneAndAmount(TransactionStatus status, String senderPhone, BigDecimal amount);

    // 3. Performance : Recherche ciblée indexée pour la réconciliation du virement (Phase 2)
    Optional<Transaction> findByStatusAndRecipientPhoneAndAmount(TransactionStatus status, String recipientPhone, BigDecimal amount);

    /**
     * 4. Concurrence : Algorithme du Load Balancer FIFO (First In, First Out) hautement concurrent.
     * Trouve la plus ancienne transaction éligible selon l'opérateur et l'état requis,
     * la verrouille pour ce thread d'exécution, et ignore les lignes déjà verrouillées par d'autres puces.
     */
    @Query(value = "SELECT * FROM transactions t " +
            "WHERE t.status = :#{#status.name()} " +
            "AND t.operator_destination = :operator " +
            "ORDER BY t.sequence_number ASC " +
            "LIMIT 1 " +
            "FOR UPDATE SKIP LOCKED", nativeQuery = true)
    Optional<Transaction> findOldestAvailableJob(
            @Param("status") TransactionStatus status,
            @Param("operator") String operator
    );
}