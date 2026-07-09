package TNB.Switch.service;

import TNB.Switch.entity.Device;
import TNB.Switch.entity.FleetAlert;
import TNB.Switch.entity.Operator;
import TNB.Switch.entity.ServiceType;
import TNB.Switch.exception.DeviceDisconnectedException;
import TNB.Switch.repository.DeviceRepository;
import TNB.Switch.repository.FleetAlertRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * =====================================================================================
 * WORKFLOW GLOBAL DU SERVICE : LOAD BALANCER & ETAT CACHE DE LA FLOTTE DE PERIPHERIQUES
 * =====================================================================================
 * Ce service centralise la mémoire vive de l'infrastructure de routage.
 * 1. Il maintient une cartographie temps réel des sessions WebSocket actives via un `ConcurrentHashMap`.
 * 2. Il agit comme un Load Balancer en triant les machines éligibles pour une transaction donnée.
 * 3. Il surveille en continu la santé financière des SIMs (gestion des alertes de rechargement).
 * 4. Il applique un mécanisme d'isolation gracieuse pour désactiver un terminal sans briser les flux.
 * =====================================================================================
 */
@Service
public class DeviceQueueService {

    private static final Logger logger = LoggerFactory.getLogger(DeviceQueueService.class);

    private final Map<UUID, ConnectedDeviceSession> activeSessions = new ConcurrentHashMap<>();
    private final DeviceRepository deviceRepository;
    private final FleetAlertRepository fleetAlertRepository;

    // Verrou dédié à la phase critique "sélection + réservation" d'un device.
    // Le fleet étant de taille modeste (dizaines de téléphones physiques, pas millions),
    // un synchronized ici est largement suffisant et garantit l'atomicité stricte.
    private final Object acquisitionLock = new Object();

    // Pause administrative : distincte de la réservation transactionnelle (isAvailable).
    // Un device en pause reste "disponible" au sens transactionnel mais est exclu du pool.
    private final java.util.Set<UUID> pausedDevices = ConcurrentHashMap.newKeySet();

    private final BigDecimal MIN_MOMO_THRESHOLD = new BigDecimal("5000");
    private final BigDecimal MIN_AIRTIME_THRESHOLD = new BigDecimal("1000");

    public DeviceQueueService(DeviceRepository deviceRepository, FleetAlertRepository fleetAlertRepository) {
        this.deviceRepository = deviceRepository;
        this.fleetAlertRepository = fleetAlertRepository;
    }

    /**
     * WORKFLOW : registerDevice
     * └── 1. Ajoute ou écrase la session en mémoire de l'appareil dès que l'application Android ouvre son canal WebSocket.
     */
    public void registerDevice(ConnectedDeviceSession session) {
        activeSessions.put(session.deviceId(), session);
    }

    /**
     * WORKFLOW : disconnectDevice
     * └── 1. Supprime instantanément la clé de l'appareil de la Map mémoire lors d'une perte de connexion réseau.
     */
    public void disconnectDevice(UUID deviceId) {
        activeSessions.remove(deviceId);
        pausedDevices.remove(deviceId); // évite une fuite mémoire si le device ne revient jamais
    }

    /**
     * WORKFLOW : getLiveSessions
     * └── 1. Encapsule la Map mémoire sous forme non-modifiable pour préserver l'intégrité du cache face aux APIs.
     */
    public Map<UUID, ConnectedDeviceSession> getLiveSessions() {
        return Collections.unmodifiableMap(activeSessions);
    }

