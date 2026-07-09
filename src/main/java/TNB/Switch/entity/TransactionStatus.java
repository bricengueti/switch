package TNB.Switch.entity;

public enum TransactionStatus {
    INITIATED,           // Transaction créée par le client, en attente de traitement
    COLLECT_PROCESSING,  // Assignée à un device, USSD de débit envoyé au client
    COLLECT_DONE,        // Le client a saisi son PIN, le SMS de dépôt est validé par le device
    TRANSFER_PROCESSING, // Le device exécute l'USSD de crédit/transfert vers le bénéficiaire
    SUCCESS,             // Le bénéficiaire a reçu ses fonds, transaction clôturée
    SUSPENDED,           // STRATÉGIE 2 : Encaissée mais bloquée (Flotte indisponible / Mise en attente SAV)
    FAILED               // Échec critique définitif survenu lors de la collecte ou du transfert
}