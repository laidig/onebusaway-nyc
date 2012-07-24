package org.onebusaway.nyc.admin.util;

import java.util.List;

import org.onebusaway.nyc.admin.model.ui.VehicleStatus;

/**
 * Caches vehicle records fetched from TDM and operational API web services 
 * @author abelsare
 *
 */
public class VehicleStatusCache {
	
	private List<VehicleStatus> records;
	
	/**
	 * Add new records to the cache
	 * @param newRecords
	 */
	public void add(List<VehicleStatus> newRecords) {
		this.records = newRecords;
	}
	
	/**
	 * Retrieve the records present in the cache
	 * @return the records currently present in the cache
	 */
	public List<VehicleStatus> fetch() {
		return records;
	}

}