    /**
     * WORKFLOW : acquireAvailableDevice (L'algorithme du Load Balancer Multi-SIM)
     * ---------------------------------------------------------------------------------
     * [TransactionService] ──(Besoin : Opérateur, Type de service, Montant)──> [Ici]
     * │
     * ├── 1. Ouvre un Stream sur l'ensemble des sessions connectées en mémoire vive.
     * ├── 2. FILTRE 1 : Sélectionne uniquement les terminaux dont 'isAvailable' est égal à TRUE (exclut les appareils en pause).
     * ├── 3. FILTRE 2 : Interroge la session pour valider que le solde de la SIM concernée >= Montant requis.
     * ├── 4. RÉTENTION : Sélectionne le tout premier terminal disponible répondant à l'ensemble des conditions.
     * │
     * └── [Exception] ──> Si le pool filtré est vide, lance une Rupture de flotte ('DeviceDisconnectedException').
     */
    public UUID acquireAvailableDevice(Operator targetOperator, ServiceType serviceType, BigDecimal requiredAmount) {
        // CORRECTIF RACE CONDITION : la sélection ET la réservation (bascule isAvailable -> false)
        // doivent être une seule opération atomique. Avant, deux threads pouvaient lire
        // isAvailable == true sur le MEME device avant qu'aucun des deux ne l'ait marqué occupé.
        synchronized (acquisitionLock) {
            ConnectedDeviceSession chosen = activeSessions.values().stream()
                    .filter(ConnectedDeviceSession::isAvailable)
                    .filter(device -> !pausedDevices.contains(device.deviceId()))
                    .filter(device -> device.hasSufficientBalance(targetOperator, serviceType, requiredAmount))
                    .findFirst()
                    .orElseThrow(() -> new DeviceDisconnectedException(
                            "Rupture de flotte : Aucun périphérique disponible pour le service "
                                    + serviceType + " chez " + targetOperator
                    ));

            // Réservation immédiate : plus aucun autre thread ne peut choisir ce device
            // tant qu'il n'aura pas été libéré via releaseDevice(...).
            activeSessions.put(chosen.deviceId(), new ConnectedDeviceSession(
                    chosen.deviceId(),
                    false, // OCCUPÉ
                    chosen.mtnMomoBalance(), chosen.mtnAirtimeBalance(),
                    chosen.orangeOmBalance(), chosen.orangeAirtimeBalance(),
                    chosen.camtelAirtimeBalance()
            ));

            logger.info("[LOAD BALANCER] Device [{}] réservé de manière atomique pour {} / {}.",
                    chosen.deviceId(), targetOperator, serviceType);

            return chosen.deviceId();
        }
    }

    /**
     * WORKFLOW : releaseDevice (Libération du terminal après fin de session USSD)
     * ---------------------------------------------------------------------------------
     * [TransactionService] ──(Collecte ou Transfert confirmé / échoué)──> [Ici]
     * │
     * └── Rebascule 'isAvailable' à TRUE pour réintégrer le device dans le pool du Load Balancer.
     *     À appeler systématiquement après acquireAvailableDevice, que l'opération ait
     *     réussi ou échoué (sinon le device reste bloqué "occupé" indéfiniment).
     */
    public void releaseDevice(UUID deviceId) {
        ConnectedDeviceSession currentSession = activeSessions.get(deviceId);
        if (currentSession != null && !currentSession.isAvailable()) {
            activeSessions.put(deviceId, new ConnectedDeviceSession(
                    currentSession.deviceId(),
                    true, // LIBÉRÉ
                    currentSession.mtnMomoBalance(), currentSession.mtnAirtimeBalance(),
                    currentSession.orangeOmBalance(), currentSession.orangeAirtimeBalance(),
                    currentSession.camtelAirtimeBalance()
            ));
            logger.info("[LOAD BALANCER] Device [{}] libéré, redisponible pour le pool.", deviceId);
        }
    }

