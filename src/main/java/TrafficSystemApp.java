import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

// ==========================================
// CUSTOM BUSINESS EXCEPTIONS
// ==========================================
class InvalidVehicleException extends RuntimeException {
    public InvalidVehicleException(String message) {
        super(message);
    }
}

class ChallanException extends RuntimeException {
    public ChallanException(String message) {
        super(message);
    }
}

// ==========================================
// CORE DOMAIN MODELS & ENUMS
// ==========================================
enum VehicleType {
    TWO_WHEELER, CAR, COMMERCIAL
}

enum ViolationType {
    OVER_SPEEDING(2000.0), SIGNAL_VIOLATION(1500.0), ILLEGAL_PARKING(1000.0);

    private final double baseFine;
    ViolationType(double baseFine) { this.baseFine = baseFine; }
    public double getBaseFine() { return baseFine; }
}

enum VehicleRiskClass {
    SAFE, MODERATE_RISK, HIGH_RISK
}

class Challan {
    private final String challanId;
    private final String vehicleNumber;
    private final ViolationType violationType;
    private final String location;
    private final LocalDateTime timestamp;
    private final double fineAmount;
    private final double speed;
    private final double permittedSpeed;
    private boolean isPaid;

    public Challan(String challanId, String vehicleNumber, ViolationType violationType, String location, 
                   LocalDateTime timestamp, double fineAmount, double speed, double permittedSpeed) {
        this.challanId = challanId;
        this.vehicleNumber = vehicleNumber;
        this.violationType = violationType;
        this.location = location;
        this.timestamp = timestamp;
        this.fineAmount = fineAmount;
        this.speed = speed;
        this.permittedSpeed = permittedSpeed;
        this.isPaid = false;
    }

    public String getChallanId() { return challanId; }
    public String getVehicleNumber() { return vehicleNumber; }
    public ViolationType getViolationType() { return violationType; }
    public String getLocation() { return location; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public double getFineAmount() { return fineAmount; }
    public boolean isPaid() { return isPaid; }
    public void setPaid(boolean paid) { isPaid = paid; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Challan challan = (Challan) o;
        return Objects.equals(vehicleNumber, challan.vehicleNumber) &&
                violationType == challan.violationType &&
                Objects.equals(location, challan.location) &&
                Objects.equals(timestamp, challan.timestamp);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vehicleNumber, violationType, location, timestamp);
    }
}

class Vehicle {
    private final String vehicleNumber;
    private final String ownerName;
    private final VehicleType vehicleType;
    private final List<Challan> violationHistory = new ArrayList<>();

    public Vehicle(String vehicleNumber, String ownerName, VehicleType vehicleType) {
        this.vehicleNumber = vehicleNumber;
        this.ownerName = ownerName;
        this.vehicleType = vehicleType;
    }

    public String getVehicleNumber() { return vehicleNumber; }
    public String getOwnerName() { return ownerName; }
    public VehicleType getVehicleType() { return vehicleType; }
    public List<Challan> getViolationHistory() { return violationHistory; }

    public void addViolation(Challan challan) {
        this.violationHistory.add(challan);
    }

    public VehicleRiskClass getRiskClassification() {
        long violations = violationHistory.size();
        if (violations <= 1) return VehicleRiskClass.SAFE;
        if (violations <= 3) return VehicleRiskClass.MODERATE_RISK;
        return VehicleRiskClass.HIGH_RISK;
    }
}

// ==========================================
// CORE MANAGEMENT ENGINE SERVICE
// ==========================================
class ChallanManagementService {
    private final Map<String, Vehicle> vehicleRegistry = new HashMap<>();
    private final Map<String, Challan> challanStore = new HashMap<>();
    
    private static final Pattern VEHICLE_PATTERN = Pattern.compile("^[A-Z]{2}[0-9]{2}[A-Z]{1,2}[0-9]{4}$");

    public void registerVehicle(Vehicle vehicle) {
        if (vehicle == null || vehicle.getVehicleNumber() == null) {
            throw new InvalidVehicleException("Vehicle registration data cannot be empty.");
        }
        if (!VEHICLE_PATTERN.matcher(vehicle.getVehicleNumber()).matches()) {
            throw new InvalidVehicleException("Invalid vehicle number standard format structure: " + vehicle.getVehicleNumber());
        }
        vehicleRegistry.put(vehicle.getVehicleNumber(), vehicle);
    }

