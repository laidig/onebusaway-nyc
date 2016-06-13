package org.onebusaway.nyc.transit_data_federation.services.nyc;

import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.nyc.transit_data.model.OccupancyStatusEnum;
import org.onebusaway.transit_data.model.trips.TripStatusBean;

public interface APCSetterService {

  /**
   * Tell the integration service to refresh the records in cache for the given vehicle.
   * @param vehicleId
   */
  public void updateVehicleLoadForVehicle(AgencyAndId vehicleId);
  
}