    /**
     * WORKFLOW : requestPauseDevice (Mise en pause gracieuse / Isolement à chaud)
     * ---------------------------------------------------------------------------------
     * [Dashboard Admin] ──(Clic bouton "Mettre en Pause")──> [Ici]
     * │
     * ├── 1. Extrait la session active présente en mémoire vive.
     * ├── 2. Crée et injecte une nouvelle instance de Session en forçant l'indicateur 'isAvailable' à FALSE.
     * │      └── EFFET : L'appareil devient invisible pour le Load Balancer (`acquireAvailableDevice`).
     * └── 3. [Note d'intégration] : Laisse le canal WebSocket ouvert pour laisser le terminal finir sa tâche en cours.
     */
    public void requestPauseDevice(UUID deviceId) {
        if (!activeSessions.containsKey(deviceId)) {
            throw new IllegalArgumentException("Aucune session active en mémoire pour le périphérique: " + deviceId);
        }
        // Pause = drapeau séparé de 'isAvailable', qui lui reste réservé à l'état
        // occupé/libre transactionnel. Ça évite qu'une pause admin soit écrasée par
        // une réservation en cours, ou qu'une réservation efface une pause admin.
        pausedDevices.add(deviceId);
        logger.info("[ADMIN] Demande de pause enregistrée pour le périphérique [{}]. Isolement du Load Balancer réussi, fin de transaction en cours autorisée.", deviceId);
    }

    /**
     * WORKFLOW : resumeDevice (Réactivation d'un terminal suspendu)
     * ---------------------------------------------------------------------------------
     * [Dashboard Admin] ──(Clic bouton "Remettre en Service")──> [Ici]
     * │
     * ├── 1. Récupère la session courante isolée depuis la Map mémoire.
     * └── 2. Réinjecte une copie de session actualisée avec l'état 'isAvailable' configuré à TRUE.
     * └── EFFET : Réintègre instantanément l'appareil dans les flux de distribution automatiques.
     */
    public void resumeDevice(UUID deviceId) {
        if (!activeSessions.containsKey(deviceId)) {
            throw new IllegalArgumentException("Aucune session active en mémoire pour le périphérique: " + deviceId);
        }
        pausedDevices.remove(deviceId);
        logger.info("[ADMIN] Le périphérique [{}] a été remis EN SERVICE.", deviceId);
    }

    /**
     * WORKFLOW : incrementMomoBalance (Ajustement comptable après encaissement client)
     * ---------------------------------------------------------------------------------
     * ├── 1. Extrait l'entité Device de la base de données PostgreSQL/MySQL sous verrou transactionnel.
     * ├── 2. Identifie la SIM réceptrice (MTN ou Orange) et procède à l'addition mathématique du montant encaissé.
     * ├── 3. Enregistre la mise à jour pérenne en Base de Données.
     * ├── 4. Synchronise le cache en mémoire active (`activeSessions`) pour éviter tout décalage avec le Load Balancer.
     * └── 5. Appel de autoResolveAlert(...) ──> Clôture automatiquement d'éventuelles alertes de solde bas.
     */
    @Transactional
    public void incrementMomoBalance(UUID deviceId, Operator operator, BigDecimal amount) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device introuvable en DB: " + deviceId));

        if (operator == Operator.MTN) {
            BigDecimal current = device.getMtnMomoBalance() != null ? device.getMtnMomoBalance() : BigDecimal.ZERO;
            device.setMtnMomoBalance(current.add(amount));
        } else if (operator == Operator.ORANGE) {
            BigDecimal current = device.getOrangeOmBalance() != null ? device.getOrangeOmBalance() : BigDecimal.ZERO;
            device.setOrangeOmBalance(current.add(amount));
        }
        deviceRepository.save(device);

        ConnectedDeviceSession currentSession = activeSessions.get(deviceId);
        if (currentSession != null) {
            BigDecimal newMtnMomo = operator == Operator.MTN ?
                    (currentSession.mtnMomoBalance() != null ? currentSession.mtnMomoBalance().add(amount) : amount) : currentSession.mtnMomoBalance();

            BigDecimal newOrangeOm = operator == Operator.ORANGE ?
                    (currentSession.orangeOmBalance() != null ? currentSession.orangeOmBalance().add(amount) : amount) : currentSession.orangeOmBalance();

            activeSessions.put(deviceId, new ConnectedDeviceSession(
                    currentSession.deviceId(), currentSession.isAvailable(),
                    newMtnMomo, currentSession.mtnAirtimeBalance(),
                    newOrangeOm, currentSession.orangeAirtimeBalance(),
                    currentSession.camtelAirtimeBalance()
            ));
        }
        logger.info("[SOLDE] +{} XAF (MoMo/OM) appliqué au périphérique [{}] ({})", amount, deviceId, operator);

