/*
 * Copyright 2010, OpenPlans Licensed under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package org.onebusaway.nyc.presentation.impl.realtime;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.datatype.DatatypeConfigurationException;

import org.apache.commons.lang.StringUtils;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.nyc.presentation.impl.AgencySupportLibrary;
import org.onebusaway.nyc.presentation.impl.DateUtil;
import org.onebusaway.nyc.presentation.service.realtime.PresentationService;
import org.onebusaway.nyc.transit_data.services.NycTransitDataService;
import org.onebusaway.nyc.transit_data_federation.siri.SiriDistanceExtension;
import org.onebusaway.nyc.transit_data_federation.siri.SiriExtensionWrapper;
import org.onebusaway.realtime.api.TimepointPredictionRecord;
import org.onebusaway.transit_data.model.RouteBean;
import org.onebusaway.transit_data.model.StopBean;
import org.onebusaway.transit_data.model.blocks.BlockInstanceBean;
import org.onebusaway.transit_data.model.blocks.BlockStopTimeBean;
import org.onebusaway.transit_data.model.blocks.BlockTripBean;
import org.onebusaway.transit_data.model.service_alerts.ServiceAlertBean;
import org.onebusaway.transit_data.model.trips.TripBean;
import org.onebusaway.transit_data.model.trips.TripStatusBean;

import uk.org.siri.siri_2.AnnotatedStopPointStructure;
import uk.org.siri.siri_2.BlockRefStructure;
import uk.org.siri.siri_2.DataFrameRefStructure;
import uk.org.siri.siri_2.DestinationRefStructure;
import uk.org.siri.siri_2.DirectionRefStructure;
import uk.org.siri.siri_2.ExtensionsStructure;
import uk.org.siri.siri_2.FramedVehicleJourneyRefStructure;
import uk.org.siri.siri_2.JourneyPatternRefStructure;
import uk.org.siri.siri_2.JourneyPlaceRefStructure;
import uk.org.siri.siri_2.LineDirectionStructure;
import uk.org.siri.siri_2.LineRefStructure;
import uk.org.siri.siri_2.LocationStructure;
import uk.org.siri.siri_2.MonitoredCallStructure;
import uk.org.siri.siri_2.MonitoredVehicleJourneyStructure;
import uk.org.siri.siri_2.NaturalLanguageStringStructure;
import uk.org.siri.siri_2.OnwardCallStructure;
import uk.org.siri.siri_2.OnwardCallsStructure;
import uk.org.siri.siri_2.OperatorRefStructure;
import uk.org.siri.siri_2.ProgressRateEnumeration;
import uk.org.siri.siri_2.SituationRefStructure;
import uk.org.siri.siri_2.SituationSimpleRefStructure;
import uk.org.siri.siri_2.StopPointRefStructure;
import uk.org.siri.siri_2.VehicleRefStructure;
import uk.org.siri.siri_2.AnnotatedStopPointStructure.Lines;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class SiriSupportV2 {
	
	private static Logger _log = LoggerFactory.getLogger(SiriSupportV2.class);

	public enum OnwardCallsMode {
		VEHICLE_MONITORING,
		STOP_MONITORING
	}
	
	public enum DetailLevel {
		MINIMUM,
		BASIC,
		NORMAL,
		CALLS;
	}
	
	

	/**
	 * NOTE: The tripDetails bean here may not be for the trip the vehicle is currently on 
	 * in the case of A-D for stop!
	 */
	public static void fillMonitoredVehicleJourney(MonitoredVehicleJourneyStructure monitoredVehicleJourney, 
			TripBean framedJourneyTripBean, TripStatusBean currentVehicleTripStatus, StopBean monitoredCallStopBean, OnwardCallsMode onwardCallsMode,
			PresentationService presentationService, NycTransitDataService nycTransitDataService,
			int maximumOnwardCalls, List<TimepointPredictionRecord> stopLevelPredictions, long responseTimestamp) {
		BlockInstanceBean blockInstance = 
				nycTransitDataService.getBlockInstance(currentVehicleTripStatus.getActiveTrip().getBlockId(), currentVehicleTripStatus.getServiceDate());

		List<BlockTripBean> blockTrips = blockInstance.getBlockConfiguration().getTrips();

		if(monitoredCallStopBean == null) {
			monitoredCallStopBean = currentVehicleTripStatus.getNextStop();
		}
		
		/////////////

		LineRefStructure lineRef = new LineRefStructure();
		lineRef.setValue(framedJourneyTripBean.getRoute().getId());
		monitoredVehicleJourney.setLineRef(lineRef);

		OperatorRefStructure operatorRef = new OperatorRefStructure();
		operatorRef.setValue(AgencySupportLibrary.getAgencyForId(framedJourneyTripBean.getRoute().getId()));
		monitoredVehicleJourney.setOperatorRef(operatorRef);

		DirectionRefStructure directionRef = new DirectionRefStructure();
		directionRef.setValue(framedJourneyTripBean.getDirectionId());
		monitoredVehicleJourney.setDirectionRef(directionRef);

		NaturalLanguageStringStructure routeShortName = new NaturalLanguageStringStructure();
		routeShortName.setValue(framedJourneyTripBean.getRoute().getShortName());
		monitoredVehicleJourney.getPublishedLineName().add(routeShortName);

		JourneyPatternRefStructure journeyPattern = new JourneyPatternRefStructure();
		journeyPattern.setValue(framedJourneyTripBean.getShapeId());
		monitoredVehicleJourney.setJourneyPatternRef(journeyPattern);

		NaturalLanguageStringStructure headsign = new NaturalLanguageStringStructure();
		headsign.setValue(framedJourneyTripBean.getTripHeadsign());
		monitoredVehicleJourney.getDestinationName().add(headsign);

		VehicleRefStructure vehicleRef = new VehicleRefStructure();
		vehicleRef.setValue(currentVehicleTripStatus.getVehicleId());
		monitoredVehicleJourney.setVehicleRef(vehicleRef);

		monitoredVehicleJourney.setMonitored(currentVehicleTripStatus.isPredicted());

		monitoredVehicleJourney.setBearing((float)currentVehicleTripStatus.getOrientation());

		monitoredVehicleJourney.setProgressRate(getProgressRateForPhaseAndStatus(
				currentVehicleTripStatus.getStatus(), currentVehicleTripStatus.getPhase()));

		// origin-destination
		for(int i = 0; i < blockTrips.size(); i++) {
			BlockTripBean blockTrip = blockTrips.get(i);

			if(blockTrip.getTrip().getId().equals(framedJourneyTripBean.getId())) {
				List<BlockStopTimeBean> stops = blockTrip.getBlockStopTimes();
				
				JourneyPlaceRefStructure origin = new JourneyPlaceRefStructure();
				origin.setValue(stops.get(0).getStopTime().getStop().getId());
				monitoredVehicleJourney.setOriginRef(origin);
				
				StopBean lastStop = stops.get(stops.size() - 1).getStopTime().getStop();
				DestinationRefStructure dest = new DestinationRefStructure();
				dest.setValue(lastStop.getId());
				monitoredVehicleJourney.setDestinationRef(dest);
				
				break;
			}
		}

		// framed journey 
		FramedVehicleJourneyRefStructure framedJourney = new FramedVehicleJourneyRefStructure();
		DataFrameRefStructure dataFrame = new DataFrameRefStructure();
		dataFrame.setValue(String.format("%1$tY-%1$tm-%1$td", currentVehicleTripStatus.getServiceDate()));
		framedJourney.setDataFrameRef(dataFrame);
		framedJourney.setDatedVehicleJourneyRef(framedJourneyTripBean.getId());
		monitoredVehicleJourney.setFramedVehicleJourneyRef(framedJourney);

		// location
		// if vehicle is detected to be on detour, use actual lat/lon, not snapped location.
		LocationStructure location = new LocationStructure();

		DecimalFormat df = new DecimalFormat();
		df.setMaximumFractionDigits(6);

		if (presentationService.isOnDetour(currentVehicleTripStatus)) {
			location.setLatitude(new BigDecimal(df.format(currentVehicleTripStatus.getLastKnownLocation().getLat())));
			location.setLongitude(new BigDecimal(df.format(currentVehicleTripStatus.getLastKnownLocation().getLon())));
		} else {
			location.setLatitude(new BigDecimal(df.format(currentVehicleTripStatus.getLocation().getLat())));
			location.setLongitude(new BigDecimal(df.format(currentVehicleTripStatus.getLocation().getLon())));
		}

		monitoredVehicleJourney.setVehicleLocation(location);

		// progress status
		List<String> progressStatuses = new ArrayList<String>();

		if (presentationService.isInLayover(currentVehicleTripStatus)) {
			progressStatuses.add("layover");
		}

		// "prevTrip" really means not on the framedvehiclejourney trip
		if(!framedJourneyTripBean.getId().equals(currentVehicleTripStatus.getActiveTrip().getId())) {
			progressStatuses.add("prevTrip");
		}

		if(!progressStatuses.isEmpty()) {
			NaturalLanguageStringStructure progressStatus = new NaturalLanguageStringStructure();
			progressStatus.setValue(StringUtils.join(progressStatuses, ","));
			monitoredVehicleJourney.getProgressStatus().add(progressStatus);    	
		}

		// block ref
		if (presentationService.isBlockLevelInference(currentVehicleTripStatus)) {
			BlockRefStructure blockRef = new BlockRefStructure();
			blockRef.setValue(framedJourneyTripBean.getBlockId());
			monitoredVehicleJourney.setBlockRef(blockRef);
		}

		// scheduled depature time
		if (presentationService.isBlockLevelInference(currentVehicleTripStatus) 
				&& (presentationService.isInLayover(currentVehicleTripStatus) 
				|| !framedJourneyTripBean.getId().equals(currentVehicleTripStatus.getActiveTrip().getId()))) {
			BlockStopTimeBean originDepartureStopTime = null;

			for(int t = 0; t < blockTrips.size(); t++) {
				BlockTripBean thisTrip = blockTrips.get(t);
				BlockTripBean nextTrip = null;    		
				if(t + 1 < blockTrips.size()) {
					nextTrip = blockTrips.get(t + 1);
				}

				if(thisTrip.getTrip().getId().equals(currentVehicleTripStatus.getActiveTrip().getId())) {    			
					// just started new trip
					if(currentVehicleTripStatus.getDistanceAlongTrip() < (0.5 * currentVehicleTripStatus.getTotalDistanceAlongTrip())) {
						originDepartureStopTime = thisTrip.getBlockStopTimes().get(0);

					// at end of previous trip
					} else {
						if(nextTrip != null) {
							originDepartureStopTime = nextTrip.getBlockStopTimes().get(0);
						}
					}

					break;
				}
			}

			if(originDepartureStopTime != null) {
				long departureTime = currentVehicleTripStatus.getServiceDate() + (originDepartureStopTime.getStopTime().getDepartureTime() * 1000);
					monitoredVehicleJourney.setOriginAimedDepartureTime(DateUtil.toXmlGregorianCalendar(departureTime));
			}
		}    
		
		Map<String, TimepointPredictionRecord> stopIdToPredictionRecordMap = new HashMap<String, TimepointPredictionRecord>();

		// (build map of stop IDs to TPRs)
		if(stopLevelPredictions != null) {
			for(TimepointPredictionRecord tpr : stopLevelPredictions) {
				stopIdToPredictionRecordMap.put(AgencyAndId.convertToString(tpr.getTimepointId()), tpr);
			}
		}
		
		// monitored call
		if(!presentationService.isOnDetour(currentVehicleTripStatus))
			fillMonitoredCall(monitoredVehicleJourney, blockInstance, currentVehicleTripStatus, monitoredCallStopBean, 
				presentationService, nycTransitDataService, stopIdToPredictionRecordMap, responseTimestamp);

		// onward calls
		if(!presentationService.isOnDetour(currentVehicleTripStatus))
			fillOnwardCalls(monitoredVehicleJourney, blockInstance, framedJourneyTripBean, currentVehicleTripStatus, onwardCallsMode,
				presentationService, nycTransitDataService, stopIdToPredictionRecordMap, maximumOnwardCalls, responseTimestamp);

		// situations
		fillSituations(monitoredVehicleJourney, currentVehicleTripStatus);

		return;
	}
	
	public static void fillAnnotatedStopPointStructure(AnnotatedStopPointStructure annotatedStopPoint, StopBean stopBean, String detailLevel, long currentTime){
		//Set Stop Name
		NaturalLanguageStringStructure stopName = new NaturalLanguageStringStructure();
		stopName.setValue(stopBean.getName());
		
		
		// Set Route and Direction
		Lines lines = new Lines();
		DirectionRefStructure direction = new DirectionRefStructure();
		direction.setValue(stopBean.getDirection());	
		
		for(RouteBean routeBean : stopBean.getRoutes()){
			LineRefStructure line = new LineRefStructure();
			line.setValue(routeBean.getId());
			
			LineDirectionStructure lineDirection = new LineDirectionStructure();
			lineDirection.setDirectionRef(direction);
			lineDirection.setLineRef(line);
			
			lines.getLineRefOrLineDirection().add(lineDirection);
		}
		
		// Set Lat and Lon
		BigDecimal stopLat = new BigDecimal(stopBean.getLat());
		BigDecimal stopLon = new BigDecimal(stopBean.getLon());
		
		LocationStructure location = new LocationStructure();
		location.setLongitude(stopLon);
		location.setLatitude(stopLat);
		
		//Set StopId
		StopPointRefStructure stopPointRef = new StopPointRefStructure();
		stopPointRef.setValue(stopBean.getId());
		
		//Detail -- minimum
		annotatedStopPoint.getStopName().add(stopName);
		
		// Details -- normal
		if(!detailLevel.equalsIgnoreCase(DetailLevel.MINIMUM.name())){
			annotatedStopPoint.setLocation(location);
			annotatedStopPoint.setLines(lines);
			//TODO - LCARABALLO Always true?
			annotatedStopPoint.setMonitored(true);
		}
		
		annotatedStopPoint.setStopPointRef(stopPointRef);		
	}

	/***
	 * PRIVATE STATIC METHODS
	 */
	private static void fillOnwardCalls(MonitoredVehicleJourneyStructure monitoredVehicleJourney, 
			BlockInstanceBean blockInstance, TripBean framedJourneyTripBean, TripStatusBean currentVehicleTripStatus, OnwardCallsMode onwardCallsMode,
			PresentationService presentationService, NycTransitDataService nycTransitDataService, 
			Map<String, TimepointPredictionRecord> stopLevelPredictions, int maximumOnwardCalls, long responseTimestamp) {

		String tripIdOfMonitoredCall = framedJourneyTripBean.getId();

		monitoredVehicleJourney.setOnwardCalls(new OnwardCallsStructure());

		//////////

		// no need to go further if this is the case!
		if(maximumOnwardCalls == 0) { 
			return;
		}

		List<BlockTripBean> blockTrips = blockInstance.getBlockConfiguration().getTrips();

		double distanceOfVehicleAlongBlock = 0;
		int blockTripStopsAfterTheVehicle = 0; 
		int onwardCallsAdded = 0;

		boolean foundActiveTrip = false;
		for(int i = 0; i < blockTrips.size(); i++) {
			BlockTripBean blockTrip = blockTrips.get(i);

			if(!foundActiveTrip) {
				if(currentVehicleTripStatus.getActiveTrip().getId().equals(blockTrip.getTrip().getId())) {
					distanceOfVehicleAlongBlock += currentVehicleTripStatus.getDistanceAlongTrip();

					foundActiveTrip = true;
				} else {
					// a block trip's distance along block is the *beginning* of that block trip along the block
					// so to get the size of this one, we have to look at the next.
					if(i + 1 < blockTrips.size()) {
						distanceOfVehicleAlongBlock = blockTrips.get(i + 1).getDistanceAlongBlock();
					}

					// bus has already served this trip, so no need to go further
					continue;
				}
			}

			if(onwardCallsMode == OnwardCallsMode.STOP_MONITORING) {
				// always include onward calls for the trip the monitored call is on ONLY.
				if(!blockTrip.getTrip().getId().equals(tripIdOfMonitoredCall)) {
					continue;
				}
			}

			HashMap<String, Integer> visitNumberForStopMap = new HashMap<String, Integer>();	   
			for(BlockStopTimeBean stopTime : blockTrip.getBlockStopTimes()) {
				int visitNumber = getVisitNumber(visitNumberForStopMap, stopTime.getStopTime().getStop());

				// block trip stops away--on this trip, only after we've passed the stop, 
				// on future trips, count always.
				if(currentVehicleTripStatus.getActiveTrip().getId().equals(blockTrip.getTrip().getId())) {
					if(stopTime.getDistanceAlongBlock() >= distanceOfVehicleAlongBlock) {
						blockTripStopsAfterTheVehicle++;
					} else {
						// stop is behind the bus--no need to go further
						continue;
					}

				// future trip--bus hasn't reached this trip yet, so count all stops
				} else {
					blockTripStopsAfterTheVehicle++;
				}

				monitoredVehicleJourney.getOnwardCalls().getOnwardCall().add(
						getOnwardCallStructure(stopTime.getStopTime().getStop(), presentationService, 
								stopTime.getDistanceAlongBlock() - blockTrip.getDistanceAlongBlock(), 
								stopTime.getDistanceAlongBlock() - distanceOfVehicleAlongBlock, 
								visitNumber, blockTripStopsAfterTheVehicle - 1,
								stopLevelPredictions.get(stopTime.getStopTime().getStop().getId()), responseTimestamp));

				onwardCallsAdded++;

				if(onwardCallsAdded >= maximumOnwardCalls) {
					return;
				}
			}

			// if we get here, we added our stops
			return;
		}

		return;
	}

	private static void fillMonitoredCall(MonitoredVehicleJourneyStructure monitoredVehicleJourney, 
			BlockInstanceBean blockInstance, TripStatusBean tripStatus, StopBean monitoredCallStopBean, 
			PresentationService presentationService, NycTransitDataService nycTransitDataService,
			Map<String, TimepointPredictionRecord> stopLevelPredictions, long responseTimestamp) {

		List<BlockTripBean> blockTrips = blockInstance.getBlockConfiguration().getTrips();

		double distanceOfVehicleAlongBlock = 0;
		int blockTripStopsAfterTheVehicle = 0; 

		boolean foundActiveTrip = false;
		for(int i = 0; i < blockTrips.size(); i++) {
			BlockTripBean blockTrip = blockTrips.get(i);

			if(!foundActiveTrip) {
				if(tripStatus.getActiveTrip().getId().equals(blockTrip.getTrip().getId())) {
					distanceOfVehicleAlongBlock += tripStatus.getDistanceAlongTrip();

					foundActiveTrip = true;
				} else {
					// a block trip's distance along block is the *beginning* of that block trip along the block
					// so to get the size of this one, we have to look at the next.
					if(i + 1 < blockTrips.size()) {
						distanceOfVehicleAlongBlock = blockTrips.get(i + 1).getDistanceAlongBlock();
					}

					// bus has already served this trip, so no need to go further
					continue;
				}
			}

			HashMap<String, Integer> visitNumberForStopMap = new HashMap<String, Integer>();	   

			for(BlockStopTimeBean stopTime : blockTrip.getBlockStopTimes()) {
				int visitNumber = getVisitNumber(visitNumberForStopMap, stopTime.getStopTime().getStop());

				// block trip stops away--on this trip, only after we've passed the stop, 
				// on future trips, count always.
				if(tripStatus.getActiveTrip().getId().equals(blockTrip.getTrip().getId())) {
					if(stopTime.getDistanceAlongBlock() >= distanceOfVehicleAlongBlock) {
						blockTripStopsAfterTheVehicle++;
					} else {
						// bus has passed this stop already--no need to go further
						continue;
					}

				// future trip--bus hasn't reached this trip yet, so count all stops
				} else {
					blockTripStopsAfterTheVehicle++;
				}

				// monitored call
				if(stopTime.getStopTime().getStop().getId().equals(monitoredCallStopBean.getId())) {    
					if(!presentationService.isOnDetour(tripStatus)) {
						monitoredVehicleJourney.setMonitoredCall(
								getMonitoredCallStructure(stopTime.getStopTime().getStop(), presentationService, 
										stopTime.getDistanceAlongBlock() - blockTrip.getDistanceAlongBlock(), 
										stopTime.getDistanceAlongBlock() - distanceOfVehicleAlongBlock, 
										visitNumber, blockTripStopsAfterTheVehicle - 1,
										stopLevelPredictions.get(stopTime.getStopTime().getStop().getId()),
										responseTimestamp));
					}

					// we found our monitored call--stop
					return;
				}
			}    	
		}
	}

	private static void fillSituations(MonitoredVehicleJourneyStructure monitoredVehicleJourney, TripStatusBean tripStatus) {
		if (tripStatus == null || tripStatus.getSituations() == null || tripStatus.getSituations().isEmpty()) {
			return;
		}

		List<SituationRefStructure> situationRef = monitoredVehicleJourney.getSituationRef();

		for (ServiceAlertBean situation : tripStatus.getSituations()) {
			SituationRefStructure sitRef = new SituationRefStructure();
			SituationSimpleRefStructure sitSimpleRef = new SituationSimpleRefStructure();
			sitSimpleRef.setValue(situation.getId());
			sitRef.setSituationSimpleRef(sitSimpleRef);
			situationRef.add(sitRef);
		}
	}

	private static OnwardCallStructure getOnwardCallStructure(StopBean stopBean, 
			PresentationService presentationService, 
			double distanceOfCallAlongTrip, double distanceOfVehicleFromCall, int visitNumber, int index,
			TimepointPredictionRecord prediction, long responseTimestamp) {

		OnwardCallStructure onwardCallStructure = new OnwardCallStructure();
		onwardCallStructure.setVisitNumber(BigInteger.valueOf(visitNumber));

		StopPointRefStructure stopPointRef = new StopPointRefStructure();
		stopPointRef.setValue(stopBean.getId());
		onwardCallStructure.setStopPointRef(stopPointRef);

		NaturalLanguageStringStructure stopPoint = new NaturalLanguageStringStructure();
		stopPoint.setValue(stopBean.getName());
		onwardCallStructure.getStopPointName().add(stopPoint);

		if(prediction != null) {
			if (prediction.getTimepointPredictedTime() < responseTimestamp) {
				//TODO - LCARABALLO - should this be setExpectedArrivalTime?
				onwardCallStructure.setExpectedArrivalTime(DateUtil.toXmlGregorianCalendar(responseTimestamp));
				onwardCallStructure.setExpectedDepartureTime(DateUtil.toXmlGregorianCalendar(responseTimestamp));
			} else {
				onwardCallStructure.setExpectedArrivalTime(DateUtil.toXmlGregorianCalendar(prediction.getTimepointPredictedTime()));
				onwardCallStructure.setExpectedDepartureTime(DateUtil.toXmlGregorianCalendar(prediction.getTimepointPredictedTime()));
			}
		}

		// siri extensions
		SiriExtensionWrapper wrapper = new SiriExtensionWrapper();
		ExtensionsStructure distancesExtensions = new ExtensionsStructure();
		SiriDistanceExtension distances = new SiriDistanceExtension();

		DecimalFormat df = new DecimalFormat();
		df.setMaximumFractionDigits(2);
		df.setGroupingUsed(false);

		distances.setStopsFromCall(index);
		distances.setCallDistanceAlongRoute(Double.valueOf(df.format(distanceOfCallAlongTrip)));
		distances.setDistanceFromCall(Double.valueOf(df.format(distanceOfVehicleFromCall)));
		distances.setPresentableDistance(presentationService.getPresentableDistance(distances));

		wrapper.setDistances(distances);
		distancesExtensions.setAny(wrapper);    
		onwardCallStructure.setExtensions(distancesExtensions);

		return onwardCallStructure;
	}

	private static MonitoredCallStructure getMonitoredCallStructure(StopBean stopBean, 
			PresentationService presentationService, 
			double distanceOfCallAlongTrip, double distanceOfVehicleFromCall, int visitNumber, int index,
			TimepointPredictionRecord prediction, long responseTimestamp) {

		MonitoredCallStructure monitoredCallStructure = new MonitoredCallStructure();
		monitoredCallStructure.setVisitNumber(BigInteger.valueOf(visitNumber));

		StopPointRefStructure stopPointRef = new StopPointRefStructure();
		stopPointRef.setValue(stopBean.getId());
		monitoredCallStructure.setStopPointRef(stopPointRef);

		NaturalLanguageStringStructure stopPoint = new NaturalLanguageStringStructure();
		stopPoint.setValue(stopBean.getName());
		monitoredCallStructure.getStopPointName().add(stopPoint);

		if(prediction != null) {
			// do not allow predicted times to be less than ResponseTimestamp
			if (prediction.getTimepointPredictedTime() < responseTimestamp) {
				/*
				 * monitoredCall has less precision than onwardCall (date vs. timestamp)
				 * which results in a small amount of error when converting back to timestamp.
				 * Add a second here to prevent negative values from showing up in the UI 
				 * (actual precision of the value is 1 minute, so a second has little influence)
				 */				
				monitoredCallStructure.setExpectedArrivalTime(DateUtil.toXmlGregorianCalendar(responseTimestamp + 1000)); 
				monitoredCallStructure.setExpectedDepartureTime(DateUtil.toXmlGregorianCalendar(responseTimestamp + 1000));
			} else {
				monitoredCallStructure.setExpectedArrivalTime(DateUtil.toXmlGregorianCalendar(prediction.getTimepointPredictedTime()));
				monitoredCallStructure.setExpectedDepartureTime(DateUtil.toXmlGregorianCalendar(prediction.getTimepointPredictedTime()));
			}
			
		}
		
		// siri extensions
		SiriExtensionWrapper wrapper = new SiriExtensionWrapper();
		ExtensionsStructure distancesExtensions = new ExtensionsStructure();
		SiriDistanceExtension distances = new SiriDistanceExtension();

		DecimalFormat df = new DecimalFormat();
		df.setMaximumFractionDigits(2);
		df.setGroupingUsed(false);

		distances.setStopsFromCall(index);
		distances.setCallDistanceAlongRoute(Double.valueOf(df.format(distanceOfCallAlongTrip)));
		distances.setDistanceFromCall(Double.valueOf(df.format(distanceOfVehicleFromCall)));
		distances.setPresentableDistance(presentationService.getPresentableDistance(distances));

		wrapper.setDistances(distances);
		distancesExtensions.setAny(wrapper);
		monitoredCallStructure.setExtensions(distancesExtensions);

		return monitoredCallStructure;
	}

	private static int getVisitNumber(HashMap<String, Integer> visitNumberForStop, StopBean stop) {
		int visitNumber;

		if (visitNumberForStop.containsKey(stop.getId())) {
			visitNumber = visitNumberForStop.get(stop.getId()) + 1;
		} else {
			visitNumber = 1;
		}

		visitNumberForStop.put(stop.getId(), visitNumber);

		return visitNumber;
	}

	private static ProgressRateEnumeration getProgressRateForPhaseAndStatus(String status, String phase) {
		if (phase == null) {
			return ProgressRateEnumeration.UNKNOWN;
		}

		if (phase.toLowerCase().startsWith("layover")
				|| phase.toLowerCase().startsWith("deadhead")
				|| phase.toLowerCase().equals("at_base")) {
			return ProgressRateEnumeration.NO_PROGRESS;
		}

		if (status != null && status.toLowerCase().equals("stalled")) {
			return ProgressRateEnumeration.NO_PROGRESS;
		}

		if (phase.toLowerCase().equals("in_progress")) {
			return ProgressRateEnumeration.NORMAL_PROGRESS;
		}

		return ProgressRateEnumeration.UNKNOWN;
	}

}
