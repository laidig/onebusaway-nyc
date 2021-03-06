/**
 * Copyright (c) 2011 Metropolitan Transportation Authority
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package org.onebusaway.nyc.transit_data_manager.siri;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.PostConstruct;
import javax.xml.bind.JAXBException;

import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang.StringUtils;
import org.onebusaway.collections.CollectionsLibrary;
import org.onebusaway.gtfs.model.AgencyAndId;
import org.onebusaway.nyc.presentation.impl.service_alerts.ServiceAlertsHelper;
import org.onebusaway.nyc.siri.support.SiriXmlSerializer;
import org.onebusaway.nyc.transit_data.services.NycTransitDataService;
import org.onebusaway.nyc.transit_data_manager.util.NycEnvironment;
import org.onebusaway.siri.AffectedApplicationStructure;
import org.onebusaway.siri.OneBusAwayAffects;
import org.onebusaway.siri.OneBusAwayAffectsStructure.Applications;
import org.onebusaway.siri.OneBusAwayConsequence;
import org.onebusaway.siri.core.ESiriModuleType;
import org.onebusaway.transit_data.model.service_alerts.EEffect;
import org.onebusaway.transit_data.model.service_alerts.ESeverity;
import org.onebusaway.transit_data.model.service_alerts.NaturalLanguageStringBean;
import org.onebusaway.transit_data.model.service_alerts.ServiceAlertBean;
import org.onebusaway.transit_data.model.service_alerts.SituationAffectsBean;
import org.onebusaway.transit_data.model.service_alerts.SituationConsequenceBean;
import org.onebusaway.transit_data.model.service_alerts.TimeRangeBean;
import org.onebusaway.transit_data_federation.impl.realtime.siri.SiriEndpointDetails;
import org.onebusaway.transit_data_federation.services.AgencyAndIdLibrary;
import org.onebusaway.transit_data_federation.services.service_alerts.ServiceAlerts.TranslatedString;
import org.onebusaway.transit_data_federation.services.service_alerts.ServiceAlerts.TranslatedString.Translation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import uk.org.siri.siri.AbstractServiceDeliveryStructure;
import uk.org.siri.siri.AffectedCallStructure;
import uk.org.siri.siri.AffectedOperatorStructure;
import uk.org.siri.siri.AffectedStopPointStructure;
import uk.org.siri.siri.AffectedVehicleJourneyStructure;
import uk.org.siri.siri.AffectedVehicleJourneyStructure.Calls;
import uk.org.siri.siri.AffectsScopeStructure;
import uk.org.siri.siri.AffectsScopeStructure.Operators;
import uk.org.siri.siri.AffectsScopeStructure.StopPoints;
import uk.org.siri.siri.AffectsScopeStructure.VehicleJourneys;
import uk.org.siri.siri.DefaultedTextStructure;
import uk.org.siri.siri.EntryQualifierStructure;
import uk.org.siri.siri.ExtensionsStructure;
import uk.org.siri.siri.HalfOpenTimestampRangeStructure;
import uk.org.siri.siri.OperatorRefStructure;
import uk.org.siri.siri.PtConsequenceStructure;
import uk.org.siri.siri.PtConsequencesStructure;
import uk.org.siri.siri.PtSituationElementStructure;
import uk.org.siri.siri.ServiceConditionEnumeration;
import uk.org.siri.siri.ServiceDelivery;
import uk.org.siri.siri.ServiceRequest;
import uk.org.siri.siri.SeverityEnumeration;
import uk.org.siri.siri.Siri;
import uk.org.siri.siri.SituationExchangeDeliveryStructure;
import uk.org.siri.siri.SituationExchangeDeliveryStructure.Situations;
import uk.org.siri.siri.SituationExchangeRequestStructure;
import uk.org.siri.siri.SituationExchangeSubscriptionStructure;
import uk.org.siri.siri.StatusResponseStructure;
import uk.org.siri.siri.StopPointRefStructure;
import uk.org.siri.siri.SubscriptionQualifierStructure;
import uk.org.siri.siri.SubscriptionRequest;
import uk.org.siri.siri.SubscriptionResponseStructure;
import uk.org.siri.siri.VehicleJourneyRefStructure;
import uk.org.siri.siri.WorkflowStatusEnumeration;

@SuppressWarnings("restriction")
@Component
public abstract class NycSiriService {

  static final Logger _log = LoggerFactory.getLogger(NycSiriService.class);

  public static final String ALL_OPERATORS = "__ALL_OPERATORS__";

  @Autowired
  private NycTransitDataService _nycTransitDataService;

  private String _serviceAlertsUrl;

  private String _subscriptionPath;

  private WebResourceWrapper _webResourceWrapper;

  private String _subscriptionUrl;

  private SiriXmlSerializer _siriXmlSerializer = new SiriXmlSerializer();

  protected NycEnvironment _environment = new NycEnvironment();

  abstract void setupForMode() throws Exception, JAXBException;

  abstract List<String> getExistingAlertIds(Set<String> agencies);

  abstract void removeServiceAlert(SituationExchangeResults result,
      DeliveryResult deliveryResult, String serviceAlertId);

  abstract void addOrUpdateServiceAlert(SituationExchangeResults result,
      DeliveryResult deliveryResult, ServiceAlertBean serviceAlertBean,
      String defaultAgencyId);

  abstract void postServiceDeliveryActions(SituationExchangeResults result,
      Collection<String> deletedIds) throws Exception;

  abstract void addSubscription(ServiceAlertSubscription subscription);

  abstract public List<ServiceAlertSubscription> getActiveServiceAlertSubscriptions();

  abstract public SiriServicePersister getPersister();

  abstract public void setPersister(SiriServicePersister _siriServicePersister);

  abstract public void deleteAllServiceAlerts();

  @PostConstruct
  public void setup() {
    _log.info("setup(), serviceAlertsUrl is: " + _serviceAlertsUrl);
    try {
      setupForMode();
    } catch (Exception e) {
      _log.error("********************\n"
          + "NycSiriService failed to start, message is: " + e.getMessage()
          + "\n********************");
    }
  }

  public void handleServiceDeliveries(SituationExchangeResults result,
      ServiceDelivery delivery) throws Exception {
    Set<String> incomingAgencies = collectAgencies(delivery);
    List<String> preAlertIds = getExistingAlertIds(incomingAgencies);

    for (SituationExchangeDeliveryStructure s : delivery.getSituationExchangeDelivery()) {
      SiriEndpointDetails endpointDetails = new SiriEndpointDetails();
      handleServiceDelivery(delivery, s, ESiriModuleType.SITUATION_EXCHANGE,
          endpointDetails, result, preAlertIds);
    }

    List<String> postAlertIds = getExistingAlertIds(incomingAgencies);
    @SuppressWarnings("unchecked")
    Collection<String> deletedIds = CollectionUtils.subtract(preAlertIds,
        postAlertIds);
    postServiceDeliveryActions(result, deletedIds);

  }

  private Set<String> collectAgencies(ServiceDelivery delivery) {
    Set<String> agencies = new HashSet<String>();
    for (SituationExchangeDeliveryStructure s : delivery.getSituationExchangeDelivery()) {
      Situations situations = s.getSituations();
      for (PtSituationElementStructure element : situations.getPtSituationElement()) {
        String situationId = element.getSituationNumber().getValue();
        AgencyAndId id = AgencyAndIdLibrary.convertFromString(situationId);
        if (id != null)
          agencies.add(id.getAgencyId());
      }
    }
    return agencies;
  }

  public synchronized void handleServiceDelivery(
      ServiceDelivery serviceDelivery,
      AbstractServiceDeliveryStructure deliveryForModule,
      ESiriModuleType moduleType, SiriEndpointDetails endpointDetails,
      SituationExchangeResults result, List<String> preAlertIds) {

    handleSituationExchange(serviceDelivery,
        (SituationExchangeDeliveryStructure) deliveryForModule,
        endpointDetails, result, preAlertIds);
  }

  void handleSituationExchange(ServiceDelivery serviceDelivery,
      SituationExchangeDeliveryStructure sxDelivery,
      SiriEndpointDetails endpointDetails, SituationExchangeResults result,
      List<String> preAlertIds) {

    DeliveryResult deliveryResult = new DeliveryResult();
    result.getDelivery().add(deliveryResult);

    Situations situations = sxDelivery.getSituations();

    if (situations == null)
      return;

    List<ServiceAlertBean> serviceAlertsToUpdate = new ArrayList<ServiceAlertBean>();
    List<String> serviceAlertIdsToRemove = new ArrayList<String>();

    deleteAllServiceAlerts();

    for (PtSituationElementStructure ptSituation : situations.getPtSituationElement()) {

      ServiceAlertBean serviceAlertBean = getPtSituationAsServiceAlertBean(
          ptSituation, endpointDetails);

      String id = serviceAlertBean.getId();
      if (StringUtils.isEmpty(id)) {
        _log.warn("Service alert has no id, discarding.");
        continue;
      }
      WorkflowStatusEnumeration progress = ptSituation.getProgress();
      boolean remove = (progress != null && (progress == WorkflowStatusEnumeration.CLOSING || progress == WorkflowStatusEnumeration.CLOSED));

      if (remove) {
        serviceAlertIdsToRemove.add(id);
      } else {
        serviceAlertsToUpdate.add(serviceAlertBean);
        preAlertIds.remove(id);
      }

    }

    for (String id: preAlertIds) {
      serviceAlertIdsToRemove.add(id);
    }
    
    String defaultAgencyId = null;
    if (!CollectionsLibrary.isEmpty(endpointDetails.getDefaultAgencyIds()))
      defaultAgencyId = endpointDetails.getDefaultAgencyIds().get(0);

    for (ServiceAlertBean serviceAlertBean : serviceAlertsToUpdate) {
      addOrUpdateServiceAlert(result, deliveryResult, serviceAlertBean,
          defaultAgencyId);
    }
    for (String serviceAlertId : serviceAlertIdsToRemove) {
      removeServiceAlert(result, deliveryResult, serviceAlertId);
    }
  }

  public void handleServiceRequests(ServiceRequest serviceRequest,
      Siri responseSiri) {
    List<SituationExchangeRequestStructure> requests = serviceRequest.getSituationExchangeRequest();
    for (SituationExchangeRequestStructure request : requests) {
      handleServiceRequest(request, responseSiri);
    }
  }

  private void handleServiceRequest(SituationExchangeRequestStructure request,
      Siri responseSiri) {
    ServiceAlertsHelper helper = new ServiceAlertsHelper();
    ServiceDelivery serviceDelivery = new ServiceDelivery();
    helper.addSituationExchangeToServiceDelivery(serviceDelivery,
        getPersister().getAllActiveServiceAlerts());
    responseSiri.setServiceDelivery(serviceDelivery);
    return;
  }

  public void handleSubscriptionRequests(
      SubscriptionRequest subscriptionRequests, Siri responseSiri) {
    String address = subscriptionRequests.getAddress();
    List<SituationExchangeSubscriptionStructure> requests = subscriptionRequests.getSituationExchangeSubscriptionRequest();
    for (SituationExchangeSubscriptionStructure request : requests) {
      handleSubscriptionRequest(request, responseSiri, address);
    }
  }

  private void handleSubscriptionRequest(
      SituationExchangeSubscriptionStructure request, Siri responseSiri,
      String address) {
    boolean status = true;
    String errorMessage = null;
    String subscriptionRef = UUID.randomUUID().toString();
    try {
      ServiceAlertSubscription subscription = new ServiceAlertSubscription();
      subscription.setAddress(address);
      subscription.setCreatedAt(new Date());
      if (request.getSubscriptionIdentifier() == null)
        throw new RuntimeException(
            "required element missing: subscriptionIdentifier");
      subscription.setSubscriptionIdentifier(request.getSubscriptionIdentifier().getValue());
      subscription.setSubscriptionRef(subscriptionRef);
      addSubscription(subscription);
    } catch (Exception e) {
      errorMessage = "Failed to create service alert subscription: "
          + e.getMessage();
      _log.error(errorMessage);
      status = false;
    }

    createSubscriptionSiriResponse(responseSiri, status, errorMessage,
        subscriptionRef);
    return;
  }

  private void createSubscriptionSiriResponse(Siri responseSiri,
      boolean status, String errorMessage, String subscriptionRef) {
    SubscriptionResponseStructure response = new SubscriptionResponseStructure();
    StatusResponseStructure statusResponseStructure = SiriHelper.createStatusResponseStructure(
        status, errorMessage);
    SubscriptionQualifierStructure subscriptionQualifierStructure = new SubscriptionQualifierStructure();
    subscriptionQualifierStructure.setValue(subscriptionRef);
    statusResponseStructure.setSubscriptionRef(subscriptionQualifierStructure);
    response.getResponseStatus().add(statusResponseStructure);
    responseSiri.setSubscriptionResponse(response);
  }

  ServiceAlertBean getPtSituationAsServiceAlertBean(
      PtSituationElementStructure ptSituation,
      SiriEndpointDetails endpointDetails) {
    ServiceAlertBean serviceAlert = new ServiceAlertBean();
    try {
      EntryQualifierStructure serviceAlertNumber = ptSituation.getSituationNumber();
      String situationId = StringUtils.trim(serviceAlertNumber.getValue());

      if (!endpointDetails.getDefaultAgencyIds().isEmpty()) {
        String agencyId = endpointDetails.getDefaultAgencyIds().get(0);
        serviceAlert.setId(AgencyAndId.convertToString(new AgencyAndId(
            agencyId, situationId)));
      } else {
        AgencyAndId id = AgencyAndIdLibrary.convertFromString(situationId);
        serviceAlert.setId(AgencyAndId.convertToString(id));
      }

      if (ptSituation.getCreationTime() != null)
        serviceAlert.setCreationTime(ptSituation.getCreationTime().getTime());
      
      handleDescriptions(ptSituation, serviceAlert);
      handleOtherFields(ptSituation, serviceAlert);
      // TODO not yet implemented
      // handlReasons(ptSituation, serviceAlert);
      handleAffects(ptSituation, serviceAlert);
      handleConsequences(ptSituation, serviceAlert);
    } catch (Exception e) {
      _log.error("Failed to convert SIRI to service alert: " + e.getMessage());
    }

    return serviceAlert;
  }

  private void handleDescriptions(PtSituationElementStructure ptSituation,
      ServiceAlertBean serviceAlert) {
    TranslatedString summary = translation(ptSituation.getSummary());
    if (summary != null)
      serviceAlert.setSummaries(naturalLanguageStringBeanFromTranslatedString(summary));

    TranslatedString description = translation(ptSituation.getDescription());
    if (description != null)
      serviceAlert.setDescriptions(naturalLanguageStringBeanFromTranslatedString(description));
  }

  private List<NaturalLanguageStringBean> naturalLanguageStringBeanFromTranslatedString(
      TranslatedString translatedString) {
    List<NaturalLanguageStringBean> nlsb = new ArrayList<NaturalLanguageStringBean>();
    for (Translation t : translatedString.getTranslationList()) {
      nlsb.add(new NaturalLanguageStringBean(StringUtils.trim(t.getText()),
          StringUtils.trim(t.getLanguage())));
    }
    return nlsb;
  }

  private void handleOtherFields(PtSituationElementStructure ptSituation,
      ServiceAlertBean serviceAlert) {

    SeverityEnumeration severity = ptSituation.getSeverity();
    if (severity != null) {
      ESeverity severityEnum = ESeverity.valueOfTpegCode(StringUtils.trim(severity.value()));
      serviceAlert.setSeverity(severityEnum);
    }

    if (ptSituation.getPublicationWindow() != null) {
      HalfOpenTimestampRangeStructure window = ptSituation.getPublicationWindow();
      TimeRangeBean range = new TimeRangeBean();
      if (window.getStartTime() != null)
        range.setFrom(window.getStartTime().getTime());
      if (window.getEndTime() != null)
        range.setTo(window.getEndTime().getTime());
      if (range.getFrom() != 0 || range.getTo() != 0)
        serviceAlert.setPublicationWindows(Arrays.asList(range));
    }
  }

  @SuppressWarnings("unused")
  private void handlReasons(PtSituationElementStructure ptSituation,
      ServiceAlertBean serviceAlert) {
    throw new RuntimeException("handleReasons not implemented");
  }

  private void handleAffects(PtSituationElementStructure ptSituation,
      ServiceAlertBean serviceAlert) {
    AffectsScopeStructure affectsStructure = ptSituation.getAffects();

    if (affectsStructure == null)
      return;

    List<SituationAffectsBean> allAffects = new ArrayList<SituationAffectsBean>();

    Operators operators = affectsStructure.getOperators();

    if (operators != null) {
      if (operators.getAllOperators() != null) {
        addAffectsOperator(allAffects, ALL_OPERATORS);
      }
      if (!CollectionsLibrary.isEmpty(operators.getAffectedOperator())) {

        for (AffectedOperatorStructure operator : operators.getAffectedOperator()) {
          OperatorRefStructure operatorRef = operator.getOperatorRef();
          if (operatorRef == null || operatorRef.getValue() == null)
            continue;
          addAffectsOperator(allAffects, StringUtils.trim(operatorRef.getValue()));
        }
      }
    }

    StopPoints stopPoints = affectsStructure.getStopPoints();

    if (stopPoints != null
        && !CollectionsLibrary.isEmpty(stopPoints.getAffectedStopPoint())) {

      for (AffectedStopPointStructure stopPoint : stopPoints.getAffectedStopPoint()) {
        StopPointRefStructure stopRef = stopPoint.getStopPointRef();
        if (stopRef == null || stopRef.getValue() == null)
          continue;
        SituationAffectsBean sab = new SituationAffectsBean();
        sab.setStopId(StringUtils.trim(stopRef.getValue()));
        allAffects.add(sab);
      }
    }

    VehicleJourneys vjs = affectsStructure.getVehicleJourneys();
    if (vjs != null
        && !CollectionsLibrary.isEmpty(vjs.getAffectedVehicleJourney())) {

      for (AffectedVehicleJourneyStructure vj : vjs.getAffectedVehicleJourney()) {

        SituationAffectsBean sab = new SituationAffectsBean();

        if (vj.getLineRef() != null) {
          sab.setRouteId(StringUtils.trim(vj.getLineRef().getValue()));
        }

        if (vj.getDirectionRef() != null)
          sab.setDirectionId(StringUtils.trim(vj.getDirectionRef().getValue()));

        List<VehicleJourneyRefStructure> tripRefs = vj.getVehicleJourneyRef();
        Calls stopRefs = vj.getCalls();

        boolean hasTripRefs = !CollectionsLibrary.isEmpty(tripRefs);
        boolean hasStopRefs = stopRefs != null
            && !CollectionsLibrary.isEmpty(stopRefs.getCall());

        if (!(hasTripRefs || hasStopRefs)) {
          if (sab.getRouteId() != null && !sab.getRouteId().isEmpty()) {
            allAffects.add(sab);
          }
        } else if (hasTripRefs && hasStopRefs) {
          for (VehicleJourneyRefStructure vjRef : vj.getVehicleJourneyRef()) {
            sab.setTripId(StringUtils.trim(vjRef.getValue()));
            for (AffectedCallStructure call : stopRefs.getCall()) {
              sab.setStopId(call.getStopPointRef().getValue());
              allAffects.add(sab);
            }
          }
        } else if (hasTripRefs) {
          for (VehicleJourneyRefStructure vjRef : vj.getVehicleJourneyRef()) {
            sab.setTripId(StringUtils.trim(vjRef.getValue()));
            allAffects.add(sab);
          }
        } else {
          for (AffectedCallStructure call : stopRefs.getCall()) {
            sab.setStopId(StringUtils.trim(call.getStopPointRef().getValue()));
            allAffects.add(sab);
          }
        }
      }
    }

    ExtensionsStructure extension = affectsStructure.getExtensions();
    if (extension != null && extension.getAny() != null) {
      Object ext = extension.getAny();
      if (ext instanceof OneBusAwayAffects) {
        OneBusAwayAffects obaAffects = (OneBusAwayAffects) ext;

        Applications applications = obaAffects.getApplications();
        if (applications != null
            && !CollectionsLibrary.isEmpty(applications.getAffectedApplication())) {

          List<AffectedApplicationStructure> apps = applications.getAffectedApplication();

          for (AffectedApplicationStructure sApp : apps) {
            SituationAffectsBean sab = new SituationAffectsBean();
            sab.setApplicationId(StringUtils.trim(sApp.getApiKey()));
            allAffects.add(sab);
          }
        }
      }
    }

    if (!allAffects.isEmpty())
      serviceAlert.setAllAffects(allAffects);
  }

  private void addAffectsOperator(List<SituationAffectsBean> allAffects,
      String operator) {
    SituationAffectsBean sab = new SituationAffectsBean();
    sab.setAgencyId(operator);
    allAffects.add(sab);
  }

  private void handleConsequences(PtSituationElementStructure ptSituation,
      ServiceAlertBean serviceAlert) {

    List<SituationConsequenceBean> consequencesList = new ArrayList<SituationConsequenceBean>();

    PtConsequencesStructure consequences = ptSituation.getConsequences();

    if (consequences == null || consequences.getConsequence() == null)
      return;

    for (PtConsequenceStructure consequence : consequences.getConsequence()) {
      SituationConsequenceBean situationConsequenceBean = new SituationConsequenceBean();

      if (consequence.getCondition() != null)
        situationConsequenceBean.setEffect(getConditionAsEffect(consequence.getCondition()));
      ExtensionsStructure extensions = consequence.getExtensions();
      if (extensions != null) {
        Object obj = extensions.getAny();
        if (obj instanceof OneBusAwayConsequence) {
          OneBusAwayConsequence obaConsequence = (OneBusAwayConsequence) obj;
          if (obaConsequence.getDiversionPath() != null)
            situationConsequenceBean.setDetourPath(StringUtils.trim(obaConsequence.getDiversionPath()));
        }
      }
      if (situationConsequenceBean.getDetourPath() != null
          || situationConsequenceBean.getEffect() != null)
        consequencesList.add(situationConsequenceBean);
    }

    if (!consequencesList.isEmpty())
      serviceAlert.setConsequences(consequencesList);
  }

  private EEffect getConditionAsEffect(ServiceConditionEnumeration condition) {
    switch (condition) {

      case CANCELLED:
      case NO_SERVICE:
        return EEffect.NO_SERVICE;

      case DELAYED:
        return EEffect.SIGNIFICANT_DELAYS;

      case DIVERTED:
        return EEffect.DETOUR;

      case ADDITIONAL_SERVICE:
      case EXTENDED_SERVICE:
      case SHUTTLE_SERVICE:
      case SPECIAL_SERVICE:
      case REPLACEMENT_SERVICE:
        return EEffect.ADDITIONAL_SERVICE;

      case DISRUPTED:
      case INTERMITTENT_SERVICE:
      case SHORT_FORMED_SERVICE:
        return EEffect.REDUCED_SERVICE;

      case ALTERED:
      case ARRIVES_EARLY:
      case REPLACEMENT_TRANSPORT:
      case SPLITTING_TRAIN:
        return EEffect.MODIFIED_SERVICE;

      case ON_TIME:
      case FULL_LENGTH_SERVICE:
      case NORMAL_SERVICE:
        return EEffect.OTHER_EFFECT;

      case UNDEFINED_SERVICE_INFORMATION:
      case UNKNOWN:
        return EEffect.UNKNOWN_EFFECT;

      default:
        _log.warn("unknown condition: " + condition);
        return EEffect.UNKNOWN_EFFECT;
    }
  }

  private TranslatedString translation(DefaultedTextStructure text) {
    if (text == null)
      return null;
    String value = StringUtils.trim(text.getValue());
    if (value == null || value.isEmpty())
      return null;

    Translation.Builder translation = Translation.newBuilder();
    translation.setText(value);
    if (text.getLang() != null)
      translation.setLanguage(StringUtils.trim(text.getLang()));

    TranslatedString.Builder tsBuilder = TranslatedString.newBuilder();
    tsBuilder.addTranslation(translation);
    return tsBuilder.build();
  }

  protected void sendAndProcessSubscriptionAndServiceRequest() throws Exception {
    String result = sendSubscriptionAndServiceRequest();
    Siri siri = _siriXmlSerializer.fromXml(result);
    SituationExchangeResults handleResult = new SituationExchangeResults();
    handleServiceDeliveries(handleResult, siri.getServiceDelivery());
    _log.info(handleResult.toString());
  }

  String sendSubscriptionAndServiceRequest() throws Exception {
    Siri siri = createSubsAndSxRequest();
    String sendResult = getWebResourceWrapper().post(
        _siriXmlSerializer.getXml(siri), _serviceAlertsUrl);
    return sendResult;
  }

  Siri createSubsAndSxRequest() throws Exception {
    Siri siri = createSubscriptionRequest();
    addSituationExchangeRequest(siri);
    return siri;
  }

  Siri createSubscriptionRequest() throws Exception {
    Siri siri = new Siri();
    SubscriptionRequest subscriptionRequest = new SubscriptionRequest();
    subscriptionRequest.setAddress(makeSubscriptionUrl(getSubscriptionPath()));
    subscriptionRequest.setRequestorRef(_environment.getParticipant() );
    siri.setSubscriptionRequest(subscriptionRequest);
    List<SituationExchangeSubscriptionStructure> exchangeSubscriptionRequests = subscriptionRequest.getSituationExchangeSubscriptionRequest();
    SituationExchangeSubscriptionStructure situationExchangeSubscriptionStructure = new SituationExchangeSubscriptionStructure();;
    exchangeSubscriptionRequests.add(situationExchangeSubscriptionStructure);
    SituationExchangeRequestStructure situationExchangeRequestStructure = new SituationExchangeRequestStructure();
    situationExchangeSubscriptionStructure.setSituationExchangeRequest(situationExchangeRequestStructure);
    SubscriptionQualifierStructure id = new SubscriptionQualifierStructure();
    id.setValue(UUID.randomUUID().toString());
    situationExchangeSubscriptionStructure.setSubscriptionIdentifier(id);
    situationExchangeRequestStructure.setRequestTimestamp(new Date());
    
    return siri;
  }

  private String makeSubscriptionUrl(String subscriptionPath)
      throws UnknownHostException {
    if (_subscriptionUrl != null)
      return _subscriptionUrl;
    String hostName = InetAddress.getLocalHost().getCanonicalHostName();
    _subscriptionUrl = "http://" + hostName + subscriptionPath;
    return _subscriptionUrl;
  }

  private void addSituationExchangeRequest(Siri siri) {
    ServiceRequest serviceRequest = new ServiceRequest();
    siri.setServiceRequest(serviceRequest);
    List<SituationExchangeRequestStructure> situationExchangeRequest = serviceRequest.getSituationExchangeRequest();
    SituationExchangeRequestStructure situationExchangeRequestStructure = new SituationExchangeRequestStructure();
    situationExchangeRequest.add(situationExchangeRequestStructure);
    situationExchangeRequestStructure.setRequestTimestamp(new Date());
    
    serviceRequest.setRequestorRef(_environment.getParticipant());
  }

  public NycTransitDataService getTransitDataService() {
    return _nycTransitDataService;
  }

  public void setTransitDataService(NycTransitDataService nycTransitDataService) {  
    this._nycTransitDataService = nycTransitDataService;
  }

  public String getServiceAlertsUrl() {
    return _serviceAlertsUrl;
  }

  public void setServiceAlertsUrl(String _serviceAlertsUrl) {
    this._serviceAlertsUrl = _serviceAlertsUrl;
  }

  public String getSubscriptionPath() {
    return _subscriptionPath;
  }

  public void setSubscriptionPath(String subscriptionPath) {
    this._subscriptionPath = subscriptionPath;
  }

  public WebResourceWrapper getWebResourceWrapper() {
    if (_webResourceWrapper == null)
      _webResourceWrapper = new WebResourceWrapper();
    return _webResourceWrapper;
  }

  public void setWebResourceWrapper(WebResourceWrapper _webResourceWrapper) {
    this._webResourceWrapper = _webResourceWrapper;
  }
  
}
