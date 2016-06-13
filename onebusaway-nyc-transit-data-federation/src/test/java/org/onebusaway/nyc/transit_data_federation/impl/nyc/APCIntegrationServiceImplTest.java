package org.onebusaway.nyc.transit_data_federation.impl.nyc;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.onebusaway.nyc.transit_data.model.NycVehicleLoadBean;
import org.onebusaway.nyc.transit_data.model.OccupancyStatusEnum;
import org.onebusaway.transit_data.model.RouteBean;
import org.onebusaway.transit_data.model.trips.TripBean;
import org.onebusaway.transit_data.model.trips.TripStatusBean;

@Ignore public class APCIntegrationServiceImplTest {
  TripStatusBean tripStatus;
  APCIntegrationServiceImpl service;
  
  @Before
  public void setUp() throws Exception {
    tripStatus =  new TripStatusBean();
    service = new APCIntegrationServiceImpl();
    service.initializeCache();
    
    tripStatus.setVehicleId("MTA_12345");
    RouteBean.Builder routeBuilder = RouteBean.builder();
    routeBuilder.setId("MTA_101X");
    RouteBean routeBean = routeBuilder.create();
    TripBean tripBean = new TripBean();
    tripBean.setRoute(routeBean);
    tripBean.setDirectionId("1");
    tripStatus.setActiveTrip(tripBean);
  }

  @Test
  public void testProcessMessageAndCache() {
    long timeStamp = System.currentTimeMillis();
    NycVehicleLoadBean loadBean = new NycVehicleLoadBean.
        NycVehicleLoadBeanBuilder("MTA_12345", timeStamp)
        .direction("1")
        .route("MTA_101X")
        .load("FEW_SEATS_AVAILABLE")
        .build();
    service.processResult(loadBean, null);
    
    System.out.println(service.getVehicleLoadForVehicleRouteDirection("MTA_12345", "MTA_101X", "1"));
    
    assertEquals(service.getVehicleLoadForVehicleRouteDirection("MTA_12345", "MTA_101X", "1"),
        OccupancyStatusEnum.FEW_SEATS_AVAILABLE);
    
  }
  
  

}
