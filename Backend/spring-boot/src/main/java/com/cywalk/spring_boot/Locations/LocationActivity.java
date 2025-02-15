package com.cywalk.spring_boot.Locations;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Schema(description = "The activity or location session recording")
public class LocationActivity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Schema(description = "primary key in the database")
    private Long id;

    @OneToMany(cascade = CascadeType.ALL, fetch = FetchType.EAGER)
    @Schema(description = "The locations logged during this activity")
    private List<Location> locations;

    @Schema(description = "The total distance traveled during this session")
    private double totalDistance;

    /**
     * Whether the activity is finished or not.
     */
    @Schema(description = "Whether this is actively being update or whether this is complete")
    private boolean finished;

    public LocationActivity(Long id, List<Location> locations) {
        this.id = id;
        this.locations = locations;
        this.finished = false;
        this.totalDistance = 0;
    }

    public LocationActivity(Long id, List<Location> locations, boolean finished) {
        this.id = id;
        this.locations = locations;
        this.finished = finished;
        this.totalDistance = 0;
    }

    public LocationActivity() {
        this.locations = new ArrayList<>();
        this.finished = false;
        this.totalDistance = 0;
    }

    public LocationActivity(Long id, List<Location> locations, double totalDistance, boolean finished) {
        this.id = id;
        this.locations = locations;
        this.totalDistance = totalDistance;
        this.finished = finished;
    }

    public double getTotalDistance() {
        return totalDistance;
    }

    public void setTotalDistance(double totalDistance) {
        this.totalDistance = totalDistance;
    }

    public double calculateAndUpdateDistance() {
        double distance = 0;
        for (int i = 1; i < locations.size(); i++) {
            LocationUtils.calculateDistance(locations.get(i - 1), locations.get(i));
        }
        totalDistance = distance;
        return distance;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<Location> getLocations() {
        return locations;
    }

    public void setLocations(List<Location> locations) {
        this.locations = locations;
    }

    public void addLocationToActivity(Location newLocation) {
        if (this.locations == null) {
            this.locations = new ArrayList<>();
        }
        this.locations.add(newLocation);
        // update distance here
        if (locations.size() > 1) {
            totalDistance += LocationUtils.calculateDistance(locations.get(locations.size() - 2), newLocation);
        }
    }

    public boolean isFinished() {
        return finished;
    }

    public void setFinished(boolean finished) {
        this.finished = finished;
    }

    /**
     * A valid session is one that has a start and end. This means at least two logged locations
     * @return Whether the session is valid/complete
     */
    public boolean validSession() {
        return this.locations.size() > 1;
    }
}
