package org.onebusaway.nyc.transit_data.model;

/*
 * Copyright 2016 Metropolitan Transportation Authority
 * 
 * An enum for defining a vehicle's observed capacity. 
 * Based on the proposed GTFS Realtime OccupancyStatus
 * 
 * 
 * mapping to proposed GTFSrt OccupancyStatus:
 * SIRI -> GTFS
 * 
 * SEATS_AVAILABLE -> FEW_SEATS_AVAILABLE
 * STANDING_AVAILABLE -> STANDING_ROOM_ONLY
 * FULL -> FULL
 */

public enum OccupancyStatusEnum {
  UNKNOWN, EMPTY, MANY_SEATS_AVAILABLE, FEW_SEATS_AVAILABLE,
  STANDING_ROOM_ONLY, CRUSHED_STANDING_ROOM_ONLY, FULL,
  NOT_ACCEPTING_PASSENGERS;
  
  // since pulling in the SIRI dependency is a bit large for this package,
  // this returns a string over the SIRI OccupancyEnum.
  // there may be a better way over using a String.
  public String getSiriEquivalentOccupancyEnum(){
    if (this == OccupancyStatusEnum.EMPTY || this == OccupancyStatusEnum.MANY_SEATS_AVAILABLE 
        || this == OccupancyStatusEnum.FEW_SEATS_AVAILABLE) {
      return "seatsAvailable";
    } else if (this == OccupancyStatusEnum.STANDING_ROOM_ONLY) {
      return "standingAvailable";
    } else if (this == OccupancyStatusEnum.CRUSHED_STANDING_ROOM_ONLY || this == OccupancyStatusEnum.FULL
       || this == OccupancyStatusEnum.NOT_ACCEPTING_PASSENGERS ) {
      return "full";
    } else {
      return null;
    }    
  }
}
