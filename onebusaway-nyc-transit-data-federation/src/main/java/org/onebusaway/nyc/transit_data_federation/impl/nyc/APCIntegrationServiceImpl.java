package org.onebusaway.nyc.transit_data_federation.impl.nyc;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import org.onebusaway.container.refresh.Refreshable;
import org.onebusaway.nyc.transit_data.model.NycVehicleLoadBean;
import org.onebusaway.nyc.transit_data.model.OccupancyStatusEnum;
import org.onebusaway.nyc.transit_data.services.NycTransitDataService;
import org.onebusaway.nyc.transit_data_federation.impl.queue.APCQueueListenerTask;
import org.onebusaway.nyc.transit_data_federation.services.nyc.APCGetterService;
import org.onebusaway.nyc.util.configuration.ConfigurationService;
import org.onebusaway.transit_data.model.trips.TripStatusBean;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;

public class APCIntegrationServiceImpl extends APCQueueListenerTask 
implements APCGetterService {

  private static final int DEFAULT_CACHE_TIMEOUT = 15 * 60; // 15 min
  private static final String CACHE_TIMEOUT_KEY = "tds.apc.expiry";

  @Autowired
  private ConfigurationService _configurationService;
  public void setConfigurationService(ConfigurationService configService) {
	  _configurationService = configService;
  }

  private Cache<String, NycVehicleLoadBean> _cache = null;

  private synchronized Cache<String, NycVehicleLoadBean> getCache(){
    if (_cache == null) {

      int timeout = _configurationService.getConfigurationValueAsInteger(CACHE_TIMEOUT_KEY, DEFAULT_CACHE_TIMEOUT);
      _log.info("creating initial APC cache with timeout " + timeout + "...");
      _cache = CacheBuilder.newBuilder()
          .expireAfterWrite(timeout, TimeUnit.SECONDS)
          .build();
      _log.info("done");
    }
    return _cache;
  }

  //TODO: is this necessary?
  @Refreshable(dependsOn = {CACHE_TIMEOUT_KEY})
  private synchronized void refreshCache() {
    
    int timeout = _configurationService.getConfigurationValueAsInteger(CACHE_TIMEOUT_KEY, DEFAULT_CACHE_TIMEOUT);
    _log.info("rebuilding prediction cache with " + getCache().size() + " entries after refresh with timeout=" + timeout + "...");

    ConcurrentMap<String, NycVehicleLoadBean> map = getCache().asMap();
 
    for (Entry<String, NycVehicleLoadBean> entry : map.entrySet()) {
      getCache().put(entry.getKey(), entry.getValue());
    }
    _log.info("done");
  }

  @Override
  public OccupancyStatusEnum getVehicleLoadForTrip(TripStatusBean tripStatus) {
      return  getCache().getIfPresent(
        hash(tripStatus.getVehicleId(), 
            tripStatus.getActiveTrip().getRoute().getId(), 
            tripStatus.getActiveTrip().getDirectionId()))
        .getLoad();
  }

  @Override
  public OccupancyStatusEnum getVehicleLoadForVehicleRouteDirection(
      String VehicleId, String RouteID, String Direction) {
          return getCache().getIfPresent(
            hash(VehicleId, RouteID, Direction))
            .getLoad();
  }

  @Override
  protected void processResult(NycVehicleLoadBean message, String s) {
    _log.debug("VehicleLoadBean for " + hashFromBean(message) + message.getEstimatedCount());
    getCache().put(hashFromBean(message), message);
  }

  @Override
  public boolean processMessage(String address, byte[] buff) {
    // Handled by Queue
    return false;
  }
  private String hash (String vid, String routeId, String direction){
    return vid + routeId + direction;
  }
  private String hashFromBean (NycVehicleLoadBean b){
    String h = hash(b.getVehicleId() , b.getRoute() , b.getDirection());
    return h;
  }

  //Unit Testing method
  void initializeCache(){
    this.refreshCache();
  }
}
