package org.onebusaway.nyc.transit_data_federation.impl.nyc;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.onebusaway.nyc.transit_data.model.NycVehicleLoadBean;
import org.onebusaway.nyc.transit_data.model.OccupancyStatusEnum;
import org.onebusaway.nyc.transit_data.services.NycTransitDataService;
import org.onebusaway.nyc.util.configuration.ConfigurationService;
import org.onebusaway.transit_data.model.RouteBean;
import org.onebusaway.transit_data.model.trips.TripBean;
import org.onebusaway.transit_data.model.trips.TripStatusBean;


@Ignore public class APCIntegrationServiceImplTest {
  private static TripStatusBean tripStatus;
  @InjectMocks
  private static APCIntegrationServiceImpl service;
  
  
  @BeforeClass
  public static void setUp() throws Exception {
    
    tripStatus =  new TripStatusBean();
    service = new APCIntegrationServiceImpl();
        
    tripStatus.setVehicleId("MTA_12345");
    RouteBean.Builder routeBuilder = RouteBean.builder();
    routeBuilder.setId("MTA_101X");
    RouteBean routeBean = routeBuilder.create();
    TripBean tripBean = new TripBean();
    tripBean.setRoute(routeBean);
    tripBean.setDirectionId("1");
    tripStatus.setActiveTrip(tripBean);
  }
  
  @Before
  public void initMocks(){
    MockitoAnnotations.initMocks(this);
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
