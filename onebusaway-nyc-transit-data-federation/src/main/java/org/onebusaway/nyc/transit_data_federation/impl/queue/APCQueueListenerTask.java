package org.onebusaway.nyc.transit_data_federation.impl.queue;

import javax.annotation.PreDestroy;

import org.onebusaway.container.refresh.Refreshable;
import org.onebusaway.nyc.queue.QueueListenerTask;
import org.onebusaway.nyc.transit_data.model.NycVehicleLoadBean;



public abstract class APCQueueListenerTask extends QueueListenerTask {
  
  protected abstract void processResult(NycVehicleLoadBean message, String contents);
  
  @Override
  public boolean processMessage(String address, byte[] buff) {
    String contents = new String(buff);
    try {
      if (address == null || !address.equals(getQueueName())) {
        return false;
      }

      
      NycVehicleLoadBean inferredResult = _mapper.readValue(contents, NycVehicleLoadBean.class);
      processResult(inferredResult, contents);
      
      return true;
    } catch (Exception e) {
      _log.warn("Received corrupted message from queue; discarding: " + e.getMessage(), e);
      _log.warn("Contents=" + contents);
      return false;
    }
  }
  
  @PreDestroy
  public void destroy() {
    super.destroy();
  }

     @Override
    public String getQueueHost() {
      return _configurationService.getConfigurationValueAsString("tds.APCQueueHost", null);
    }

    @Override
    public String getQueueName() {
      return _configurationService.getConfigurationValueAsString("tds.APCQueueName", "apc");
    }

    @Override
    public Integer getQueuePort() {
      //TODO: confirm queue port num
      return _configurationService.getConfigurationValueAsInteger("tds.APCQueueOutputPort", 5577);
    }

    @Override
    public String getQueueDisplayName() {
      return "APC";
    }

    private String disable = "false";
    public void setDisable(String disable) {
      this.disable = disable;
    }

    @Refreshable(dependsOn = { "display.useAPC" })
    public Boolean useAPCIfAvailable() {
      if (Boolean.TRUE.equals(Boolean.parseBoolean(disable))) return false;
      return Boolean.parseBoolean(_configurationService.getConfigurationValueAsString("display.useTimeAPC", "false"));
    }

    @Refreshable(dependsOn = { "tds.APCQueueHost", "tds.APCQueuePort", "tds.APCQueueName" })
    public void startListenerThread() {
      if (_initialized == true) {
        _log.warn("Configuration service tried to reconfigure APC input queue reader; this service is not reconfigurable once started.");
        return;
      }

      if (!useAPCIfAvailable()) {
        _log.error("APC disabled -- exiting");
        return;
      }

      String host = getQueueHost();
      String queueName = getQueueName();
      Integer port = getQueuePort();

      if (host == null) {
        _log.info("APC input queue is not attached; input hostname was not available via configuration service.");
        return;
      }

      _log.info("APC input queue listening on " + host + ":" + port + ", queue=" + queueName);

      try {
        initializeQueue(host, queueName, port);
      } catch (InterruptedException ie) {
        return;
      }

      _initialized = true;
    }
}