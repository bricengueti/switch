package TNB.Switch.repository;

import TNB.Switch.entity.FleetAlert;
import TNB.Switch.entity.Operator;
import TNB.Switch.entity.ServiceType;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.UUID;
import java.util.Optional;

public interface FleetAlertRepository extends JpaRepository<FleetAlert, Long> {

    // CORRECTION ICI : Remplacer PivotAlert par FleetAlert
    List<FleetAlert> findByResolvedFalse();

    Optional<FleetAlert> findFirstByDeviceIdAndOperatorAndServiceTypeAndResolvedFalse(
            UUID deviceId, Operator operator, ServiceType serviceType);
}