package TNB.Switch.entity;

public enum Operator {
    ORANGE("Orange Money", new String[]{
            "69",           // 690-699
            "655", "656", "657", "658", "659",
            "686", "687", "688", "689",
            "640"
    }),
    MTN("MTN Mobile Money", new String[]{
            "650", "651", "652", "653", "654",
            "670", "671", "672", "673", "674", "675", "676", "677", "678", "679",
            "680", "681", "682", "683"
    }),
    CAMTEL("Blue Money", new String[]{
            "620", "242", "243"
            // préfixes mobiles Camtel (620-622, 660-...) moins documentés publiquement
    });

    private final String displayName;
    private final String[] prefixes;

    // Constructeur strict pour l'enum
    Operator(String displayName, String[] prefixes) {
        this.displayName = displayName;
        this.prefixes = prefixes;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String[] getPrefixes() {
        return prefixes;
    }

    /**
     * Méthode utilitaire essentielle pour le Backend :
     * Permet de détecter automatiquement l'opérateur à partir du numéro du client (Jean).
     */
    public static Operator fromPhoneNumber(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.isEmpty()) {
            return null;
        }

        // Nettoyage sommaire du numéro (ex: enlever le +237)
        String cleanNumber = phoneNumber.replace("+237", "").trim();

        for (Operator op : Operator.values()) {
            for (String prefix : op.getPrefixes()) {
                if (cleanNumber.startsWith(prefix)) {
                    return op;
                }
            }
        }
        return null; // Opérateur inconnu
    }
}