package TNB.Switch.service;

import TNB.Switch.entity.Operator;
import TNB.Switch.entity.ServiceType;

import java.math.BigDecimal;
import java.util.UUID;

public record ConnectedDeviceSession(
        UUID deviceId,
        boolean isAvailable,

        // Portefeuille MTN (Null si pas de SIM MTN)
        BigDecimal mtnMomoBalance,
        BigDecimal mtnAirtimeBalance,

        // Portefeuille Orange (Null si pas de SIM Orange)
        BigDecimal orangeOmBalance,
        BigDecimal orangeAirtimeBalance,

        // Portefeuille Camtel (Null si pas de SIM Camtel)
        BigDecimal camtelAirtimeBalance
) {
    public boolean hasSufficientBalance(Operator operator, ServiceType serviceType, BigDecimal requiredAmount) {
        BigDecimal balanceToCheck = null;

        if ("AIRTIME".equalsIgnoreCase(serviceType.toString())) {
            balanceToCheck = switch (operator) {
                case MTN -> mtnAirtimeBalance;
                case ORANGE -> orangeAirtimeBalance;
                case CAMTEL -> camtelAirtimeBalance;
            };
        } else if ("MONEY_TRANSFER".equalsIgnoreCase(serviceType.toString())) {
            balanceToCheck = switch (operator) {
                case MTN -> mtnMomoBalance;
                case ORANGE -> orangeOmBalance;
                case CAMTEL -> null;
            };
        }

        return balanceToCheck != null && balanceToCheck.compareTo(requiredAmount) >= 0;
    }
}