package TNB.Switch.service;

import TNB.Switch.entity.Operator;
import TNB.Switch.exception.SmsParsingException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * =====================================================================================
 * WORKFLOW GLOBAL DU SERVICE : MOTEUR D'ANALYSE ET EXTRACTEUR RELEXICAL (REGEXP)
 * =====================================================================================
 * Ce service agit comme un traducteur de données non-structurées en entités structurées.
 * 1. Il reçoit le contenu textuel brut des notifications opérateurs (SMS).
 * 2. Il isole le moteur de traitement spécifique selon l'opérateur émetteur (Orange, MTN, Camtel).
 * 3. Il passe le texte au tamis de expressions régulières (Regex) compilées en mémoire.
 * 4. Il extrait de manière chirurgicale les données clés : Montant, Téléphone tiers, Référence.
 * =====================================================================================
 */
@Service
public class SmsParserService {

    private static final Pattern ORANGE_COLLECT_PATTERN =
            Pattern.compile("Vous avez recu (\\d+) XAF de (\\d+)\\. Ref: (\\w+)");

    private static final Pattern ORANGE_TRANSFER_PATTERN =
            Pattern.compile("Transfert de (\\d+) XAF vers (\\d+) reussi\\. Transaction ID: (\\w+)");

    private static final Pattern MTN_COLLECT_PATTERN =
            Pattern.compile("Depot de (\\d+) XAF fait par (\\d+)\\. Reference: (\\w+)");

    private static final Pattern MTN_TRANSFER_PATTERN =
            Pattern.compile("Transfert de (\\d+) XAF vers (\\d+) reussi\\. Ref: (\\w+)");

    private static final Pattern CAMTEL_TRANSFER_PATTERN =
            Pattern.compile("Recharge Blue de (\\d+) XAF effectuee vers (\\d+)\\. Ref: (\\w+)");

    public record ParsedSmsData(
            boolean isCollect,
            BigDecimal amount,
            String phoneNumber,
            String operatorReference
    ) {}

    /**
     * WORKFLOW : parse
     * ---------------------------------------------------------------------------------
     * [TransactionService] ──(Contenu brut du SMS + Opérateur attendu)──> [Ici]
     * │
     * ├── 1. Vérification sanitaire de premier niveau : Rejette le traitement si la chaîne est vide/nulle.
     * ├── 2. Aiguillage algorithmique vers la sous-méthode de parsing selon l'énumération Operator.
     * │        ├── Orange ──> parseOrange(smsRaw)
     * │        ├── MTN    ──> parseMtn(smsRaw)
     * │        └── Camtel ──> parseCamtel(smsRaw)
     * │
     * └── [Exception] ──> Si aucune règle textuelle ne matche la structure du SMS, lève une SmsParsingException.
     */
    public ParsedSmsData parse(Operator operator, String senderId, String smsRaw) {
        if (smsRaw == null || smsRaw.isBlank()) {
            throw new SmsParsingException("Le contenu du SMS est vide.");
        }

        try {
            return switch (operator) {
                case ORANGE -> parseOrange(smsRaw);
                case MTN -> parseMtn(smsRaw);
                case CAMTEL -> parseCamtel(smsRaw);
                default -> throw new SmsParsingException("Opérateur non supporté pour l'analyse automatisée.");
            };
        } catch (Exception e) {
            throw new SmsParsingException("Échec du parsing de l'analyse textuelle : " + e.getMessage());
        }
    }

    /**
     * WORKFLOW INTERNE : parseOrange
     * ---------------------------------------------------------------------------------
     * ├── 1. Exécute le gabarit Regex de COLLECTE Orange Money.
     * │      └── Match trouvé ? Retourne ParsedSmsData configuré à isCollect = true.
     * ├── 2. Si échec, bascule et exécute le gabarit de TRANSFERT/DÉBIT Orange Money.
     * │      └── Match trouvé ? Retourne ParsedSmsData configuré à isCollect = false.
     * └── 3. Aucun motif valide détecté ? Propulse une IllegalArgumentException.
     */
    private ParsedSmsData parseOrange(String smsRaw) {
        Matcher collectMatcher = ORANGE_COLLECT_PATTERN.matcher(smsRaw);
        if (collectMatcher.find()) {
            return new ParsedSmsData(
                    true,
                    new BigDecimal(collectMatcher.group(1)),
                    collectMatcher.group(2),
                    collectMatcher.group(3)
            );
        }

        Matcher transferMatcher = ORANGE_TRANSFER_PATTERN.matcher(smsRaw);
        if (transferMatcher.find()) {
            return new ParsedSmsData(
                    false,
                    new BigDecimal(transferMatcher.group(1)),
                    transferMatcher.group(2),
                    transferMatcher.group(3)
            );
        }

        throw new IllegalArgumentException("Le format du SMS Orange ne correspond à aucune règle.");
    }

    /**
     * WORKFLOW INTERNE : parseMtn
     * ---------------------------------------------------------------------------------
     * ├── 1. Exécute le gabarit Regex de COLLECTE MTN Mobile Money.
     * │      └── Match trouvé ? Retourne ParsedSmsData configuré à isCollect = true.
     * ├── 2. Si échec, exécute le gabarit de TRANSFERT/DÉBIT MTN Mobile Money.
     * │      └── Match trouvé ? Retourne ParsedSmsData configuré à isCollect = false.
     * └── 3. Aucun motif valide détecté ? Propulse une IllegalArgumentException.
     */
    private ParsedSmsData parseMtn(String smsRaw) {
        Matcher collectMatcher = MTN_COLLECT_PATTERN.matcher(smsRaw);
        if (collectMatcher.find()) {
            return new ParsedSmsData(
                    true,
                    new BigDecimal(collectMatcher.group(1)),
                    collectMatcher.group(2),
                    collectMatcher.group(3)
            );
        }

        Matcher transferMatcher = MTN_TRANSFER_PATTERN.matcher(smsRaw);
        if (transferMatcher.find()) {
            return new ParsedSmsData(
                    false,
                    new BigDecimal(transferMatcher.group(1)),
                    transferMatcher.group(2),
                    transferMatcher.group(3)
            );
        }

        throw new IllegalArgumentException("Le format du SMS MTN ne correspond à aucune règle.");
    }

    /**
     * WORKFLOW INTERNE : parseCamtel
     * ---------------------------------------------------------------------------------
     * ├── 1. Exécute l'unique gabarit Regex de TRANSFERT de crédit de communication Camtel Blue.
     * │      └── Match trouvé ? Retourne ParsedSmsData configuré strict à isCollect = false.
     * └── 2. Aucun motif valide détecté ? Propulse une IllegalArgumentException.
     */
    private ParsedSmsData parseCamtel(String smsRaw) {
        Matcher transferMatcher = CAMTEL_TRANSFER_PATTERN.matcher(smsRaw);
        if (transferMatcher.find()) {
            return new ParsedSmsData(
                    false,
                    new BigDecimal(transferMatcher.group(1)),
                    transferMatcher.group(2),
                    transferMatcher.group(3)
            );
        }

        throw new IllegalArgumentException("Le format du SMS Camtel ne correspond à aucune règle.");
    }
}