    public synchronized Challan issueChallan(String vehicleNumber, ViolationType violation, String location, 
                                            LocalDateTime timestamp, double speed, double permittedSpeed) {
        
        Vehicle vehicle = vehicleRegistry.get(vehicleNumber);
        if (vehicle == null) {
            throw new InvalidVehicleException("Vehicle number is not registered in the system network database.");
        }

        if (violation == ViolationType.OVER_SPEEDING && speed <= permittedSpeed) {
            throw new ChallanException("Cannot issue speed violation where tracked speed is lower than boundary parameter limits.");
        }

        for (Challan existing : vehicle.getViolationHistory()) {
            if (existing.getLocation().equalsIgnoreCase(location) && 
                existing.getTimestamp().equals(timestamp) && 
                existing.getViolationType() == violation) {
                throw new ChallanException("Duplicate violation event recorded for the identical space-time fingerprint target context.");
            }
        }

        long totalPreviousViolations = vehicle.getViolationHistory().size();
        double calculatedFine = violation.getBaseFine();
        if (totalPreviousViolations > 0) {
            calculatedFine += (violation.getBaseFine() * 0.50 * totalPreviousViolations);
        }

        String challanId = "CH-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        Challan newChallan = new Challan(challanId, vehicleNumber, violation, location, timestamp, calculatedFine, speed, permittedSpeed);
        
        vehicle.addViolation(newChallan);
        challanStore.put(challanId, newChallan);
        
        return newChallan;
    }

    public synchronized void payChallan(String challanId) {
        Challan challan = challanStore.get(challanId);
        if (challan == null) {
            throw new ChallanException("Challan identification mapping code variant index key not found.");
        }
        if (challan.isPaid()) {
            throw new ChallanException("Challan statement parameters indicate settlement balance has been fulfilled previously.");
        }
        challan.setPaid(true);
    }

    public double calculateOutstandingFines(String vehicleNumber) {
        Vehicle vehicle = vehicleRegistry.get(vehicleNumber);
        if (vehicle == null) return 0.0;
        
        return vehicle.getViolationHistory().stream()
                .filter(c -> !c.isPaid())
                .mapToDouble(Challan::getFineAmount)
                .sum();
    }
}

// ==========================================
// RUNNABLE MAIN APPLICATION METHOD
// ==========================================
public class TrafficSystemApp {
    public static void main(String[] args) {
        System.out.println("=== INITIALIZING TRAFFIC MANAGEMENT SYSTEM APPLICATION ===");
        ChallanManagementService service = new ChallanManagementService();
        LocalDateTime time = LocalDateTime.now();

        try {
            Vehicle myCar = new Vehicle("DL01CA1234", "Amit Sharma", VehicleType.CAR);
            service.registerVehicle(myCar);
            System.out.println("[SUCCESS] Registered vehicle: DL01CA1234");

            Challan c1 = service.issueChallan("DL01CA1234", ViolationType.SIGNAL_VIOLATION, "Connaught Place", time, 0, 0);
            System.out.println("[CHALLAN ISSUED] ID: " + c1.getChallanId() + " | Fine Base Rate: ₹" + c1.getFineAmount());

            Challan c2 = service.issueChallan("DL01CA1234", ViolationType.ILLEGAL_PARKING, "Rajiv Chowk", time.plusMinutes(30), 0, 0);
            System.out.println("[REPEAT CHALLAN ISSUED] ID: " + c2.getChallanId() + " | Fine Escalated Rate: ₹" + c2.getFineAmount());

            System.out.println("Total Outstanding Fines Due: ₹" + service.calculateOutstandingFines("DL01CA1234"));
            System.out.println("Current Asset Classification Profile State: " + myCar.getRiskClassification());

            service.payChallan(c1.getChallanId());
            System.out.println("[PAYMENT COMPLETED] Balance left: ₹" + service.calculateOutstandingFines("DL01CA1234"));

        } catch (Exception e) {
            System.out.println("[ERROR]: " + e.getMessage());
        }
    }
}
