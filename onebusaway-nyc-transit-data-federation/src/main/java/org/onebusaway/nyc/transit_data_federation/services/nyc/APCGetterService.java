package org.onebusaway.nyc.transit_data_federation.services.nyc;

import org.onebusaway.nyc.transit_data.model.OccupancyStatusEnum;
import org.onebusaway.transit_data.model.trips.TripStatusBean;

public interface APCGetterService {
  /**
   * A method to return passengerLoad in a format suitable for injection into the TDS for the given
   * vehicle status.
   * 
   * @param tripStatus
   * @return
   */
  public OccupancyStatusEnum getVehicleLoadForTrip(TripStatusBean tripStatus);
  
  /**
   * A method to return passengerLoad in a format suitable for injection into the TDS for the given
   * vehicle status.
   * 
   * @param vehicleId
   * @return
   */
  public OccupancyStatusEnum getVehicleLoadForVehicleRouteDirection(String VehicleId, String RouteID, String Direction);

}
