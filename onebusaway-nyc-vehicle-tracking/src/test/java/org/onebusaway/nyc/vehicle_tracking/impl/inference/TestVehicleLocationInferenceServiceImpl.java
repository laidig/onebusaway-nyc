package org.onebusaway.nyc.vehicle_tracking.impl.inference;

import static org.junit.Assert.*;

import org.onebusaway.gtfs.csv.CsvEntityContext;
import org.onebusaway.gtfs.csv.CsvEntityReader;
import org.onebusaway.gtfs.csv.EntityHandler;
import org.onebusaway.gtfs.csv.exceptions.CsvEntityException;
import org.onebusaway.gtfs.csv.exceptions.CsvEntityIOException;
import org.onebusaway.gtfs.csv.schema.AbstractFieldMapping;
import org.onebusaway.gtfs.csv.schema.BeanWrapper;
import org.onebusaway.gtfs.csv.schema.DefaultEntitySchemaFactory;
import org.onebusaway.gtfs.csv.schema.EntitySchemaFactoryHelper;
import org.onebusaway.gtfs.csv.schema.annotations.CsvFields;
import org.onebusaway.gtfs.csv.schema.beans.CsvEntityMappingBean;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.nyc.vehicle_tracking.services.VehicleLocationInferenceRecord;
import org.onebusaway.realtime.api.VehicleLocationRecord;

import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Map;

@CsvFields(filename = "ivn-dsc.csv", fieldOrder = {
    "vehicleId", "date", "time", "lat", "lon",
    "timestamp", "dsc", "new_dsc"})
class NycTestLocationRecord {
  private String vehicleId;
  
  private double lat;
  private double lon;
  private long timestamp;
  private String dsc;
  
  public void setVehicleId(String vehicleId) {
    this.vehicleId = vehicleId;
  }
  public String getVehicleId() {
    return vehicleId;
  }
  public void setLat(double lat) {
    this.lat = lat;
  }
  public double getLat() {
    return lat;
  }
  public void setLon(double lon) {
    this.lon = lon;
  }
  public double getLon() {
    return lon;
  }
  public void setTimestamp(String timestamp) {
    SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd' 'HH:mm:ss");
    try {
      this.timestamp = sdf.parse(timestamp).getTime();
    } catch (ParseException e) {
      throw new RuntimeException("error parsing datetime " + timestamp, e);
    }
  }
  public long getTimestamp() {
    return timestamp;
  }
  public void setDsc(String dsc) {
    this.dsc = dsc;
  }
  public String getDsc() {
    return dsc;
  } 
}

public class TestVehicleLocationInferenceServiceImpl {

  @Test
  public void testInference() throws CsvEntityIOException, IOException, InterruptedException {
    final VehicleLocationInferenceServiceImpl service;
    service = new VehicleLocationInferenceServiceImpl();
    service.start();
    CsvEntityReader reader = new CsvEntityReader();

    DefaultEntitySchemaFactory factory = new DefaultEntitySchemaFactory();
    EntitySchemaFactoryHelper helper = new EntitySchemaFactoryHelper(factory);

    CsvEntityMappingBean record = helper.addEntity(NycTestLocationRecord.class);
    record.setAutoGenerateSchema(false);
    reader.setEntitySchemaFactory(factory);
    record.addAdditionalFieldMapping(new AbstractFieldMapping(NycTestLocationRecord.class, "", "", true) {
      
      @Override
      public void translateFromObjectToCSV(CsvEntityContext context,
          BeanWrapper object, Map<String, Object> csvValues)
          throws CsvEntityException {
        /* don't bother */
      }
      
      @Override
      public void translateFromCSVToObject(CsvEntityContext context,
          Map<String, Object> csvValues, BeanWrapper object)
          throws CsvEntityException {
        
        NycTestLocationRecord record = object.getWrappedInstance(NycTestLocationRecord.class);
        record.setDsc(csvValues.get("dsc").toString());
        record.setTimestamp(csvValues.get("dt").toString());
        record.setVehicleId(csvValues.get("vid").toString());
        record.setLat(Double.parseDouble(csvValues.get("lat").toString()));
        record.setLon(Double.parseDouble(csvValues.get("lon").toString()));
      } 
    });

    reader.addEntityHandler(new EntityHandler() {

      @Override
      public void handleEntity(Object bean) {
        NycTestLocationRecord record = (NycTestLocationRecord) bean;

        VehicleLocationInferenceRecord location = new VehicleLocationInferenceRecord();
        location.setTimestamp(record.getTimestamp());
        assertNotNull(record.getVehicleId());
        location.setVehicleId(new AgencyAndId("MTA NYCT", record.getVehicleId()));
        location.setDestinationSignCode(record.getDsc());
        location.setLat(record.getLat());
        location.setLon(record.getLon());

        service.handleVehicleLocation(location);
      }
    });
    
    InputStream in = getClass().getResourceAsStream("ivn-dsc.csv");
    assertNotNull(in);
    reader.readEntities(NycTestLocationRecord.class, in);

    /* wait a max of two seconds for processing to finish */
    VehicleLocationRecord lastLocation = null;
    for (int i = 0; i < 20; ++i) {
      Thread.sleep(100); //wait for processing to finish (but not very long)
      List<VehicleLocationRecord> records = service.getLatestProcessedVehicleLocationRecords();
      assertEquals(1, records.size());
      lastLocation = records.get(0);
      if (Math.abs(40.717632 - lastLocation.getCurrentLocationLat()) < 0.0000001) {
        break;
      }
    }
    assertEquals(40.717632, lastLocation.getCurrentLocationLat(), 0.0000001);
    assertEquals(-73.920038, lastLocation.getCurrentLocationLon(), 0.0000001);
  }

}
