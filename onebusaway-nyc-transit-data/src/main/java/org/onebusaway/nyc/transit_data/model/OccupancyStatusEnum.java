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
  
  public String getSiriEquivalentOccupancyEnum(OccupancyStatusEnum o){
    if (o == OccupancyStatusEnum.EMPTY || o == OccupancyStatusEnum.MANY_SEATS_AVAILABLE 
        || o == OccupancyStatusEnum.FEW_SEATS_AVAILABLE) {
      return "seatsAvailable";
    } else if (o == OccupancyStatusEnum.STANDING_ROOM_ONLY) {
      return "standingAvailable";
    } else if (o == OccupancyStatusEnum.CRUSHED_STANDING_ROOM_ONLY || o == OccupancyStatusEnum.FULL
       || o == OccupancyStatusEnum.NOT_ACCEPTING_PASSENGERS ) {
      return "full";
    } else {
      return "unknown";
    }    
  }
}
