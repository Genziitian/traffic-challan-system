import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;

public class TrafficSystemAppTest {
    private ChallanManagementService service;
    private final LocalDateTime now = LocalDateTime.now();

    @BeforeEach
    public void setup() {
        service = new ChallanManagementService();
    }

    @Test
    public void testValidVehicleRegistrationAndChallanIssuance() {
        Vehicle car = new Vehicle("DL01CA1234", "John Doe", VehicleType.CAR);
        service.registerVehicle(car);

        Challan c = service.issueChallan("DL01CA1234", ViolationType.SIGNAL_VIOLATION, "Intersection A", now, 0, 0);
        assertNotNull(c.getChallanId());
        assertEquals(1500.0, c.getFineAmount());
        assertEquals(VehicleRiskClass.SAFE, car.getRiskClassification());
    }

    @Test
    public void testInvalidVehicleNumberFormat() {
        assertThrows(InvalidVehicleException.class, () -> {
            service.registerVehicle(new Vehicle("INVALID-FORMAT-123", "Jane Doe", VehicleType.TWO_WHEELER));
        });
    }

    @Test
    public void testEscalatedFinesForRepeatViolations() {
        Vehicle car = new Vehicle("MH12AB5678", "Bob Smith", VehicleType.CAR);
        service.registerVehicle(car);

        Challan first = service.issueChallan("MH12AB5678", ViolationType.ILLEGAL_PARKING, "Street 1", now, 0, 0);
        Challan second = service.issueChallan("MH12AB5678", ViolationType.ILLEGAL_PARKING, "Street 2", now.plusHours(1), 0, 0);

        assertEquals(1000.0, first.getFineAmount());
        assertEquals(1500.0, second.getFineAmount()); // Base (1000) + 50% compound penalty
        assertEquals(VehicleRiskClass.MODERATE_RISK, car.getRiskClassification());
    }

    @Test
    public void testBoundarySpeedInvalidIssuance() {
        Vehicle car = new Vehicle("KA03HA9999", "Alice White", VehicleType.COMMERCIAL);
        service.registerVehicle(car);

        assertThrows(ChallanException.class, () -> {
            service.issueChallan("KA03HA9999", ViolationType.OVER_SPEEDING, "Highway 1", now, 60, 80);
        });
    }

    @Test
    public void testDuplicateViolationRejection() {
        Vehicle car = new Vehicle("DL01CA1234", "John Doe", VehicleType.CAR);
        service.registerVehicle(car);

        service.issueChallan("DL01CA1234", ViolationType.SIGNAL_VIOLATION, "Intersection A", now, 0, 0);
        
        assertThrows(ChallanException.class, () -> {
            service.issueChallan("DL01CA1234", ViolationType.SIGNAL_VIOLATION, "Intersection A", now, 0, 0);
        });
    }

    @Test
    public void testPaymentAndOutstandingBalances() {
        Vehicle car = new Vehicle("DL01CA1234", "John Doe", VehicleType.CAR);
        service.registerVehicle(car);

        Challan c = service.issueChallan("DL01CA1234", ViolationType.SIGNAL_VIOLATION, "Intersection A", now, 0, 0);
        assertEquals(1500.0, service.calculateOutstandingFines("DL01CA1234"));

        service.payChallan(c.getChallanId());
        assertEquals(0.0, service.calculateOutstandingFines("DL01CA1234"));
    }
}