        autoResolveAlert(deviceId, operator, ServiceType.MONEY_TRANSFER);
    }

    /**
     * WORKFLOW : decrementBalance (Ajustement comptable après livraison de crédit)
     * ---------------------------------------------------------------------------------
     * ├── 1. Extrait l'entité Device depuis la base de données.
     * ├── 2. Détermine la poche financière impactée (Airtime vs Money Transfer) de l'opérateur concerné (MTN, Orange, Camtel).
     * ├── 3. Soustrait le montant livré et enregistre le nouvel état persistant en BDD.
     * ├── 4. Actualise la structure de session en mémoire (`activeSessions`) pour une cohérence instantanée du Load Balancer.
     * ├── 5. Calcule dynamiquement le solde résiduel après transaction.
     * └── 6. Appel de checkAndTriggerRechargeAlert(...) ──> Analyse si la SIM franchit un seuil de faillite technique.
     */
    @Transactional
    public void decrementBalance(UUID deviceId, Operator operator, ServiceType serviceType, BigDecimal amount) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device introuvable en DB: " + deviceId));

        if (serviceType == ServiceType.AIRTIME) {
            switch (operator) {
                case MTN -> device.setMtnAirtimeBalance(device.getMtnAirtimeBalance().subtract(amount));
                case ORANGE -> device.setOrangeAirtimeBalance(device.getOrangeAirtimeBalance().subtract(amount));
                case CAMTEL -> device.setCamtelAirtimeBalance(device.getCamtelAirtimeBalance().subtract(amount));
            }
        } else if (serviceType == ServiceType.MONEY_TRANSFER) {
            switch (operator) {
                case MTN -> device.setMtnMomoBalance(device.getMtnMomoBalance().subtract(amount));
                case ORANGE -> device.setOrangeOmBalance(device.getOrangeOmBalance().subtract(amount));
                default -> logger.warn("Pas de portefeuille de transfert d'argent pour Camtel");
            }
        }
        deviceRepository.save(device);

        ConnectedDeviceSession currentSession = activeSessions.get(deviceId);
        if (currentSession != null) {
            BigDecimal mtnMomo = currentSession.mtnMomoBalance();
            BigDecimal mtnAirtime = currentSession.mtnAirtimeBalance();
            BigDecimal orangeOm = currentSession.orangeOmBalance();
            BigDecimal orangeAirtime = currentSession.orangeAirtimeBalance();
            BigDecimal camtelAirtime = currentSession.camtelAirtimeBalance();

            if (serviceType == ServiceType.AIRTIME) {
                switch (operator) {
                    case MTN -> mtnAirtime = mtnAirtime.subtract(amount);
                    case ORANGE -> orangeAirtime = orangeAirtime.subtract(amount);
                    case CAMTEL -> camtelAirtime = camtelAirtime.subtract(amount);
                }
            } else if (serviceType == ServiceType.MONEY_TRANSFER) {
                switch (operator) {
                    case MTN -> mtnMomo = mtnMomo.subtract(amount);
                    case ORANGE -> orangeOm = orangeOm.subtract(amount);
                    default -> {}
                }
            }

            activeSessions.put(deviceId, new ConnectedDeviceSession(
                    currentSession.deviceId(), currentSession.isAvailable(),
                    mtnMomo, mtnAirtime, orangeOm, orangeAirtime, camtelAirtime
            ));
        }
        logger.info("[SOLDE] -{} XAF ({}) appliqué au périphérique [{}] ({})", amount, serviceType, deviceId, operator);

        BigDecimal remainingBalance = BigDecimal.ZERO;
        if (serviceType == ServiceType.AIRTIME) {
            remainingBalance = switch (operator) {
                case MTN -> device.getMtnAirtimeBalance();
                case ORANGE -> device.getOrangeAirtimeBalance();
                case CAMTEL -> device.getCamtelAirtimeBalance();
            };
        } else if (serviceType == ServiceType.MONEY_TRANSFER) {
            remainingBalance = switch (operator) {
                case MTN -> device.getMtnMomoBalance();
                case ORANGE -> device.getOrangeOmBalance();
                default -> BigDecimal.ZERO;
            };
        }

        checkAndTriggerRechargeAlert(deviceId, operator, serviceType, remainingBalance);
    }

    /**
     * WORKFLOW : forceExactMomoBalance (Ajustement forcé Admin - Portefeuille MoMo)
     * ---------------------------------------------------------------------------------
     * ├── 1. Écrase le solde MoMo/OM du périphérique directement avec la valeur brute saisie par l'admin.
     * ├── 2. Sauvegarde la valeur en base de données pour aligner les comptes.
     * ├── 3. Aligne instantanément la session présente en mémoire cache.
     * └── 4. Analyse de seuil critique : Si la valeur forcée repasse au-dessus du seuil d'alerte, résout l'incident.
     */
    @Transactional
    public void forceExactMomoBalance(UUID deviceId, Operator operator, BigDecimal exactAmount) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device introuvable en DB: " + deviceId));

        if (operator == Operator.MTN) {
            device.setMtnMomoBalance(exactAmount);
        } else if (operator == Operator.ORANGE) {
            device.setOrangeOmBalance(exactAmount);
        }
        deviceRepository.save(device);

        ConnectedDeviceSession currentSession = activeSessions.get(deviceId);
        if (currentSession != null) {
            activeSessions.put(deviceId, new ConnectedDeviceSession(
                    currentSession.deviceId(), currentSession.isAvailable(),
                    operator == Operator.MTN ? exactAmount : currentSession.mtnMomoBalance(),
                    currentSession.mtnAirtimeBalance(),
                    operator == Operator.ORANGE ? exactAmount : currentSession.orangeOmBalance(),
                    currentSession.orangeAirtimeBalance(),
                    currentSession.camtelAirtimeBalance()
            ));
        }

        logger.info("[ADMIN] Solde MoMo/OM de {} ({}) FORCÉ manuellement à {} XAF", deviceId, operator, exactAmount);

        if (exactAmount.compareTo(MIN_MOMO_THRESHOLD) >= 0) {
            autoResolveAlert(deviceId, operator, ServiceType.MONEY_TRANSFER);
        }
    }

    /**
     * WORKFLOW : forceExactAirtimeBalance (Ajustement forcé Admin - Portefeuille Airtime)
     * ---------------------------------------------------------------------------------
     * ├── 1. Écrase le solde Airtime (Crédit) de l'opérateur désigné avec la valeur fournie.
     * ├── 2. Sauvegarde l'entité mise à jour en base de données.
     * ├── 3. Met à jour la cartographie mémoire cache pour le routage.
     * └── 4. Clôture automatiquement l'alerte sur le Dashboard si le niveau de crédit est de nouveau correct.
     */
    @Transactional
    public void forceExactAirtimeBalance(UUID deviceId, Operator operator, BigDecimal exactAmount) {
        Device device = deviceRepository.findById(deviceId)
                .orElseThrow(() -> new IllegalArgumentException("Device introuvable en DB: " + deviceId));

        switch (operator) {
            case MTN -> device.setMtnAirtimeBalance(exactAmount);
            case ORANGE -> device.setOrangeAirtimeBalance(exactAmount);
            case CAMTEL -> device.setCamtelAirtimeBalance(exactAmount);
        }
        deviceRepository.save(device);

        ConnectedDeviceSession currentSession = activeSessions.get(deviceId);
        if (currentSession != null) {
            activeSessions.put(deviceId, new ConnectedDeviceSession(
                    currentSession.deviceId(), currentSession.isAvailable(),
                    currentSession.mtnMomoBalance(),
                    operator == Operator.MTN ? exactAmount : currentSession.mtnAirtimeBalance(),
                    currentSession.orangeOmBalance(),
                    operator == Operator.ORANGE ? exactAmount : currentSession.orangeAirtimeBalance(),
                    operator == Operator.CAMTEL ? exactAmount : currentSession.camtelAirtimeBalance()
            ));
        }

        logger.info("[ADMIN] Solde Airtime de {} ({}) FORCÉ manuellement à {} XAF", deviceId, operator, exactAmount);

        if (exactAmount.compareTo(MIN_AIRTIME_THRESHOLD) >= 0) {
            autoResolveAlert(deviceId, operator, ServiceType.AIRTIME);
        }
    }

    /**
     * WORKFLOW INTERNE : checkAndTriggerRechargeAlert (Surveillance automatisée des fonds)
     * ---------------------------------------------------------------------------------
     * ├── 1. Détermine le seuil minimal (5 000 XAF pour le transfert, 1 000 XAF pour le crédit).
     * ├── 2. SI (Solde résiduel < Seuil critique) :
     * │      ├── Interroge l'historique pour vérifier qu'aucune alerte identique n'est déjà en cours (éviter les doublons).
     * │      └── SI (Aucune alerte active) : Génère et persiste une entité 'FleetAlert' pour affichage Angular.
     * └── 3. SINON : L'état financier est jugé sain, fin de l'évaluation.
     */
    private void checkAndTriggerRechargeAlert(UUID deviceId, Operator operator, ServiceType serviceType, BigDecimal currentBalance) {
        BigDecimal threshold = (serviceType == ServiceType.AIRTIME) ? MIN_AIRTIME_THRESHOLD : MIN_MOMO_THRESHOLD;

        if (currentBalance.compareTo(threshold) < 0) {
            logger.warn("[ALERTE SEUIL] Solde critique sur Device {} -> {} ({}) : {} XAF", deviceId, operator, serviceType, currentBalance);

            boolean alertExists = fleetAlertRepository
                    .findFirstByDeviceIdAndOperatorAndServiceTypeAndResolvedFalse(deviceId, operator, serviceType)
                    .isPresent();

            if (!alertExists) {
                FleetAlert alert = new FleetAlert(deviceId, operator, serviceType, currentBalance);
                fleetAlertRepository.save(alert);
            }
        }
    }

    /**
     * WORKFLOW INTERNE : autoResolveAlert (Clôture automatique d'incident de flotte)
     * ---------------------------------------------------------------------------------
     * ├── 1. Cherche une ligne d'alerte non résolue (resolved = false) pour le couple (Appareil, Opérateur, Service).
     * └── 2. SI présente :
     * ├── Bascule le drapeau technique à True ('setResolved(true)').
     * ├── Horodate l'instant exact du retour à la normale avec l'heure du serveur.
     * └── Sauvegarde la mise à jour pour libérer le voyant d'alarme sur l'interface d'administration.
     */
    private void autoResolveAlert(UUID deviceId, Operator operator, ServiceType serviceType) {
        fleetAlertRepository
                .findFirstByDeviceIdAndOperatorAndServiceTypeAndResolvedFalse(deviceId, operator, serviceType)
                .ifPresent(alert -> {
                    alert.setResolved(true);
                    alert.setResolvedAt(LocalDateTime.now());
                    fleetAlertRepository.save(alert);
                    logger.info("[ALERTE RESOLUE] L'alerte pour le périphérique {} ({}) - {} a été clôturée.", deviceId, operator, serviceType);
                });
    }
}