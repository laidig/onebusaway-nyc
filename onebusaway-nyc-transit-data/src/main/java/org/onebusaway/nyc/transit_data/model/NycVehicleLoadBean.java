package org.onebusaway.nyc.transit_data.model;

import java.io.Serializable;
/**
 * An "over the wire", queued APC result--gets passed between the APC infrastructure
 * and the TDF/TDS running on all front-end notes, plus the archiver and possibly other
 * data consumers.
 * 
 * @author laidig
 *
 */
public class NycVehicleLoadBean implements Serializable {

  /**
   * 
   */
  private static final long serialVersionUID = 1L;
  
  // for consistency, epoch ms in GMT
  private Long recordTimestamp;

  // vehicle id, preferrably fully qualified
  // may need to change to AgencyAndID
  private String vehicleId;
  
  //loading maps directly to SIRI OccupancyEnum
  private OccupancyStatusEnum load;
  
  //GTFS route_id
  private String route;
  
  //GTFS direction_id, typed as String to keep in pattern with rest of OBA.
  private String direction;
  
  //estimated count for debugging only
  private int estimatedCount;

  public NycVehicleLoadBean(NycVehicleLoadBeanBuilder b) {
        this.vehicleId = b.vehicleId;
        this.load = b.load;
        this.route = b.route;
        this.direction = b.direction;
        this.estimatedCount = b.estimatedCount;
  }

  public int getEstimatedCount() {
    return estimatedCount;
  }

  public OccupancyStatusEnum getLoad() {
    return load;
  }

  public Long getRecordTimestamp() {
    return recordTimestamp;
  }

  public String getVehicleId() {
    return vehicleId;
  }

  public String getDirection() {
    return direction;
  }

  public String getRoute() {
    return route;
  }

/* builder pattern class for NycVehicleLoadBean
 * to use this, call something like:
 * new NycVehicleLoadBean.NycVehicleLoadBeanBuilder('vehicle_1234',1234566789)
 * .route('route1').direction(1).load('FULL').build();
  */
  public static class NycVehicleLoadBeanBuilder {
    // load at which to throw IllegalStateException
    // TODO: should this constant be refactored out? 
    private final int ERROR_LOAD = 1024; 
    
    private final String vehicleId;
    private final long recordTimestamp;
    private OccupancyStatusEnum load;
    private String direction;
    private String route;
    private int estimatedCount;
    
    public NycVehicleLoadBeanBuilder(String vehicleId, long recordTimestamp){
      this.vehicleId = vehicleId;
      this.recordTimestamp = recordTimestamp;
    }
    public NycVehicleLoadBeanBuilder load(String load){
      load = load.toUpperCase();
      
      if (load == "FEW_SEATS_AVAILABLE"){
        this.load = OccupancyStatusEnum.FEW_SEATS_AVAILABLE;
      } else if (load =="STANDING_AVAILABLE"){
        this.load = OccupancyStatusEnum.STANDING_ROOM_ONLY;
      } else if (load == "FULL"){
        this.load = OccupancyStatusEnum.FULL;
      } else {
        this.load = OccupancyStatusEnum.UNKNOWN;
      }
      return this;
    }
    public NycVehicleLoadBeanBuilder route(String route){
      this.route = route;
      return this;
    }
    public NycVehicleLoadBeanBuilder direction(String direction){
      this.direction = direction;
      return this;
    }
    public NycVehicleLoadBeanBuilder estimatedCount(int ct){
      this.estimatedCount = ct;
      return this;
    }
    
    public NycVehicleLoadBean build() throws IllegalStateException{
 
      if (load == null){ load = OccupancyStatusEnum.UNKNOWN; }
      if (estimatedCount > ERROR_LOAD){
        throw new IllegalStateException("Load is greatere than " + ERROR_LOAD + ", which is unlikely on a bus.");
      }
      
      return new NycVehicleLoadBean(this);
    }
    
  }


}
