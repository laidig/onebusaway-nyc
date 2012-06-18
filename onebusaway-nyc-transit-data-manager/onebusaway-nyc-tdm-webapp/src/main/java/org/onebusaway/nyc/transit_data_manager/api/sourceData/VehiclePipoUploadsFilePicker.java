package org.onebusaway.nyc.transit_data_manager.api.sourceData;

import java.io.IOException;

public class VehiclePipoUploadsFilePicker extends DateUbarTimeTimestampFilePicker {

	private static String FILE_PREFIX = "UTSPUPUFULL_";
	private static String FILE_SUFFIX = ".txt";
	  
	public VehiclePipoUploadsFilePicker(String timestampedUploadsDir)
			throws IOException {
		super(timestampedUploadsDir);
	}

	@Override
	protected String getFilePrefix() {
		return FILE_PREFIX;
	}

	@Override
	protected String getFileSuffix() {
		return FILE_SUFFIX;
	}

}
